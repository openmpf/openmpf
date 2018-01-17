/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

#include <map>
#include <iostream>
#include <detectionComponentUtils.h>
#include "HelloWorld.h"

using namespace MPF::COMPONENT;

//-----------------------------------------------------------------------------
/* virtual */ bool HelloWorld::Init() {

    // Determine where the executable is running
    std::string run_dir = GetRunDirectory();
    if (run_dir == "") {
        run_dir = ".";
    }

    std::cout << "Running in directory " << run_dir << std::endl;

    return true;
}

//-----------------------------------------------------------------------------
/* virtual */ bool HelloWorld::Close() {

    return true;
}

//-----------------------------------------------------------------------------
// Video case
MPFDetectionError HelloWorld::GetDetections(const MPFVideoJob &job,
                                            std::vector <MPFVideoTrack> &tracks) {

    // The MPFVideoJob structure contains all of the details needed to
    // process a video.
    std::cout << "[" << job.job_name << "] Processing \"" << job.data_uri
              << "\" from frame " << job.start_frame
              << " to frame " << job.stop_frame << "." << std::endl;

    // The MPFVideoJob structure contains two Properties entries, one
    // that contains job-specific properties, and one that contains
    // media-specific properties.  The frame processing interval is one
    // example of a job-specific property.
    std::cout << "[" << job.job_name << "] Job properties contains FRAME_INTERVAL with a value of " << job.job_properties.at("FRAME_INTERVAL") << "." << std::endl;

    // Detection logic goes here

    MPFVideoTrack video_track(job.start_frame, job.stop_frame);
    video_track.confidence = 0.80f;
    // The MPFVideoTrack structure contains a Properties member that
    // can be used to return component-specific information about the
    // track. Here we add "METADATA", which might be used, for
    // example, to return the type of the object that is tracked.
    video_track.detection_properties["METADATA"] = "extra video track info";

    // Do something with the feed forward track if it exists
    if (job.has_feed_forward_track) {
        int feed_forward_count = DetectionComponentUtils::GetProperty(job.feed_forward_track.detection_properties, "FEED_FORWARD_COUNT", 0);
        video_track.detection_properties["FEED_FORWARD_COUNT"] = std::to_string(feed_forward_count+1);
    }

    MPFImageLocation image_location(0, 0, 100, 100);
    image_location.confidence = 0.80f;
    // The MPFImageLocation structure also contains a Properties
    // member that can be used to return component-specific
    // information about the image in a particular frame. Here we add
    // "METADATA", which might be used, for example, to return the
    // pose of the object detected in the frame.
    image_location.detection_properties["METADATA"] = "extra image location info";

    // Do something with the feed forward track if it exists
    if (job.has_feed_forward_track) {
        std::map<int, MPFImageLocation>::const_iterator iter =  job.feed_forward_track.frame_locations.begin();
        if (iter != job.feed_forward_track.frame_locations.end()) {
            int feed_forward_count = DetectionComponentUtils::GetProperty((iter->second).detection_properties, "FEED_FORWARD_COUNT", 0);
            image_location.detection_properties["FEED_FORWARD_COUNT"] = std::to_string(feed_forward_count+1);
        }
    }

    video_track.frame_locations.insert(std::pair<int, MPFImageLocation>(job.start_frame, image_location));

    tracks.push_back(video_track);

    std::cout << "[" << job.job_name << "] Processing complete. Generated " << tracks.size() << " dummy video tracks." << std::endl;

    return MPF_DETECTION_SUCCESS;
}

//-----------------------------------------------------------------------------
// Audio case
MPFDetectionError HelloWorld::GetDetections(const MPFAudioJob &job,
                                            std::vector <MPFAudioTrack> &tracks) {

    // The MPFAudioJob structure contains all of the details needed to
    // process an audio file.
    std::cout << "[" << job.job_name << "] Processing \"" << job.data_uri << "\" from start time " << job.start_time << " msec to stop time " << job.stop_time << " msec." << std::endl;

    // NOTE: A stop_time parameter of -1 means process the whole file.

    // Detection logic goes here

    MPFAudioTrack audio_track(job.start_time, job.start_time+1);
    audio_track.confidence = 0.80f;
    // The MPFAudioTrack structure contains a Properties member that
    // can be used to return component-specific information about the
    // track. Here we add "METADATA", which might be used, for
    // example, to return the phrase recognized in the audio track.
    audio_track.detection_properties["METADATA"] = "extra audio track info";

    // Do something with the feed forward track if it exists
    if (job.has_feed_forward_track) {
        int feed_forward_count = DetectionComponentUtils::GetProperty(job.feed_forward_track.detection_properties, "FEED_FORWARD_COUNT", 0);
        audio_track.detection_properties["FEED_FORWARD_COUNT"] = std::to_string(feed_forward_count+1);
    }

    tracks.push_back(audio_track);

    std::cout << "[" << job.job_name << "] Processing complete. Generated " << tracks.size() << " dummy audio tracks." << std::endl;

    return MPF_DETECTION_SUCCESS;
}

//-----------------------------------------------------------------------------
// Image case
MPFDetectionError HelloWorld::GetDetections(const MPFImageJob &job,
                                            std::vector <MPFImageLocation> &locations)
{

    // The MPFImageJob structure contains all of the details needed to
    // process an image file.
    std::cout << "[" << job.job_name << "] Processing \"" << job.data_uri << "\"." << std::endl;

    // Detection logic goes here

    MPFImageLocation image_location(0, 0, 100, 100);
    image_location.confidence = 0.80f;
    // The MPFImageLocation structure contains a Properties member that
    // can be used to return component-specific information about the
    // image. Here we add "METADATA", which might be used, for
    // example, to return the type of object detected in the image.
    image_location.detection_properties["METADATA"] = "extra image location info";

    // Do something with the feed forward location if it exists
    if (job.has_feed_forward_location) {
        int feed_forward_count = DetectionComponentUtils::GetProperty(job.feed_forward_location.detection_properties, "FEED_FORWARD_COUNT", 0);
        image_location.detection_properties["FEED_FORWARD_COUNT"] = std::to_string(feed_forward_count+1);
    }

    locations.push_back(image_location);

    std::cout << "[" << job.job_name << "] Processing complete. Generated " << locations.size() << " dummy image locations." << std::endl;

    return MPF_DETECTION_SUCCESS;
}

//-----------------------------------------------------------------------------
// Generic case
MPFDetectionError HelloWorld::GetDetections(const MPFGenericJob &job,
                                            std::vector <MPFGenericTrack> &tracks)
{

    // The MPFGenericJob structure contains all of the details needed to
    // process a generic file.
    std::cout << "[" << job.job_name << "] Processing \"" << job.data_uri << "\"." << std::endl;

    // Detection logic goes here

    MPFGenericTrack generic_track(0.80f);

    // The MPFGenericTrack structure contains a Properties member that
    // can be used to return component-specific information about the
    // file. Here we add "METADATA", which might be used, for
    // example, to return the type of object detected in the image.
    generic_track.detection_properties["METADATA"] = "extra generic track info";

    // Do something with the feed forward track if it exists
    if (job.has_feed_forward_track) {
        int feed_forward_count = DetectionComponentUtils::GetProperty(job.feed_forward_track.detection_properties, "FEED_FORWARD_COUNT", 0);
        generic_track.detection_properties["FEED_FORWARD_COUNT"] = std::to_string(feed_forward_count+1);
    }

    tracks.push_back(generic_track);

    std::cout << "[" << job.job_name << "] Processing complete. Generated " << tracks.size() << " dummy tracks." << std::endl;

    return MPF_DETECTION_SUCCESS;
}

//-----------------------------------------------------------------------------
bool HelloWorld::Supports(MPFDetectionDataType data_type) {

    return data_type == MPFDetectionDataType::IMAGE
           || data_type == MPFDetectionDataType::VIDEO
           || data_type == MPFDetectionDataType::AUDIO
           || data_type == MPFDetectionDataType::UNKNOWN;
}

//-----------------------------------------------------------------------------
std::string HelloWorld::GetDetectionType() {
    return "HELLO";
}

MPF_COMPONENT_CREATOR(HelloWorld);
MPF_COMPONENT_DELETER();

