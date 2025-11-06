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

package org.mitre.mpf.wfm.data.entities.persistent;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.test.MockitoTest;

import com.google.common.collect.ImmutableList;

public class TestBatchJobImplTiming extends MockitoTest.Strict {
    private BatchJobImpl _job;
    private Action _action1;
    private Action _action2;

    @Before
    public void init() {
        var pipelineElements = mock(JobPipelineElements.class);
        _action1 = new Action("ACTION1", null, null, List.of());
        _action2 = new Action("ACTION2", null, null, List.of());
        when(pipelineElements.getAllActions())
                .thenReturn(ImmutableList.of(_action1, _action2));
        _job = new BatchJobImpl(
                0,
                null,
                null,
                pipelineElements,
                0,
                null,
                null,
                List.of(),
                Map.of(),
                Map.of());
    }


    @Test
    public void testGetProcessingTime() {
        _job.addProcessingTime(_action1, 10);
        _job.addProcessingTime(_action2, 11);
        _job.addProcessingTime(_action1, 12);
        _job.addProcessingTime(_action2, 13);

        assertEquals(22, _job.getProcessingTime(_action1));
        assertEquals(24, _job.getProcessingTime(_action2));
        assertEquals(46, _job.getTotalProcessingTime());
    }


    @Test
    public void processingTimeIsInitiallyNegative() {
        assertEquals(-1, _job.getProcessingTime(_action1));
        assertEquals(-1, _job.getProcessingTime(_action2));
        assertEquals(-1, _job.getTotalProcessingTime());
    }


    @Test
    public void totalProcessingTimeIsNegativeWhenAnyActionMissingTime() {
        _job.addProcessingTime(_action1, 10);
        _job.addProcessingTime(_action1, 5);

        assertEquals(15, _job.getProcessingTime(_action1));
        assertEquals(-1, _job.getProcessingTime(_action2));
        assertEquals(-1, _job.getTotalProcessingTime());
    }


    @Test
    public void processingTimeIsNegativeWhenAnySubJobDoesNotHaveTiming() {
        _job.addProcessingTime(_action1, 10);
        _job.addProcessingTime(_action2, 11);
        _job.addProcessingTime(_action1, 12);
        _job.addProcessingTime(_action2, 13);

        _job.addProcessingTime(_action1, -1);
        _job.addProcessingTime(_action1, 100);

        assertEquals(-1, _job.getProcessingTime(_action1));
        assertEquals(24, _job.getProcessingTime(_action2));
        assertEquals(-1, _job.getTotalProcessingTime());
    }
}
