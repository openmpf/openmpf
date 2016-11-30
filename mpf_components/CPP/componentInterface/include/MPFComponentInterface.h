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

#ifndef MPF_COMPONENT_INTERFACE_H
#define MPF_COMPONENT_INTERFACE_H

#include <string>

namespace MPF {

   namespace COMPONENT {

   enum MPFComponentType { MPF_DETECTION_COMPONENT };

      class MPFComponent {

         public:
            virtual ~MPFComponent() {}
            virtual bool Init() = 0;
            virtual bool Close() = 0;
            virtual MPFComponentType GetComponentType() = 0;

            void SetRunDirectory(const std::string &run_dir) {run_directory = run_dir;};

            std::string GetRunDirectory() {return run_directory;};

         protected:
            MPFComponent() = default;

         private:
            std::string run_directory;

      };

   }  // namespace COMPONENT

}  // namespace MPF

#define MPF_COMPONENT_CREATOR(name) \
  extern "C" MPFComponent* component_creator() { \
      return new (name);                                  \
  }

#define MPF_COMPONENT_DELETER() \
  extern "C" MPFComponent* component_deleter(MPFComponent *detect_P_) { \
    delete detect_P_; \
  }

#endif  // MPF_COMPONENT_INTERFACE
