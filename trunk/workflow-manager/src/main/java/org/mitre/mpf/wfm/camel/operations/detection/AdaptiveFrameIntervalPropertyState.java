/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.operations.detection;

import java.util.Map;
import javax.annotation.PostConstruct;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdaptiveFrameIntervalPropertyState {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveFrameIntervalPropertyState.class);

    // OpenMPF supports adaptive frame interval override at the same property level by frame rate cap,
    // override of properties at higher ranked property levels, or disable of properties at higher property levels.
    // In order to track where FRAME_INTERVAL or FRAME_RATE_CAP properties
    // may have been overridden or disabled at a specific property level, we need to track at which level the
    // override or disable of either of these two properties occurred. Note
    // that system properties provide the default values, so the system property level is not included here.
    public enum PropertyLevel { NONE, ACTION_LEVEL, JOB_LEVEL, ALGORITHM_LEVEL, MEDIA_LEVEL };
    public enum PropertyStatus { NOT_FOUND, FOUND_NOT_DISABLED, FOUND_DISABLED};

    private PropertyLevel frameIntervalPropertyLevel;
    public PropertyLevel getFrameIntervalPropertyLevel() { return frameIntervalPropertyLevel; }

    private PropertyStatus frameIntervalPropertyStatus;
    public boolean isFrameIntervalPropertySpecifiedAndDisabled() { return frameIntervalPropertyStatus == PropertyStatus.FOUND_DISABLED; }
    public boolean isFrameIntervalPropertySpecifiedAndNotDisabled() { return frameIntervalPropertyStatus == PropertyStatus.FOUND_NOT_DISABLED; }
    public boolean isFrameIntervalPropertyNotSpecified() { return frameIntervalPropertyStatus == PropertyStatus.NOT_FOUND; }

    private PropertyLevel frameRateCapPropertyLevel;
    public PropertyLevel getFrameRateCapPropertyLevel() { return frameRateCapPropertyLevel; }

    private PropertyStatus frameRateCapPropertyStatus;
    public boolean isFrameRateCapPropertySpecifiedAndDisabled() { return frameRateCapPropertyStatus == PropertyStatus.FOUND_DISABLED; }
    public boolean isFrameRateCapPropertySpecifiedAndNotDisabled() { return frameRateCapPropertyStatus == PropertyStatus.FOUND_NOT_DISABLED; }
    public boolean isFrameRateCapPropertyNotSpecified() { return frameRateCapPropertyStatus == PropertyStatus.NOT_FOUND; }

    // If true, then FRAME_RATE_CAP and FRAME_INTERVAL properties have been specified and not disabled
    // at the most recently processed property level (FRAME_RATE_CAP override of FRAME_INTERVAL condition applies)
    private boolean frameRateCapPropertyOverrideEnable = false;
    public boolean isFrameRateCapPropertyOverrideEnabled() { return frameRateCapPropertyOverrideEnable; }

    @PostConstruct
    public void init() {
        frameIntervalPropertyLevel = PropertyLevel.NONE;
        frameIntervalPropertyStatus = PropertyStatus.NOT_FOUND;
        frameRateCapPropertyLevel = PropertyLevel.NONE;
        frameRateCapPropertyStatus = PropertyStatus.NOT_FOUND;
        frameRateCapPropertyOverrideEnable = false;
        log.info("AdaptiveFrameIntervalPropertyState: initialized to this = " + this);
    }

    private void setFrameRateCapPropertyOverrideEnable() {
        // FRAME_RATE_CAP has been specified and not disabled, and
        // FRAME_INTERVAL has been specified and not disabled and FRAME_RATE_CAP was found
        // at the current or some higher ranked property level when compared to where FRAME_INTERVAL was found.
        // If this is the case, then enable the frame rate cap property override of frame interval.
        frameRateCapPropertyOverrideEnable = ( (frameRateCapPropertyLevel.ordinal() >= frameIntervalPropertyLevel.ordinal()) &&
                                                (frameIntervalPropertyStatus == PropertyStatus.FOUND_NOT_DISABLED) &&
                                                (frameRateCapPropertyStatus == PropertyStatus.FOUND_NOT_DISABLED));
    }

    public void updateFrameIntervalPropertyStatus(PropertyLevel propertyLevel, PropertyStatus propertyStatus) {
        // Keep track of changes to FRAME_INTERVAL property at the current property level.
        this.frameIntervalPropertyLevel = propertyLevel;
        this.frameIntervalPropertyStatus = propertyStatus;

        // Adjust the frame rate cap property override for the most recent update to FRAME_INTERVAL at this property level.
        setFrameRateCapPropertyOverrideEnable();
    }

    public void updateFrameRateCapPropertyStatus(PropertyLevel propertyLevel, PropertyStatus propertyStatus) {
        // Keep track of changes to FRAME_RATE_CAP property at the current property level.
        this.frameRateCapPropertyLevel = propertyLevel;
        this.frameRateCapPropertyStatus = propertyStatus;

        // Adjust the frame rate cap property override for the most recent update to FRAME_RATE_CAP at this property level.
        setFrameRateCapPropertyOverrideEnable();
    }

    /**
     * This method should be called for video media after each level of properties has to the modifiedMap. This method expects to be called with
     * the properties being applied in lowest to highest ranking order (i.e. called with action properties first, followed by job properties, etc).
     * For this reason, this method doesn't have to check for previously set indicators, properties at a higher rank will be allowed to
     * update any previously set conditions. When the highest ranked properties are applied last, the adaptiveFrameIntervalPropertyState should
     * reflect the correct state after FRAME_RATE_CAP and FRAME_INTERVAL properties are considered at each of the property levels.
     * This method will update adaptiveFrameIntervalPropertyState based upon properties at the specified property level.
     * @param appliedProperties the set of properties that are currently being processed into the sub-job property map.
     * @param currentPropertyLevel specifies which property level is currently being processed (action, job, algorithm, or media), where
     * media properties are the highest ranked set of properties.
     * @return adaptiveFrameIntervalPropertyState after the adaptive frame interval property stategy has been applied.
     */
    public void setAdaptiveFrameIntervalPropertyState(Map<String,String> appliedProperties, PropertyLevel currentPropertyLevel) {

        if ( appliedProperties.containsKey(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) ) {
            log.info("setAdaptiveFrameIntervalPropertyState, debug: " + currentPropertyLevel + " properties contains FRAME_INTERVAL with value = " +
                appliedProperties.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY));
            if ( Integer.valueOf(appliedProperties.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)) > 0 ) {
                updateFrameIntervalPropertyStatus(currentPropertyLevel, PropertyStatus.FOUND_NOT_DISABLED);
            } else {
                updateFrameIntervalPropertyStatus(currentPropertyLevel, PropertyStatus.FOUND_DISABLED);
            }
            log.info("setAdaptiveFrameIntervalPropertyState, debug: after application of FRAME_INTERVAL set current status to this=" + this);
        }

        if ( appliedProperties.containsKey(MpfConstants.FRAME_RATE_CAP_PROPERTY) ) {
            log.info("setAdaptiveFrameIntervalPropertyState, debug: " + currentPropertyLevel + " properties contains FRAME_RATE_CAP with value = " +
                appliedProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY));
            if ( Integer.valueOf(appliedProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY)) > 0 ) {
                updateFrameRateCapPropertyStatus(currentPropertyLevel, PropertyStatus.FOUND_NOT_DISABLED);
            } else {
                updateFrameRateCapPropertyStatus(currentPropertyLevel, PropertyStatus.FOUND_DISABLED);
            }
            log.info("setAdaptiveFrameIntervalPropertyState, debug: after application of FRAME_RATE_CAP set current status to this=" + this);
        }
    }

    public String toString() {
        return "AdaptiveFrameIntervalPropertyState: frameRateCapPropertyLevel = " + frameRateCapPropertyLevel + ", frameRateCapPropertyStatus = " + frameRateCapPropertyStatus +
            ", frameIntervalPropertyLevel = " + frameIntervalPropertyLevel + ", frameIntervalPropertyStatus = " + frameIntervalPropertyStatus +
            ", frameRateCapPropertyOverrideEnable = " + frameRateCapPropertyOverrideEnable;
    }

}
