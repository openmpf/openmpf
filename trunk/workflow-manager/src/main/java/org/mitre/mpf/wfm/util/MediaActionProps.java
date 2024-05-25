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

import java.util.Map;
import java.util.function.BiFunction;

import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.data.entities.persistent.Media;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;


public class MediaActionProps {

    private final BiFunction<Media, Action, Map<String, String>> _propMapGetter;

    private final Table<Long, String, Map<String, String>> _propMapCache = HashBasedTable.create();


    public MediaActionProps(BiFunction<Media, Action, Map<String, String>> propMapGetter) {
        _propMapGetter = propMapGetter;
    }

    public Map<String, String> get(Media media, Action action) {
        var cached = _propMapCache.get(media.getId(), action.name());
        if (cached != null) {
            return cached;
        }
        var props = _propMapGetter.apply(media, action);
        _propMapCache.put(media.getId(), action.name(), props);
        return props;
    }

    public String get(String propName, Media media, Action action) {
        return get(media, action).get(propName);
    }
}
