/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

#ifndef MPF_DETECTION_BUFFER_H_
#define MPF_DETECTION_BUFFER_H_

#include <string>
#include <vector>
#include <map>
#include <log4cxx/logger.h>

#include <MPFDetectionComponent.h>
#include "detection.pb.h"
#include "MPFMessageUtils.h"
#include "MPFMessenger.h"

using org::mitre::mpf::wfm::buffers::DetectionError;
using org::mitre::mpf::wfm::buffers::DetectionRequest;
using org::mitre::mpf::wfm::buffers::DetectionRequest_DataType;
using org::mitre::mpf::wfm::buffers::DetectionRequest_VideoRequest;
using org::mitre::mpf::wfm::buffers::DetectionRequest_AudioRequest;
using org::mitre::mpf::wfm::buffers::DetectionRequest_ImageRequest;
using org::mitre::mpf::wfm::buffers::DetectionRequest_GenericRequest;
using org::mitre::mpf::wfm::buffers::DetectionResponse;
using org::mitre::mpf::wfm::buffers::DetectionResponse_DataType;
using org::mitre::mpf::wfm::buffers::DetectionResponse_VideoResponse;
using org::mitre::mpf::wfm::buffers::DetectionResponse_AudioResponse;;
using org::mitre::mpf::wfm::buffers::DetectionResponse_ImageResponse;
using org::mitre::mpf::wfm::buffers::DetectionResponse_GenericResponse;
using org::mitre::mpf::wfm::buffers::VideoTrack;
using org::mitre::mpf::wfm::buffers::VideoTrack_FrameLocationMap;
using org::mitre::mpf::wfm::buffers::AudioTrack;
using org::mitre::mpf::wfm::buffers::ImageLocation;
using org::mitre::mpf::wfm::buffers::GenericTrack;
using org::mitre::mpf::wfm::buffers::PropertyMap;

using std::map;
using std::string;
using std::vector;

using namespace MPF;
using namespace COMPONENT;

struct MPFDetectionVideoRequest {
    int start_frame;
    int stop_frame;
    bool has_feed_forward_track = false;
    MPFVideoTrack feed_forward_track;
};

struct MPFDetectionAudioRequest {
    int start_time;
    int stop_time;
    bool has_feed_forward_track = false;
    MPFAudioTrack feed_forward_track;
};

struct MPFDetectionImageRequest {
    bool has_feed_forward_location = false;
    MPFImageLocation feed_forward_location;
};

struct MPFDetectionGenericRequest {
    bool has_feed_forward_track = false;
    MPFGenericTrack feed_forward_track;
};

class MPFDetectionBuffer {
private:
    DetectionRequest detection_request_;

    void PackCommonFields(
            const MPFMessageMetadata &msg_metadata,
            const MPFDetectionDataType data_type,
            const MPFDetectionError error,
            const std::string &error_message,
            DetectionResponse &detection_response) const;

    std::vector<unsigned char> FinalizeDetectionResponse(
            const DetectionResponse &detection_response) const;

public:
    explicit MPFDetectionBuffer(const std::vector<unsigned char> &request_contents);

    void GetMessageMetadata(MPFMessageMetadata* msg_metadata);

    MPFDetectionDataType GetDataType();

    string GetDataUri();

    void GetAlgorithmProperties(map<string, string> &algorithm_properties);

    void GetMediaProperties(map<string, string> &media_properties);

    void GetVideoRequest(MPFDetectionVideoRequest &video_request);

    void GetAudioRequest(MPFDetectionAudioRequest &audio_request);

    void GetImageRequest(MPFDetectionImageRequest &image_request);

    void GetGenericRequest(MPFDetectionGenericRequest &generic_request);

    std::vector<unsigned char> PackErrorResponse(
            const MPFMessageMetadata &msg_metadata,
            const MPFDetectionDataType data_type,
            const MPFDetectionError error,
            const std::string &error_message) const;

    std::vector<unsigned char> PackVideoResponse(
            const vector<MPFVideoTrack> &tracks,
            const MPFMessageMetadata &msg_metadata,
            const MPFDetectionDataType data_type,
            const int start_frame,
            const int stop_frame,
            const string &detection_type,
            const MPFDetectionError error,
            const std::string &error_message) const;

    std::vector<unsigned char> PackAudioResponse(
            const vector<MPFAudioTrack> &tracks,
            const MPFMessageMetadata &msg_metadata,
            const MPFDetectionDataType data_type,
            const int start_time,
            const int stop_time,
            const string &detection_type,
            const MPFDetectionError error,
            const std::string &error_message) const;

    std::vector<unsigned char> PackImageResponse(
            const vector<MPFImageLocation> &locations,
            const MPFMessageMetadata &msg_metadata,
            const MPFDetectionDataType data_type,
            const string &detection_type,
            const MPFDetectionError error,
            const std::string &error_message) const;

    std::vector<unsigned char> PackGenericResponse(
            const vector<MPFGenericTrack> &tracks,
            const MPFMessageMetadata &msg_metadata,
            const MPFDetectionDataType data_type,
            const string &detection_type,
            const MPFDetectionError error,
            const std::string &error_message) const;

};

#endif /* MPF_DETECTION_BUFFER_H_ */
