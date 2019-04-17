/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

#include "detection.pb.h"
#include "MPFMessage.h"

//TODO: For future use.
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
    typedef org::mitre::mpf::wfm::buffers::VideoTrack ProtobufVideoTrack;
    typedef org::mitre::mpf::wfm::buffers::PropertyMap ProtobufPropertyMap;
    typedef org::mitre::mpf::wfm::buffers::VideoTrack_FrameLocationMap ProtobufFrameLocationMap;
    typedef org::mitre::mpf::wfm::buffers::ImageLocation ProtobufImageLocation;
  public:
    MPFSegmentSummaryMessage fromCMSMessage(const cms::Message &msg) override {
        // Unpack the Video Track info from the protobuf in the cms message.
        std::vector<MPF::COMPONENT::MPFVideoTrack> mpfTracks;
        int seg_num = -1;
        int start_frame = -1;
        int stop_frame = -1;
        std::string detection_type;
        MPF::COMPONENT::MPFDetectionError err = MPF::COMPONENT::MPFDetectionError::MPF_DETECTION_SUCCESS;

        const cms::BytesMessage &bytes_msg = dynamic_cast<const cms::BytesMessage&>(msg);
        int length = bytes_msg.getBodyLength();
        std::unique_ptr<unsigned char[]> contents(new unsigned char[length]());
        if (NULL != contents) {
            bytes_msg.readBytes(contents.get(), length);
            ProtobufStreamingResponse response;
            response.ParseFromArray(static_cast<const void *>(contents.get()), length);
            seg_num = response.segment_number();
            start_frame = response.start_frame();
            stop_frame = response.stop_frame();
            detection_type = response.detection_type();
            err = translateProtobufError(response.error());
            for (auto &msg_track : response.video_tracks()) {
                MPF::COMPONENT::MPFVideoTrack mpf_track;
                mpf_track.start_frame = msg_track.start_frame();
                mpf_track.stop_frame = msg_track.stop_frame();
                mpf_track.confidence = msg_track.confidence();
                // Copy the frame locations
                for (auto &loc : msg_track.frame_locations()) {
                    MPF::COMPONENT::Properties tmp_props;
                    for (auto prop : loc.image_location().detection_properties()) {
                        tmp_props[prop.key()] = prop.value();
                    }
                    MPF::COMPONENT::MPFImageLocation tmp_loc(
                        loc.image_location().x_left_upper(),
                        loc.image_location().y_left_upper(),
                        loc.image_location().width(),
                        loc.image_location().height(),
                        loc.image_location().confidence(),
                        tmp_props);

                    mpf_track.frame_locations[loc.frame()] = tmp_loc;
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
                                        detection_type, err,
                                        mpfTracks);
    }

    virtual void toCMSMessage(const MPFSegmentSummaryMessage &mpfMsg, cms::Message &msg) override {

        msg.setStringProperty("JOB_NAME", mpfMsg.job_name_);
        msg.setIntProperty("JOB_ID", mpfMsg.job_number_);
        // Serialize the vector of tracks in the mpfMsg into a
        // protobuf and add it to the cms message.
        cms::BytesMessage &bytes_msg = dynamic_cast<cms::BytesMessage&>(msg);
        ProtobufStreamingResponse response;
        response.set_segment_number(mpfMsg.segment_number_);
        response.set_start_frame(mpfMsg.segment_start_frame_);
        response.set_stop_frame(mpfMsg.segment_stop_frame_);
        response.set_detection_type(mpfMsg.detection_type_);
        response.set_error(translateMPFDetectionError(mpfMsg.segment_error_));

        for (auto &track : mpfMsg.tracks_) {
            ProtobufVideoTrack* msg_track = response.add_video_tracks();
            msg_track->set_start_frame(track.start_frame);
            msg_track->set_stop_frame(track.stop_frame);
            msg_track->set_confidence(track.confidence);
            for (auto &prop : track.detection_properties) {
                ProtobufPropertyMap* detection_props = msg_track->add_detection_properties();
                detection_props->set_key(prop.first);
                detection_props->set_value(prop.second);
            }
            for (auto &loc : track.frame_locations) {
                MPF::COMPONENT::MPFImageLocation detection = loc.second;
                ProtobufFrameLocationMap* msg_loc_map = msg_track->add_frame_locations();
                msg_loc_map->set_frame(loc.first);
                ProtobufImageLocation* msg_loc = msg_loc_map->mutable_image_location();
                msg_loc->set_x_left_upper(detection.x_left_upper);
                msg_loc->set_y_left_upper(detection.y_left_upper);
                msg_loc->set_width(detection.width);
                msg_loc->set_height(detection.height);
                msg_loc->set_confidence(detection.confidence);
                for (auto &prop : detection.detection_properties) {
                    ProtobufPropertyMap* det_prop = msg_loc->add_detection_properties();
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
        msg.setStringProperty("JOB_NAME", activityAlert.job_name_);
        msg.setIntProperty("JOB_ID", activityAlert.job_number_);
        msg.setIntProperty("SEGMENT_NUMBER", activityAlert.segment_number_);
        msg.setIntProperty("FRAME_INDEX", activityAlert.frame_index_);
        msg.setLongProperty("ACTIVITY_DETECTION_TIMESTAMP", activityAlert.activity_time_);
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
        msg.setStringProperty("JOB_NAME", jobStatus.job_name_);
        msg.setIntProperty("JOB_ID", jobStatus.job_number_);
        msg.setStringProperty("JOB_STATUS", jobStatus.status_message_);
        msg.setLongProperty("STATUS_CHANGE_TIMESTAMP", jobStatus.status_change_time_);
    }
};

#if 0 //TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
class AMQSegmentReadyMessage : AMQMessage, MPFSegmentReadyMessage {

    AMQSegmentReadyMessage() = default;
    AMQSegmentReadyMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint32_t seg_num)
            : MPFSegmentReadyMessage(job_name, job_number, seg_num) {}

    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setIntProperty("SEGMENT_NUMBER", segment_number_);
        }
    }

    virtual void ReceiveMessageContent() {
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        segment_number_ = msg_->getIntProperty("SEGMENT_NUMBER");
    }
};

//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
class AMQFrameReadyMessage : AMQMessage, MPFFrameReadyMessage {

    AMQFrameReadyMessage() {};
    AMQFrameReadyMessage(const std::string &job_name,
                         const uint32_t job_number,
                         const uint32_t seg_num,
                         const uint32_t index,
                         const uint64_t offset)
            : MPFFrameReadyMessage(job_name, job_number, seg_num, index, offset) {}

    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setIntProperty("SEGMENT_NUMBER", segment_number_);
            msg_->setIntProperty("FRAME_INDEX", frame_index_);
            msg_->setLongProperty("FRAME_STORE_OFFSET", frame_offset_bytes_);
        }
    }

    virtual void ReceiveMessageContent() {
        std::vector<std::string> prop_names = msg_->getPropertyNames();
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        segment_number_ = msg_->getIntProperty("SEGMENT_NUMBER");
        frame_index_ = msg_->getIntProperty("FRAME_INDEX");
        frame_offset_bytes_ = msg_->getLongProperty("FRAME_STORE_OFFSET");
    }

};  

//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
class AMQReleaseFrameMessage : AMQMessage, MPFReleaseFrameMessage {

    AMQReleaseFrameMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint64_t offset)
            : MPFReleaseFrameMessage(job_name, job_number, offset) {}


    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setLongProperty("FRAME_STORE_OFFSET", frame_offset_bytes_);
        }
    }

    virtual void ReceiveMessageContent() {
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        frame_offset_bytes_ = msg_->getLongProperty("FRAME_STORE_OFFSET");
    }

};


//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
class AMQVideoWrittenMessage : AMQMessage, MPFVideoWrittenMessage {

    AMQVideoWrittenMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint32_t seg_num,
                           const std::string &path)
            : MPFVideoWrittenMessage(job_name, job_number, seg_num, path) {}

    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setIntProperty("SEGMENT_NUMBER", segment_number_);
            msg_->setStringProperty("VIDEO_OUTPUT_PATHNAME", video_output_pathname_);
        }
    }

    virtual void ReceiveMessageContent() {
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        segment_number_ = msg_->getIntProperty("SEGMENT_NUMBER");
        video_output_pathname_ = msg_->getStringProperty("VIDEO_OUTPUT_PATHNAME");
    }

};
#endif // TODO: For future use.

} // namespace MPF

#endif // MPF_AMQ_MESSAGE_H_
