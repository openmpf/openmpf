#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2024 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2024 The MITRE Corporation                                      #
#                                                                           #
# Licensed under the Apache License, Version 2.0 (the "License");           #
# you may not use this file except in compliance with the License.          #
# You may obtain a copy of the License at                                   #
#                                                                           #
#    http://www.apache.org/licenses/LICENSE-2.0                             #
#                                                                           #
# Unless required by applicable law or agreed to in writing, software       #
# distributed under the License is distributed on an "AS IS" BASIS,         #
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  #
# See the License for the specific language governing permissions and       #
# limitations under the License.                                            #
#############################################################################

variable "TAG" {
    default = "latest"
}

variable "REGISTRY" {
    default = ""
}

variable "USER_UID" {
    default = 1000
    description = "This should be set to your Linux user ID number. Check with `id -u`."
}

group "default" {
    targets = [
        "openmpf_build",
        "openmpf_cpp_component_build", "openmpf_cpp_executor",
        "openmpf_java_component_build", "openmpf_java_executor",
        "openmpf_python_component_build", "openmpf_python_executor", "openmpf_python_executor_ssb",
        "openmpf_python_subject_build", "openmpf_python_subject_executor"
    ]
}


target "openmpf_build" {
    context = ".."
    dockerfile = "openmpf-docker/openmpf_build/Dockerfile"
    tags = image_name("openmpf_build")
}


target "openmpf_dev" {
    context = ".."
    dockerfile = "openmpf-docker/openmpf_build/Dockerfile"
    tags = image_name("openmpf_dev")
    target = "devcontainer"
    args = {
        USER_UID = USER_UID
    }
}


target "openmpf_cpp_and_java_components" {
    name = "openmpf_${lang}_${type}"
    tags = image_name("openmpf_${lang}_${type}")
    dockerfile = "${lang}_${type}/Dockerfile"
    context = "components"
    contexts = {
        openmpf_build = "target:openmpf_build"
    }
    matrix = {
        type = ["component_build", "executor"]
        lang = ["cpp", "java"]
    }
}


target "openmpf_python_components" {
    name = "openmpf_python_${type.name}"
    tags = image_name("openmpf_python_${type.name}")
    dockerfile = "python/Dockerfile"
    target = type.target
    context = "components"
    contexts = {
        openmpf_build = "target:openmpf_build"
    }
    matrix = {
        type = [
            { name = "component_build", target = "build" },
            { name = "executor" , target = "executor" },
            { name = "executor_ssb", target = "ssb" }
        ]
    }
}

target "openmpf_subject_components" {
    name = "openmpf_python_subject_${type}"
    tags = image_name("openmpf_python_subject_${type}")
    target = type
    context = "subject-components/python"
    contexts = {
        openmpf_build = "target:openmpf_build"
    }
    matrix = {
        type = ["build", "executor"]
    }
}


function "image_name" {
    params = [base_name]
    result = ["${REGISTRY}${base_name}:${TAG}"]
}
