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

package org.mitre.mpf.nms.xml;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


@XStreamAlias("nodeManagers")
public class NodeManagers {

    @XStreamImplicit(itemFieldName="nodeManager")
    private List<NodeManager> nodeManagers = new ArrayList<NodeManager>();

    public static NodeManagers fromXml(InputStream inputStream) {
        // Create Stax parser with default namespace
        StaxDriver driver = new StaxDriver();
        driver.getQnameMap().setDefaultNamespace("launch.xml.nms.mitre.org");
        XStream xstream = new XStream(driver);
        xstream.autodetectAnnotations(true);
        xstream.processAnnotations(NodeManagers.class);
        NodeManagers retval = (NodeManagers) xstream.fromXML(inputStream);

        // Stax parser will set the collection to null if the XML has no entries; instead use an empty collection
        if (retval.getAll() == null) {
            retval.setAll(new ArrayList<>());
        }
        return retval;
    }

    public static void toXml(NodeManagers managers, OutputStream outputStream) {
        XStream xStream = new XStream();
        //just adding all of them from the start - do not see a disadvantage from doing this
        xStream.processAnnotations(NodeManagers.class);
        xStream.processAnnotations(NodeManager.class);
        xStream.processAnnotations(Service.class);
        xStream.processAnnotations(EnvironmentVariable.class);

        // Prevent using references. For example, when two NodeManager objects reference the same Service object:
        // <nodeManager target="somehost2">
        //    <service reference="../../nodeManager/service"/>
        //  </nodeManager>
        xStream.setMode(XStream.NO_REFERENCES);

        xStream.toXML(managers, outputStream);
    }

    public List<NodeManager> getAll() {
        return nodeManagers;
    }

    public void setAll(List<NodeManager> nodeManagers) {
        this.nodeManagers = nodeManagers;
    }

    public void add(NodeManager node) {
        nodeManagers.add(node);
    }
}