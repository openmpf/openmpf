/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.service.component;


import org.mitre.mpf.rest.api.component.ComponentState;

public class ComponentRegistrationStatusException extends ComponentRegistrationException {

    private final ComponentState _componentState;

    public ComponentRegistrationStatusException(ComponentState componentState) {
        super(getMessage(componentState));
        _componentState = componentState;
    }

    public ComponentState getComponentState() {
        return _componentState;
    }

    private static String getMessage(ComponentState componentState) {
        switch (componentState) {
            case REGISTERING:
                return "The component is already being registered!";
            case REGISTERED:
                return "The component is already registered!";
            case UPLOAD_ERROR:
                return "The component previously failed to upload and cannot be registered!";
            default:
                return String.format(
                        "The component is an unhandled state of '%s' and cannot be registered!", componentState);
        }
    }
}
