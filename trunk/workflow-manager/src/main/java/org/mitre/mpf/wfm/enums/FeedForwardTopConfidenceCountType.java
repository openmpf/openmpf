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

package org.mitre.mpf.wfm.enums;

/**
 * Created by mpf on 8/16/17.
 */
public enum FeedForwardTopConfidenceCountType {
  /** If <= 0, then use all of the detections in the feed-forward track. (Default) **/
  USE_ALL_DETECTIONS_IN_TRACK(0),
  DEFAULT(0),

  /** If set to 1, then only use the exemplar in the feed-forward track. */
  USE_EXEMPLAR_IN_TRACK(1),

  /** If set > 1, use that many detections from the feed-forward track with the highest confidence. */
  USE_HIGHEST_CONFIDENCE(2);

  private int feedForwardTopConfidenceCountType;

  private FeedForwardTopConfidenceCountType(int feedForwardTopConfidenceCountType) { this.feedForwardTopConfidenceCountType = feedForwardTopConfidenceCountType; }

  public int getValue() {
    return feedForwardTopConfidenceCountType;
  }

  /** Convert integer value to it's comparable FeedForwardTopConfidenceCountType
   * @param feedForwardTopConfidenceCountType integer value of the feed forward top confidence count
   * @return FeedForwardTopConfidenceCountType enumeration
   */
  public FeedForwardTopConfidenceCountType getType(int feedForwardTopConfidenceCountType) {
    if (feedForwardTopConfidenceCountType == USE_ALL_DETECTIONS_IN_TRACK.getValue() ) {
      return USE_ALL_DETECTIONS_IN_TRACK;
    } else if ( feedForwardTopConfidenceCountType == USE_EXEMPLAR_IN_TRACK.getValue() ) {
      return USE_EXEMPLAR_IN_TRACK;
    } else if ( feedForwardTopConfidenceCountType == USE_HIGHEST_CONFIDENCE.getValue() ) {
      return USE_HIGHEST_CONFIDENCE;
    } else {
      // return default
      return DEFAULT;
    }
  }
}
