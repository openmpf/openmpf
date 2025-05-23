/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import java.util.HashMap;
import java.util.Map;

public class AllJobsStatisticsModel {
	/*
	 * Fields
	 */
	private long elapsedTimeMs = 0;
	private int totalJobs = 0;
	private int jobTypes = 0;
	private Map<String,AggregatePipelineStatsModel> aggregatePipelineStatsMap = new HashMap<String,AggregatePipelineStatsModel>();
	
	/*
	 * Constructors
	 */
	public AllJobsStatisticsModel() {}
	
	/*
	 * Getters and setters
	 */
	public long getElapsedTimeMs() {
		return elapsedTimeMs;
	}
	public void setElapsedTimeMs(long elapsedTimeMs) {
		this.elapsedTimeMs = elapsedTimeMs;
	}
	
	public int getTotalJobs() {
		return totalJobs;
	}	
	public void setTotalJobs(int totalJobs) {
		this.totalJobs = totalJobs;
	}
	
	public int getJobTypes() {
		return jobTypes;
	}
	public void setJobTypes(int jobTypes) {
		this.jobTypes = jobTypes;
	}
		
	public Map<String, AggregatePipelineStatsModel> getAggregatePipelineStatsMap() {
		return aggregatePipelineStatsMap;
	}	
	public void setAggregatePipelineStatsMap(Map<String, AggregatePipelineStatsModel> aggregatePipelineStatsMap) {
		this.aggregatePipelineStatsMap = aggregatePipelineStatsMap;
	}	
}

