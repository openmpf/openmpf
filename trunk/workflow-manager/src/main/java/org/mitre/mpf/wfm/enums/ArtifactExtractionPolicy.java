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

package org.mitre.mpf.wfm.enums;

import org.apache.commons.lang3.StringUtils;

public enum ArtifactExtractionPolicy {
	/** Never extract detected objects from the medium. */
	NONE,

	/** Default: Extract the exemplar detection in the track provided the track is associated with a &quot;visual&quot; object type. For example, this would include faces and cars, but it would exclude speech and motion. */
	VISUAL_EXEMPLARS_ONLY,

	/** Extract the exemplar detection from the track regardless of the track's object type. */
	EXEMPLARS_ONLY,

	/** Extract all detections in the track provided the track is associated with a &quot;visual&quot; object type. For example, this would include faces and cars, but it would exclude speech and motion. */
	ALL_VISUAL_DETECTIONS,

	/** Extract all detections in the track regardless of the track's object type. */
	ALL_DETECTIONS;

	public static final ArtifactExtractionPolicy DEFAULT = VISUAL_EXEMPLARS_ONLY;

	/**
	 * Retrieves the enum value with the given name. If an enum value with the given name does not exist, the defaultValue
	 * parameter is used.
	 *
	 * @param name The named enum value to retrieve. Leading/trailing whitespace is ignored, and case is not important.
     * @param defaultValue default value parameter to be used if the provided name is not valid.
     * @return enum value with the given name or the default value if a policy of the specified name is not found.
	 */
	public static ArtifactExtractionPolicy parse(String name, ArtifactExtractionPolicy defaultValue) {
		name = StringUtils.trimToNull(name);
		for(ArtifactExtractionPolicy artifactExtractionPolicy : ArtifactExtractionPolicy.values()) {
			if(artifactExtractionPolicy.name().equals(name)) {
				return artifactExtractionPolicy;
			}
		}

		// Failed to find a matching name. Use the default.
		return defaultValue;
	}

    /**
     * Retrieves the enum value with the given name. If an enum value with the given name does not exist,
     * ArtifactExtractionPolicy.DEFAULT is used.
     *
     * @param name The named enum value to retrieve. Leading/trailing whitespace is ignored, and case is not important.
     * @return enum value with the given name or the default value if a policy of the specified name is not found.
     */
    public static ArtifactExtractionPolicy parse(String name) {
        return ArtifactExtractionPolicy.parse(name,DEFAULT);
    }
}
