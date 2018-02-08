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

package org.mitre.mpf.component.executor.detection;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class ProtoUtils {
	
    public static Map<String, Object> copyMsgProperties(Message src) throws JMSException {
        Map<String, Object> objectProperties = new HashMap<String, Object>();
        @SuppressWarnings("rawtypes")
        Enumeration e = src.getPropertyNames();
        while (e.hasMoreElements()) {
            String k = (String) e.nextElement();
            objectProperties.put(k, src.getObjectProperty(k));
        }
        return objectProperties;
    }

    /**
     * Sets message properties using the given mapping.
     * 
     * @param properties
     * @param dsc
     * @throws JMSException 
     */
    public static void setMsgProperties(Map<String, Object> properties, Message dsc) throws JMSException {
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            dsc.setObjectProperty(e.getKey(), e.getValue());
        }
    }

}
