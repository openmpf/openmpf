/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

public class MpfEndpoints {

    public static final String
            ARTIFACT_EXTRACTION_WORK_QUEUE = "jms:MPF.ARTIFACT_EXTRACTION_WORK_QUEUE",
            MEDIA_INSPECTION_ENTRY_POINT = "jms:MPF.MEDIA_INSPECTION",

            MEDIA_INSPECTION_WORK_QUEUE = "jms:MPF.MEDIA_INSPECTION_WORK_QUEUE",
            MEDIA_RETRIEVAL_ENTRY_POINT = "jms:MPF.MEDIA_RETRIEVAL",
            MEDIA_RETRIEVAL_WORK_QUEUE = "jms:MPF.MEDIA_RETRIEVAL_WORK_QUEUE",

            CANCELLED_DETECTIONS = "jms:MPF.CANCELLED_DETECTIONS",

            COMPLETED_DETECTIONS = "jms:MPF.COMPLETED_DETECTIONS",
            COMPLETED_DETECTIONS_REPLY_TO = "queue://MPF.COMPLETED_DETECTIONS",

            CANCELLED_MARKUPS = "jms:MPF.CANCELLED_MARKUPS",
            COMPLETED_MARKUP = "jms:MPF.COMPLETED_MARKUP",

            JOB_REQUESTS = "jms:MPF.JOB_REQUESTS",
            TASK_RESULTS_AGGREGATOR = "direct:jobRouterTaskAggregator",

            UNSOLICITED_MESSAGES = "jms:MPF.UNSOLICITED_MESSAGES",

            DEAD_LETTER_QUEUE = "activemq:ActiveMQ.DLQ",
            DLQ_PROCESSED_MESSAGES = "jms:MPF.DLQ_PROCESSED_MESSAGES",
            DLQ_INVALID_MESSAGES = "jms:MPF.DLQ_INVALID_MESSAGES";


    private MpfEndpoints() {
    }
}
