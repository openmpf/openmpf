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

import java.util.HashMap;

public class AggregatePipelineStatsModel {
	/*
	 * Fields
	 */
	private long totalTime = 0L;	
	private long minTime = 0L;
	private long maxTime = 0L;
	private long count = 0L;
	private long validCount = 0L;
	HashMap<String, Long> states = new HashMap<String, Long>();
	
	/*
	 * Constructors
	 */
	public AggregatePipelineStatsModel() {}
	
	public AggregatePipelineStatsModel(long totalTime, long minTime, long maxTime, 
			long count, long validCount, HashMap<String, Long> states) {
		this.totalTime = totalTime;
		this.minTime = minTime;
		this.maxTime = maxTime;
		this.count = count;
		this.validCount = validCount;
		this.states = states;
	}
	
	/*
	 * Getters and setters
	 */
	public long getTotalTime() {
		return totalTime;
	}
	public void setTotalTime(long totalTime) {
		this.totalTime = totalTime;
	}

	public long getMinTime() {
		return minTime;
	}
	public void setMinTime(long minTime) {
		this.minTime = minTime;
	}

	public long getMaxTime() {
		return maxTime;
	}
	public void setMaxTime(long maxTime) {
		this.maxTime = maxTime;
	}

	public long getCount() {
		return count;
	}
	public void setCount(long count) {
		this.count = count;
	}

	public long getValidCount() {
		return validCount;
	}
	public void setValidCount(long validCount) {
		this.validCount = validCount;
	}
	
	public HashMap<String, Long> getStates() {
		return states;
	}
	public void setStates(HashMap<String, Long> states) {
		this.states = states;
	}
}
