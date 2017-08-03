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

package org.mitre.mpf.wfm.util;

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

public class TimePair implements Comparable<TimePair> {
	private int startInclusive;
	public int getStartInclusive() { return startInclusive; }

	private int endInclusive;
	public int getEndInclusive() { return endInclusive; }

	public TimePair(int startInclusive, int endInclusive) {
		this.startInclusive = startInclusive;
		this.endInclusive = endInclusive;
	}

	public int hashCode() {
		int result = 37;
		result = 37 * result + startInclusive;
		result = 37 * result + endInclusive;
		return result;
	}

	public boolean equals(Object other) {
		if(other == null || !(other instanceof TimePair)) {
			return false;
		} else {
			TimePair casted = (TimePair)other;
			return startInclusive == casted.startInclusive && endInclusive == casted.endInclusive;
		}
	}

	public int compareTo(TimePair other) {
		int result = 0;
		if(other == null) {
			result =  1;
		} else if ( (result = Integer.compare(startInclusive, other.startInclusive)) == 0 &&
				(result = Integer.compare(endInclusive, other.endInclusive)) == 0) {
			result =  0;
		}
		return result;
	}

	public int length() {
		return endInclusive - startInclusive + 1;
	}
}