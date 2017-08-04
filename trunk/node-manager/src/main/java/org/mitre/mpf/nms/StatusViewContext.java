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

package org.mitre.mpf.nms;

import org.jgroups.Address;
import org.jgroups.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class StatusViewContext {

    private static final Logger LOG = LoggerFactory.getLogger(StatusViewContext.class);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final String _creationTime = DATE_FORMAT.format(new Date());

    private final List<NodeAddressInfo> _nodeAddresses;

    private final List<ManagerGrouping> _managers;


    public StatusViewContext(
                Address localAddress,
                Collection<Address> allAddresses,
                Collection<NodeDescriptor> nodes,
                Collection<ServiceDescriptor> services) {

        _nodeAddresses = allAddresses.stream()
                .map(addr -> new NodeAddressInfo(addr, localAddress))
                .collect(toList());

        Map<String, List<ServiceDescriptor>> groupedServices = services.stream()
                .collect(groupingBy(ServiceDescriptor::getHost));

        _managers = nodes.stream()
                .map(nd -> new ManagerGrouping(nd, groupedServices.get(nd.getHostname())))
                .collect(toList());

    }


    public Collection<ManagerGrouping> getManagers() {
        return _managers;
    }

    public List<NodeAddressInfo> getNodeAddresses() {
        return _nodeAddresses;
    }

    public String getCreationTime() {
        return _creationTime;
    }



    private static class ManagerGrouping {
        private final NodeDescriptor _manager;
        private final List<ServiceDescriptor> _serviceDescriptors;
        private final URI _nodeUri;

        ManagerGrouping(NodeDescriptor manager, Collection<ServiceDescriptor> serviceDescriptors) {
            _manager = manager;
            _serviceDescriptors = serviceDescriptors == null
                    ? Collections.emptyList()
                    : new ArrayList<>(serviceDescriptors);
            _nodeUri = createUri(manager);
        }


        private static URI createUri(NodeDescriptor manager) {
            try {
                return new URI("http", null, manager.getHostname(), NodeManager.getHttpPort(), null, null, null);
            }
            catch (URISyntaxException e) {
                LOG.warn(String.format("Failed to create URL from the hostname %s", manager.getHostname()), e);
                return null;
            }
        }

        public NodeDescriptor getManager() {
            return _manager;
        }

        public List<ServiceDescriptor> getServiceDescriptors() {
            return _serviceDescriptors;
        }

        public URI getNodeUri() {
            return _nodeUri;
        }
    }




    private static class NodeAddressInfo {
        private final String _address;
        private final boolean _isCurrentConnection;

        public NodeAddressInfo(Address address, Address localAddress) {
            _isCurrentConnection = address.equals(localAddress);
            if (address instanceof UUID) {
                _address = String.format("%s [%s]\n", address.toString(), ((UUID) address).toStringLong());
            }
            else {
                _address = String.format("%s\n", address.toString());
            }
        }

        public String getAddress() {
            return _address;
        }

        public boolean isCurrentConnection() {
            return _isCurrentConnection;
        }
    }
}
