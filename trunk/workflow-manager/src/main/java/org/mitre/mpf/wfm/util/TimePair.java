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

import java.util.Objects;

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

	private int endInclusive = -1;
	public int getEndInclusive() { return endInclusive; }

  /** Set the ending value for an unbounded TimePair.
   * This method supports Objects which may have been ordered by some other parameter besides time or frame offset.
   * @param endInclusive inclusive value when this TimePair ends.  May be less than startInclusive, if so the values will be adjusted such that startInclusive <= endInclusive
   */
	public void setEndInclusive(int endInclusive) {
	  // supporting construction from Objects who may have been ordered by some other parameter besides time or frame offset.
	  if ( endInclusive >= startInclusive ) {
      this.endInclusive = endInclusive;
    } else {
      this.endInclusive = startInclusive;
      startInclusive = endInclusive;
    }
	}

  /** Constructor allows for creation of an unbounded TimePair.
   * An unbounded TimePair will have the endInclusive value set at some later time.
   * @param startInclusive inclusive value when this TimePair starts.
   */
  public TimePair(int startInclusive) {
    this.startInclusive = startInclusive;
  }

	public TimePair(int startInclusive, int endInclusive) {
		this.startInclusive = startInclusive;
		this.endInclusive = endInclusive;
	}

  /** Tests if this TimePair is unbounded.
   * @return Returns true if this TimePair is unbounded (i.e. endInclusive has not been set)
   */
	public boolean isUnboundedTimePair() { return endInclusive == -1; }

  /** Tests if this TimePair is closed.
   * @return Returns true if this TimePair is closed (i.e. it is not unbounded)
   */
  public boolean isClosedTimePair() { return !isUnboundedTimePair(); }

  @Override
	public int hashCode() {
	  return Objects.hash(startInclusive,endInclusive);
	}

  @Override
	public boolean equals(Object other) {
		if(other == null || !(other instanceof TimePair)) {
			return false;
		} else {
			TimePair casted = (TimePair)other;
			return startInclusive == casted.startInclusive && endInclusive == casted.endInclusive;
		}
	}

  @Override
	public int compareTo(TimePair other) {
		int result = 0;
		if(other == null) {
		  // kept original assumption that the other TimePair is after this TimePair if the other TimePair is null
			result =  1;
		} else if ( (result = Integer.compare(startInclusive, other.startInclusive)) == 0 &&
				(result = Integer.compare(endInclusive, other.endInclusive)) == 0) {
		  // TimePairs are equivalent if the start/end boundaries are equal
			result =  0;
		} else if ( this.startInclusive < other.startInclusive ) {
		  // This TimePair starts before the other TimePair, so it is assumed to be less than (i.e. starts before) the other TimePair
      result = -1;
    } else {
		  // This TimePair starts after the other TimePair, so it is assumed to be greater than (i.e. starts after) the other TimePair
		  return 1;
    }
		return result;
	}

	public int length() {
		return endInclusive - startInclusive + 1;
	}

  @Override
	public String toString() {
		return(this.getClass().getSimpleName()+": startInclusive="+startInclusive+", endInclusive="+endInclusive);
	}
}