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


package org.mitre.mpf.wfm.service.pipeline;

import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;

import java.util.Collection;

public interface PipelineService {

    public JobPipelineElements getBatchPipelineElements(String pipelineName);

    public JobPipelineElements getStreamingPipelineElements(String pipelineName);

    public Algorithm getAlgorithm(String name);

    public Collection<Algorithm> getAlgorithms();


    public Action getAction(String name);

    public Collection<Action> getActions();


    public Task getTask(String name);

    public Collection<Task> getTasks();


    public Pipeline getPipeline(String name);

    public Collection<Pipeline> getPipelines();


    public void save(Algorithm algorithm);

    public void save(Action action);

    public void save(Task task);

    public void save(Pipeline pipeline);


    public void verifyBatchPipelineRunnable(String pipelineName);

    public void verifyStreamingPipelineRunnable(String pipelineName);

    public boolean pipelineSupportsBatch(String pipelineName);

    public boolean pipelineSupportsStreaming(String pipelineName);


    public void deleteAlgorithm(String algorithmName);

    public void deleteAction(String actionName);

    public void deleteTask(String taskName);

    public void deletePipeline(String pipelineName);

    public JsonPipeline createBatchJsonPipeline(String pipelineName);

    public JsonPipeline createStreamingJsonPipeline(String pipelineName);
}
