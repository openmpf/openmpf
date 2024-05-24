/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.Objects;

public class MediaRange implements Comparable<MediaRange> {

	private final int startInclusive;

	public int getStartInclusive() { return startInclusive; }

	private final int endInclusive;

	public int getEndInclusive() { return endInclusive; }


	public MediaRange(@JsonProperty("startInclusive") int startInclusive,
	                  @JsonProperty("endInclusive") int endInclusive) {
		this.startInclusive = startInclusive;
		this.endInclusive = endInclusive;
	}


	@Override
	public int hashCode() {
		return Objects.hash(startInclusive, endInclusive);
	}


	@Override
	public boolean equals(Object other) {
		if (!(other instanceof MediaRange)) {
			return false;
		}
		MediaRange casted = (MediaRange) other;
		return compareTo(casted) == 0;
	}


	private static final Comparator<MediaRange> DEFAULT_COMPARATOR = Comparator
			.nullsFirst(Comparator
				.comparingInt(MediaRange::getStartInclusive)
				.thenComparingInt(MediaRange::getEndInclusive));

	@Override
	public int compareTo(MediaRange other) {
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
