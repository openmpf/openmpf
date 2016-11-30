/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

package org.mitre.mpf.audioVideo.util;


public class MPFAudioAttributes {

    private Integer lowpassCutoffFrequency = null;
    private Integer highpassCutoffFrequency = null;
    private String codec = null;
    private Integer samplingRate = null;
    private Integer channels = null;
    private Integer volume = null;

    public String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public Integer getSamplingRate() {
        return samplingRate;
    }

    public void setSamplingRate(Integer samplingRate) {
        this.samplingRate = samplingRate;
    }

    public Integer getChannels() {
        return channels;
    }

    public void setChannels(Integer channels) {
        this.channels = channels;
    }

    public Integer getVolume() {
        return volume;
    }

    public void setVolume(Integer volume) {
        this.volume = volume;
    }

    public Integer getLowpassCutoffFrequency() {
        return lowpassCutoffFrequency;
    };

    public void setLowpassCutoffFrequency(Integer lowpassCutoffFrequency) {
        this.lowpassCutoffFrequency = lowpassCutoffFrequency;
    };

    public Integer getHighpassCutoffFrequency() {
        return highpassCutoffFrequency;
    }

    public void setHighpassCutoffFrequency(Integer highpassCutoffFrequency) {
        this.highpassCutoffFrequency = highpassCutoffFrequency;
    }

    public MPFAudioAttributes() {}

    public String toString() {
        String returnString = super.toString();
        returnString = returnString.substring(0, returnString.length() - 1);
        returnString += ", lowpassCutoffFrequency=" + lowpassCutoffFrequency + ", highpassCutoffFrequency= " + highpassCutoffFrequency + ")";
        return returnString;
    }
}
