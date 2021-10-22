/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import java.util.Comparator;
import java.util.Objects;

public class TimePair implements Comparable<TimePair> {

	private final int startInclusive;

	public int getStartInclusive() { return startInclusive; }

	private final int endInclusive;

	public int getEndInclusive() { return endInclusive; }


	public TimePair(int startInclusive, int endInclusive) {
		this.startInclusive = startInclusive;
		this.endInclusive = endInclusive;
	}

public TimePair() {
		this.startInclusive = -1;
		this.endInclusive = -1;
}

	@Override
	public int hashCode() {
		return Objects.hash(startInclusive, endInclusive);
	}


	@Override
	public boolean equals(Object other) {
		if (!(other instanceof TimePair)) {
			return false;
		}
		TimePair casted = (TimePair) other;
		return compareTo(casted) == 0;
	}


	private static final Comparator<TimePair> DEFAULT_COMPARATOR = Comparator
			.nullsFirst(Comparator
				.comparingInt(TimePair::getStartInclusive)
				.thenComparingInt(TimePair::getEndInclusive));

	@Override
	public int compareTo(TimePair other) {
		return DEFAULT_COMPARATOR.compare(this, other);
	}


	public int length() {
		return endInclusive - startInclusive + 1;
	}


	@Override
	public String toString() {
		return getClass() .getSimpleName() + ": startInclusive=" + startInclusive + ", endInclusive=" + endInclusive;
	}
}