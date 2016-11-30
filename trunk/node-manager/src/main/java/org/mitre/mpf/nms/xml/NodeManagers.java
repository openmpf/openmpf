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

package org.mitre.mpf.nms.xml;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


@XStreamAlias("nodeManagers")
public class NodeManagers {
    
    @XStreamImplicit(itemFieldName="nodeManager")
    private List<NodeManager> nodeManagers = new ArrayList<NodeManager>();

    public static NodeManagers fromXml(InputStream xml) {
        // Create Stax parser with default namespace
        StaxDriver driver = new StaxDriver();
        driver.getQnameMap().setDefaultNamespace("launch.xml.nms.mitre.org");
        XStream xstream = new XStream(driver);
        xstream.autodetectAnnotations(true);
        xstream.processAnnotations(NodeManagers.class);
        return (NodeManagers) xstream.fromXML(xml);  
    }
    
    public void add(NodeManager node) {
        nodeManagers.add(node);
    }

    public List<NodeManager> managers() {
        return nodeManagers;
    }
}
