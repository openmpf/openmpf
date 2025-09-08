/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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

package org.mitre.mpf.test;

import java.time.Duration;
import java.time.Instant;

import org.mitre.mpf.wfm.service.ClockService;

public class MockClockService extends ClockService {

    private Instant _currentTime;

    public MockClockService(Instant startTime) {
        _currentTime = startTime;
    }

    public MockClockService() {
        this(Instant.now());
    }

    @Override
    public Instant now() {
        return _currentTime;
    }

    public void advance(Duration duration) {
        _currentTime = _currentTime.plus(duration);
    }

    public void set(Instant newTime) {
        _currentTime = newTime;
    }
}
