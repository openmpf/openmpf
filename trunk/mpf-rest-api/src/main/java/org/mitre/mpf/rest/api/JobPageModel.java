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

package org.mitre.mpf.rest.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class JobPageModel extends SingleJobInfo {

    private boolean outputFileExists = false;

    public boolean isOutputFileExists() {
        return outputFileExists;
    }

    public void setOutputFileExists(boolean outputFileExists) {
        this.outputFileExists = outputFileExists;
    }

    public JobPageModel() {
    }

    public JobPageModel(SingleJobInfo job) {
        super(job.getJobId(), job.getPipelineName(), job.getJobPriority(), job.getJobStatus(), job.getJobProgress(), job.getStartDate(), job.getEndDate(), job.getOutputObjectPath(), job.isTerminal());
    }
}