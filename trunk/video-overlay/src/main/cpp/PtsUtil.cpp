/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2026 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2026 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

#include "PtsUtil.h"

#include <iostream>
#include <algorithm>
#include <memory>
#include <utility>
#include <string>
#include <stdexcept>

extern "C" {
    #include <libavcodec/avcodec.h>
    #include <libavformat/avformat.h>
    #include <libavutil/avutil.h>
}

namespace {

    template <typename T, typename TDel>
    auto makeAvObj(T* data, TDel deleter) {
        // unique_ptr expects the deleter function to accept a T*, but the free functions from
        // libAv usually accept T**.
        auto deleterAdapter = [deleter = std::move(deleter)](T* data) {
            deleter(&data);
        };
        return std::unique_ptr<T, decltype(deleterAdapter)>{data, std::move(deleterAdapter)};
    }


    std::string avErrorToString(int av_error_code) {
        char errorMsgBuf[AV_ERROR_MAX_STRING_SIZE];
        av_strerror(av_error_code, errorMsgBuf, sizeof(errorMsgBuf));
        return errorMsgBuf;
    }

    template <typename TFunc, typename... Args>
    auto checkAvCall(TFunc func, Args&&... args) {
        auto result = func(std::forward<Args>(args)...);
        if (result < 0) {
            throw std::runtime_error{avErrorToString(result)};
        }
        return result;
    }

    struct PacketUnrefCtx {
        AVPacket* packet;

        ~PacketUnrefCtx() {
            av_packet_unref(packet);
        }

        PacketUnrefCtx(const PacketUnrefCtx&) = delete;
        PacketUnrefCtx& operator=(const PacketUnrefCtx&) = delete;
    };


    auto makeFormatContext(const char* inputFile) {
        AVFormatContext* formatCtx = nullptr;
        checkAvCall(avformat_open_input, &formatCtx, inputFile, nullptr, nullptr);
        try {
            return makeAvObj(formatCtx, avformat_close_input);
        }
        catch (...) {
            avformat_close_input(&formatCtx);
            throw;
        }
    }


    using num_streams_t = decltype(AVFormatContext::nb_streams);

    num_streams_t getVideoStreamIdx(const AVFormatContext& formatCtx) {
        for (num_streams_t i = 0; i < formatCtx.nb_streams; i++) {
            if (formatCtx.streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
                return i;
            }
        }
        throw std::runtime_error{"Could not find video stream"};
    }

    void discardOtherStreams(const AVFormatContext& formatCtx, num_streams_t streamToKeep) {
        for (num_streams_t i = 0; i < formatCtx.nb_streams; i++) {
            if (i != streamToKeep) {
                formatCtx.streams[i]->discard = AVDISCARD_ALL;
            }
        }
    }


    PtsResult extractInternal(AVFormatContext& formatCtx, unsigned int videoStreamIdx) {
        std::vector<long> ptsValues;
        bool isMissingPts = false;
        auto packet = makeAvObj(av_packet_alloc(), av_packet_free);

        int read_rv = 0;
        while ((read_rv = av_read_frame(&formatCtx, packet.get())) != AVERROR_EOF) {
            if (read_rv < 0) {
                throw std::runtime_error{
                    "av_read_frame failed due to: " + avErrorToString(read_rv)};
            }
            if (packet->size == 0) {
                throw std::runtime_error{"Found empty packet."};
            }

            PacketUnrefCtx ctx{packet.get()};
            if (packet->stream_index != videoStreamIdx) {
                continue;
            }

            if (packet->pts == AV_NOPTS_VALUE) {
                isMissingPts = true;
                if (packet->dts == AV_NOPTS_VALUE) {
                    throw std::runtime_error{"Missing both pts and dts."};
                }
                ptsValues.push_back(packet->dts);
            }
            else {
                ptsValues.push_back(packet->pts);
            }
        }
        std::sort(ptsValues.begin(), ptsValues.end());
        return {std::move(ptsValues), isMissingPts};
    }
} // end anonymous namespace


PtsResult extractPts(const char* videoPath) {
    auto formatCtx = makeFormatContext(videoPath);
    auto videoStreamIdx = getVideoStreamIdx(*formatCtx);
    discardOtherStreams(*formatCtx, videoStreamIdx);

    auto* codec = avcodec_find_decoder(formatCtx->streams[videoStreamIdx]->codecpar->codec_id);
    if (codec == nullptr) {
        throw std::runtime_error{
            "Could not find decoder with id: "
            + std::to_string(formatCtx->streams[videoStreamIdx]->codecpar->codec_id)};
    }

    auto codecCtx = makeAvObj(avcodec_alloc_context3(codec), avcodec_free_context);
    checkAvCall(avcodec_parameters_to_context,
            codecCtx.get(), formatCtx->streams[videoStreamIdx]->codecpar);
    checkAvCall(avcodec_open2, codecCtx.get(), codec, nullptr);
    return extractInternal(*formatCtx, videoStreamIdx);
}
