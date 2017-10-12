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

package org.mitre.mpf.wfm.event;

import org.javasimon.aop.Monitored;
import org.mitre.mpf.wfm.service.PipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Monitored
public class JobProgress {
	
	private static final Logger log = LoggerFactory.getLogger(JobProgress.class);
	
    @Autowired
    private PipelineService pipelines;
	
    //trying to make this class thread safe - also added synchronized to some of the public methods - might need to use the keyword more
	private volatile Map<Long, Float> jobProgressMap = new HashMap<Long, Float>();
	
	public synchronized Map<Long, Float> getJobProgressMap() {
		return jobProgressMap;
	}
	
	//must be non primitive to support a null retrun
	public synchronized Float getJobProgress(long jobId){
		return jobProgressMap.get(jobId);
	}
	
	public synchronized void setJobProgress(long jobId, float jobProgress){
		jobProgressMap.put(jobId, jobProgress);
	}
}