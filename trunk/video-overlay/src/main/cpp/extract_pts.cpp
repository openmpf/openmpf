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

#include <exception>
#include <iostream>

#include "PtsUtil.h"

int main(int argc, char *argv[]) {
    if (argc != 2) {
        std::cerr << "Expected a single parameter, but found: " << (argc - 1) << '\n';
        std::cerr << "Usage: " << argv[0] << " <video-path>\n";
        return 1;
    }
    const auto* inputFile = argv[1];
    if (inputFile[0] == '\0') {
        std::cerr << "Path to video was empty\n";
        return 2;
    }

    try {
        auto ptsResult = extractPts(inputFile);
        for (auto ptsVal : ptsResult.values) {
            std::cout << ptsVal << '\n';
        }
        return 0;
    }
    catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << '\n';
        return 3;
    }
    catch (...) {
        std::cerr << "Caught non-exception object\n";
        return 4;
    }
}
