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

package org.mitre.mpf.wfm.data.entities.persistent;

import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;

/**
 * This class includes the essential information which describes streaming job status.
 */
public class StreamingJobStatus {

    private final String _detail;
    public String getDetail() { return _detail; }

    private final StreamingJobStatusType _type;
    public StreamingJobStatusType getType() { return _type; }

    public boolean isTerminal() {
        return _type.isTerminal();
    }


    public StreamingJobStatus(StreamingJobStatusType type) {
        this(type, null);
    }

    public StreamingJobStatus(StreamingJobStatusType type, String detail) {
        _type = type;
        _detail = detail;
    }


    @Override
    public String toString() {
        return StringUtils.isBlank(_detail)
                ? _type.toString()
                : String.format("%s: %s", _type, _detail);
    }
}
