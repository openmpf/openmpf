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

package org.mitre.mpf.rest.api;

import java.util.ArrayList;
import java.util.List;


public class AllStreamingJobsInfoModel {
    private List<Long> jobIds = new ArrayList<Long>();

	/** Constructor
	 */
	public AllStreamingJobsInfoModel() {}

	/** add the job id of another streaming job to this collector.
	 * @param jobId streaming job id to be added
	 */
	public void addJob(long jobId) {
		jobIds.add(Long.valueOf(jobId));
	}

    /** get the list of all streaming job ids that have been collected.
     * @return list of all streaming job ids that have been collected
     */
    public List<Long> getJobIds() {
        return jobIds;
    }

}
