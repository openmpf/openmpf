/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

#ifndef MPF_AMQ_MESSAGE_H_
#define MPF_AMQ_MESSAGE_H_

#include <memory>

#include <cms/Session.h>
#include <cms/Message.h>
#include <cms/TextMessage.h>
#include <cms/BytesMessage.h>

#include "MPFMessage.h"
#include "detection.pb.h"


namespace MPF {

template <typename MSGTYPE>
class AMQMessageConverter {
  public:
    typedef MSGTYPE msg_type;
    virtual ~AMQMessageConverter() = default;

    virtual MSGTYPE fromCMSMessage(const cms::Message &msg) = 0;
    virtual void toCMSMessage(const MSGTYPE &mpfMsg, cms::Message &msg) = 0;

  protected:
    AMQMessageConverter() = default;

};


class AMQSegmentSummaryConverter : public AMQMessageConverter<MPFSegmentSummaryMessage> {
  private:
    typedef org::mitre::mpf::wfm::buffers::StreamingDetectionResponse ProtobufStreamingResponse;
    typedef org::mitre::mpf::wfm::buffers::StreamingVideoTrack ProtobufVideoTrack;
    typedef org::mitre::mpf::wfm::buffers::PropertyMap ProtobufPropertyMap;
    typedef org::mitre::mpf::wfm::buffers::StreamingVideoDetection ProtobufTrackDetection;
  public:
    MPFSegmentSummaryMessage fromCMSMessage(const cms::Message &msg) override {
        // Unpack the Video Track info from the protobuf in the cms message.
        std::vector<MPF::COMPONENT::MPFVideoTrack> mpfTracks;
        int seg_num = -1;
        int start_frame = -1;
        int stop_frame = -1;
        std::string detection_type;
        std::string err_str = {};
        std::unordered_map<int, long> times;

        const cms::BytesMessage &bytes_msg = dynamic_cast<const cms::BytesMessage&>(msg);
        int length = bytes_msg.getBodyLength();
        std::unique_ptr<unsigned char[]> contents(new unsigned char[length]());
        if (NULL != contents) {
            bytes_msg.readBytes(contents.get(), length);
            ProtobufStreamingResponse response;
            response.ParseFromArray(static_cast<const void *>(contents.get()), length);
            seg_num = response.segment_number();
            start_frame = response.segment_start_frame();
            stop_frame = response.segment_stop_frame();
            detection_type = response.detection_type();
            err_str = response.error();
            for (auto &msg_track : response.video_tracks()) {
                MPF::COMPONENT::MPFVideoTrack mpf_track;
                mpf_track.start_frame = msg_track.start_frame();
                times[start_frame] = msg_track.start_time();
                mpf_track.stop_frame = msg_track.stop_frame();
                times[stop_frame] = msg_track.stop_time();
                mpf_track.confidence = msg_track.confidence();
                // Copy the frame locations
                for (auto &loc : msg_track.detections()) {
                    MPF::COMPONENT::Properties tmp_props;
                    for (auto prop : loc.detection_properties()) {
                        tmp_props[prop.key()] = prop.value();
                    }
                    MPF::COMPONENT::MPFImageLocation tmp_loc(
                        loc.x_left_upper(),
                        loc.y_left_upper(),
                        loc.width(),
                        loc.height(),
                        loc.confidence(),
                        tmp_props);

                    mpf_track.frame_locations[loc.frame_number()] = tmp_loc;
                }
                // Copy the track properties
                for (auto &prop : msg_track.detection_properties()) {
                    mpf_track.detection_properties[prop.key()] = prop.value();
                }
                // Add the track to the track vector
                mpfTracks.push_back(mpf_track);
            }
        }

        return MPFSegmentSummaryMessage(msg.getStringProperty("JOB_NAME"),
                                        msg.getIntProperty("JOB_ID"),
                                        seg_num, start_frame, stop_frame,
                                        detection_type, err_str,
                                        mpfTracks, times);
    }

    virtual void toCMSMessage(const MPFSegmentSummaryMessage &mpfMsg, cms::Message &msg) override {

        msg.setStringProperty("JOB_NAME", mpfMsg.job_name);
        msg.setIntProperty("JOB_ID", mpfMsg.job_number);
        // Serialize the vector of tracks in the mpfMsg into a
        // protobuf and add it to the cms message.
        cms::BytesMessage &bytes_msg = dynamic_cast<cms::BytesMessage&>(msg);
        ProtobufStreamingResponse response;
        response.set_segment_number(mpfMsg.segment_number);
        response.set_segment_start_frame(mpfMsg.segment_start_frame);
        response.set_segment_stop_frame(mpfMsg.segment_stop_frame);
        response.set_detection_type(mpfMsg.detection_type);
        response.set_error(mpfMsg.segment_error);

        for (auto &track : mpfMsg.tracks) {
            ProtobufVideoTrack* msg_track = response.add_video_tracks();
            msg_track->set_start_frame(track.start_frame);
            msg_track->set_start_time(mpfMsg.timestamps.at(track.start_frame));
            msg_track->set_stop_frame(track.stop_frame);
            msg_track->set_stop_time(mpfMsg.timestamps.at(track.stop_frame));
            msg_track->set_confidence(track.confidence);
            for (auto &prop : track.detection_properties) {
                ProtobufPropertyMap* detection_props = msg_track->add_detection_properties();
                detection_props->set_key(prop.first);
                detection_props->set_value(prop.second);
            }
            for (auto &loc : track.frame_locations) {
                ProtobufTrackDetection* det = msg_track->add_detections();
                det->set_frame_number(loc.first);
                det->set_time(mpfMsg.timestamps.at(loc.first));
                MPF::COMPONENT::MPFImageLocation detection = loc.second;
                det->set_x_left_upper(detection.x_left_upper);
                det->set_y_left_upper(detection.y_left_upper);
                det->set_width(detection.width);
                det->set_height(detection.height);
                det->set_confidence(detection.confidence);
                for (auto &prop : detection.detection_properties) {
                    ProtobufPropertyMap* det_prop = det->add_detection_properties();
                    det_prop->set_key(prop.first);
                    det_prop->set_value(prop.second);
                }
            }
        }
        // Now serialize and set the message body
        std::unique_ptr<unsigned char[]> response_contents(new unsigned char[response.ByteSize()]);
        response.SerializeToArray(static_cast<void *>(response_contents.get()), response.ByteSize());
        bytes_msg.setBodyBytes(response_contents.get(), response.ByteSize());
    }
    
};


class AMQActivityAlertConverter : public AMQMessageConverter<MPFActivityAlertMessage> {
  public:
    MPFActivityAlertMessage fromCMSMessage(const cms::Message &msg) override {
        return MPFActivityAlertMessage(
                msg.getStringProperty("JOB_NAME"),
                msg.getIntProperty("JOB_ID"),
                msg.getIntProperty("SEGMENT_NUMBER"),
                msg.getIntProperty("FRAME_INDEX"),
                msg.getLongProperty("ACTIVITY_DETECTION_TIMESTAMP")
        );
    }

    void toCMSMessage(const MPFActivityAlertMessage &activityAlert, cms::Message &msg) override {
        msg.setStringProperty("JOB_NAME", activityAlert.job_name);
        msg.setIntProperty("JOB_ID", activityAlert.job_number);
        msg.setIntProperty("SEGMENT_NUMBER", activityAlert.segment_number);
        msg.setIntProperty("FRAME_INDEX", activityAlert.frame_index);
        msg.setLongProperty("ACTIVITY_DETECTION_TIMESTAMP", activityAlert.activity_time);
    }
};


class AMQJobStatusConverter : public AMQMessageConverter<MPFJobStatusMessage> {
  public:
    MPFJobStatusMessage fromCMSMessage(const cms::Message &msg) override {
        return MPFJobStatusMessage(
                msg.getStringProperty("JOB_NAME"),
                msg.getIntProperty("JOB_ID"),
                msg.getStringProperty("JOB_STATUS"),
                msg.getLongProperty("STATUS_CHANGE_TIMESTAMP"));
    }

    void toCMSMessage(const MPFJobStatusMessage &jobStatus, cms::Message &msg) override {
        msg.setStringProperty("JOB_NAME", jobStatus.job_name);
        msg.setIntProperty("JOB_ID", jobStatus.job_number);
        msg.setStringProperty("JOB_STATUS", jobStatus.status_message);
        msg.setLongProperty("STATUS_CHANGE_TIMESTAMP", jobStatus.status_change_time);
    }
};


class AMQSegmentReadyConverter : public AMQMessageConverter<MPFSegmentReadyMessage> {
  public:
    MPFSegmentReadyMessage fromCMSMessage(const cms::Message &msg) override {
        return MPFSegmentReadyMessage(
                msg.getStringProperty("JOB_NAME"),
                msg.getIntProperty("JOB_ID"),
                msg.getIntProperty("SEGMENT_NUMBER"));
    }

    void toCMSMessage(const MPFSegmentReadyMessage &segment_msg, cms::Message &msg) override {
        msg.setStringProperty("JOB_NAME", segment_msg.job_name);
        msg.setIntProperty("JOB_ID", segment_msg.job_number);
        msg.setIntProperty("SEGMENT_NUMBER", segment_msg.segment_number);
    }

};


class AMQFrameReadyConverter : public AMQMessageConverter<MPFFrameReadyMessage> {
  public:
    MPFFrameReadyMessage fromCMSMessage(const cms::Message &msg) override {
        return MPFFrameReadyMessage(
                msg.getStringProperty("JOB_NAME"),
                msg.getIntProperty("JOB_ID"),
                msg.getIntProperty("SEGMENT_NUMBER"),
                msg.getIntProperty("FRAME_INDEX"),
                msg.getLongProperty("FRAME_OFFSET"),
                msg.getLongProperty("FRAME_TIMESTAMP"));
    }

    void toCMSMessage(const MPFFrameReadyMessage &frame_msg, cms::Message &msg) override {
        msg.setStringProperty("JOB_NAME", frame_msg.job_name);
        msg.setIntProperty("JOB_ID", frame_msg.job_number);
        msg.setIntProperty("SEGMENT_NUMBER", frame_msg.segment_number);
        msg.setIntProperty("FRAME_INDEX", frame_msg.frame_index);
        msg.setIntProperty("FRAME_OFFSET", frame_msg.frame_offset);
    }

};  


class AMQReleaseFrameConverter : public AMQMessageConverter<MPFReleaseFrameMessage> {
  public:
    MPFReleaseFrameMessage fromCMSMessage(const cms::Message &msg) override {
        return MPFReleaseFrameMessage(
                msg.getStringProperty("JOB_NAME"),
                msg.getIntProperty("JOB_ID"),
                msg.getIntProperty("FRAME_INDEX"),
                msg.getIntProperty("FRAME_OFFSET"));
    }

    void toCMSMessage(const MPFReleaseFrameMessage &frame_msg, cms::Message &msg) override {
        msg.setStringProperty("JOB_NAME", frame_msg.job_name);
        msg.setIntProperty("JOB_ID", frame_msg.job_number);
        msg.setIntProperty("FRAME_INDEX", frame_msg.frame_index);
        msg.setIntProperty("FRAME_OFFSET", frame_msg.frame_offset);
    }

};


#if 0
//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
class AMQVideoWrittenConverter : public AMQMessageConverter<MPFVideoWrittenMessage> {
  public:
    MPFVideoWrittenMessage fromCMSMessage(const cms::Message &msg) override {
        return MPFVideoWrittenMessage(
                msg.getStringProperty("JOB_NAME"),
                msg.getIntProperty("JOB_ID"),
                msg.getIntProperty("SEGMENT_NUMBER"),
                msg.getStringProperty("VIDEO_OUTPUT_PATHNAME"));
    }

    void toCMSMessage(const MPFVideoWrittenMessage &frame_msg, cms::Message &msg) override {
        msg.setStringProperty("JOB_NAME", frame_msg.job_name);
        msg.setIntProperty("JOB_ID", frame_msg.job_number);
        msg.setIntProperty("SEMGMENT_NUMBER", frame_msg.segment_number);
        msg.setStringProperty("VIDEO_OUTPUT_PATHNAME", frame_msg.video_output_pathname);
    }



};
#endif // TODO: For future use.

} // namespace MPF

#endif // MPF_AMQ_MESSAGE_H_
