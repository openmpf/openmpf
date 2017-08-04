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

package org.mitre.mpf.wfm.data.entities.transients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.util.TextUtils;

public class Candidate implements Comparable<Candidate> {
	private String candidateId;
	public String getCandidateId() { return candidateId; }

	private int rank;
	public int getRank() { return rank; }

	private double score;
	public double getScore() { return score; }

	@JsonCreator
	public Candidate(@JsonProperty("candidateId") String candidateId,
	                 @JsonProperty("rank") int rank,
	                 @JsonProperty("score") double score) {
		this.candidateId = candidateId;
		this.rank = rank;
		this.score = score;
	}

	public int hashCode() {  return rank; }
	public boolean equals(Object other) {
		if(other == null || !(other instanceof Candidate)) {
			return false;
		} else {
			Candidate casted = (Candidate)other;
			return TextUtils.nullSafeEquals(candidateId, casted.candidateId) && rank == casted.rank && Double.compare(score, casted.score) == 0;
		}
	}
	public int compareTo(Candidate other) {
		int result;
		if(other == null) {
			return 1;
		} else if((result = TextUtils.nullSafeCompare(candidateId, other.candidateId)) != 0 ||
				(result = Integer.compare(rank, other.rank)) != 0 ||
				(result = Double.compare(score, other.score)) != 0) {
			return result;
		} else {
			return 0;
		}
	}

	public String toString() {
		return String.format("%s#<candidateId='%s', rank=%d, score=%f>", this.getClass().getSimpleName(), candidateId, rank, score);
	}
}
