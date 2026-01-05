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

package org.mitre.mpf.mvc.controller;

import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.Destination;
import org.mitre.mpf.rest.api.QueueInfo;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api("Queues")
@RestController
@RequestMapping(produces = "application/json")
public class QueueController {

    private final BrokerService _amqBrokerService;

    @Inject
    QueueController(BrokerService amqBrokerService) {
        _amqBrokerService = amqBrokerService;
    }


    @GetMapping("/rest/queues")
    @RequestEventId(value = LogAuditEventRecord.EventId.REST_QUEUES)
    @ApiOperation("Retrieves information about all queues.")
    public List<QueueInfo> getQueues() {
        return getQueuesInternal()
            .map(QueueController::createQueueInfo)
            .toList();
    }


    @GetMapping("/rest/queues/{name}")
    @RequestEventId(value = LogAuditEventRecord.EventId.REST_QUEUES)
    @ApiOperation("Retrieves information about a single queue.")
    @ApiResponses(@ApiResponse(code = 404, message = "Not found"))
    public ResponseEntity<QueueInfo> getQueue(@PathVariable String name) {
        var optQueueInfo = getQueuesInternal()
                .filter(q -> q.getName().equals(name))
                .findAny()
                .map(QueueController::createQueueInfo);
        return ResponseEntity.of(optQueueInfo);
    }


    private Stream<Destination> getQueuesInternal() {
        Broker broker;
        try {
            broker = _amqBrokerService.getBroker();
        }
        catch (Exception e) {
            throw new IllegalStateException("Could not access broker due to: " + e, e);
        }
        return broker.getDestinationMap()
                .values()
                .stream()
                .filter(d -> d.getActiveMQDestination().isQueue());
    }

    private static QueueInfo createQueueInfo(Destination destination) {
        return new QueueInfo(
                destination.getName(),
                destination.getDestinationStatistics().getMessages().getCount());
    }
}
