/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

#ifndef MPF_DETECTION_COMPONENT_H_
#define MPF_DETECTION_COMPONENT_H_

#include <vector>
#include <string>
#include <map>

#include "MPFComponentInterface.h"


namespace MPF { namespace COMPONENT {

    typedef std::map<std::string, std::string> Properties;

    enum MPFDetectionDataType {
        UNKNOWN, VIDEO, IMAGE, AUDIO, INVALID_TYPE
    };

    enum MPFDetectionError {
        MPF_DETECTION_SUCCESS = 0,
        MPF_OTHER_DETECTION_ERROR_TYPE,
        MPF_DETECTION_NOT_INITIALIZED,
        MPF_UNRECOGNIZED_DATA_TYPE,
        MPF_UNSUPPORTED_DATA_TYPE,
        MPF_INVALID_DATAFILE_URI,
        MPF_COULD_NOT_OPEN_DATAFILE,
        MPF_COULD_NOT_READ_DATAFILE,
        MPF_FILE_WRITE_ERROR,
        MPF_IMAGE_READ_ERROR,
        MPF_BAD_FRAME_SIZE,
        MPF_BOUNDING_BOX_SIZE_ERROR,
        MPF_INVALID_FRAME_INTERVAL,
        MPF_INVALID_START_FRAME,
        MPF_INVALID_STOP_FRAME,
        MPF_DETECTION_FAILED,
        MPF_DETECTION_TRACKING_FAILED,
        MPF_INVALID_PROPERTY,
        MPF_MISSING_PROPERTY,
        MPF_PROPERTY_IS_NOT_INT,
        MPF_PROPERTY_IS_NOT_FLOAT,
        MPF_INVALID_ROTATION,
        MPF_MEMORY_ALLOCATION_FAILED
    };

    struct MPFImageLocation {
        int x_left_upper;
        int y_left_upper;
        int width;
        int height;
        float confidence;  // optional
        Properties detection_properties;


        MPFImageLocation()
                : x_left_upper(-1)
                , y_left_upper(-1)
                , width(-1)
                , height(-1)
                , confidence(-1) {
        }

        MPFImageLocation(int x_left_upper,
                         int y_left_upper,
                         int width,
                         int height,
                         float confidence = -1,
                         const Properties &detection_properties = {})
                : x_left_upper(x_left_upper)
                , y_left_upper(y_left_upper)
                , width(width)
                , height(height)
                , confidence(confidence)
                , detection_properties(detection_properties) {
        }
    };


    struct MPFVideoTrack {
        int start_frame;
        int stop_frame;
        float confidence;  // optional
        std::map<int, MPFImageLocation> frame_locations;
        Properties detection_properties;

        MPFVideoTrack()
                : start_frame(-1)
                , stop_frame(-1)
                , confidence(-1) {
        }


        MPFVideoTrack(int start, int stop, float confidence = -1, const Properties &detection_properties = {})
                : start_frame(start)
                , stop_frame(stop)
                , confidence(confidence)
                , detection_properties(detection_properties) {
        }
    };


    struct MPFAudioTrack {
        int start_time;
        int stop_time;
        float confidence;  // optional
        Properties detection_properties;

        MPFAudioTrack()
                : start_time(-1)
                , stop_time(-1)
                , confidence(-1) {
        }

        MPFAudioTrack(int start, int stop, float confidence = -1, const Properties &detection_properties = {})
                : start_time(start)
                , stop_time(stop)
                , confidence(confidence)
                , detection_properties(detection_properties) {}
    };


    struct MPFJob {
        const std::string job_name;
        const std::string data_uri;
        const Properties job_properties;
        const Properties media_properties;

    protected:
        MPFJob(const std::string &job_name,
               const std::string &data_uri,
               const Properties &job_properties,
               const Properties &media_properties)
                : job_name(job_name)
                , data_uri(data_uri)
                , job_properties(job_properties)
                , media_properties(media_properties) {
        }
    };


    struct MPFVideoJob : MPFJob {
        const int start_frame;
        const int stop_frame;

        MPFVideoJob(const std::string &job_name,
                    const std::string &data_uri,
                    int start_frame,
                    int stop_frame,
                    const Properties &job_properties,
                    const Properties &media_properties)
                : MPFJob(job_name, data_uri, job_properties, media_properties)
                , start_frame(start_frame)
                , stop_frame(stop_frame) {
        }
    };


    struct MPFImageJob : MPFJob {
        MPFImageJob(const std::string &job_name,
                    const std::string &data_uri,
                    const Properties &job_properties,
                    const Properties &media_properties)
                : MPFJob(job_name, data_uri, job_properties, media_properties) {
        }
    };


    struct MPFAudioJob : MPFJob {
        const int start_time;
        const int stop_time;

        MPFAudioJob(const std::string &job_name,
                    const std::string &data_uri,
                    int start_time,
                    int stop_time,
                    const Properties &job_properties,
                    const Properties &media_properties)
                : MPFJob(job_name, data_uri, job_properties, media_properties)
                , start_time(start_time)
                , stop_time(stop_time) {
        }
    };



    class MPFDetectionComponent : public MPFComponent {

    public:

        virtual ~MPFDetectionComponent() {}


        virtual MPFDetectionError GetDetections(const MPFVideoJob &job, std::vector<MPFVideoTrack> &tracks)  = 0;

        virtual MPFDetectionError GetDetections(const MPFImageJob &job, std::vector<MPFImageLocation> &locations) = 0;

        virtual MPFDetectionError GetDetections(const MPFAudioJob &job, std::vector<MPFAudioTrack> &tracks) = 0;


        virtual bool Supports(MPFDetectionDataType data_type) = 0;

        virtual std::string GetDetectionType() = 0;

        virtual MPFComponentType GetComponentType() { return MPF_DETECTION_COMPONENT; };

    protected:

        MPFDetectionComponent() = default;
    };

}}

#endif // MPF_DETECTION_COMPONENT_H_
