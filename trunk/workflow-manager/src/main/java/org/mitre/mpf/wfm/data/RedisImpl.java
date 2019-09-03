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

package org.mitre.mpf.wfm.data;

import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.stream.Collectors.toCollection;


@Component
public class RedisImpl implements Redis {

    private static final Logger log = LoggerFactory.getLogger(RedisImpl.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JsonUtils jsonUtils;


    @PostConstruct
    private synchronized void init() {
        log.info("Flushing Redis.");
        redisTemplate.execute((RedisCallback<Void>) redisConnection -> {
            redisConnection.flushAll();
            return null;
        });
    }


    @Override
    public void addTrack(Track track) {
        redisTemplate.boundListOps(createTrackKey(track))
                .rightPush(jsonUtils.serialize(track));
    }

    @Override
    public synchronized void setTracks(long jobId, long mediaId, int taskIndex, int actionIndex,
                                       Collection<Track> tracks) {
        String key = createTrackKey(jobId, mediaId, taskIndex, actionIndex);
        redisTemplate.delete(key);
        BoundListOperations<String, Object> redisTracks = redisTemplate.boundListOps(key);
        for (Track track : tracks) {
            redisTracks.rightPush(jsonUtils.serialize(track));
        }
    }


    @Override
    public synchronized SortedSet<Track> getTracks(long jobId, long mediaId, int taskIndex, int actionIndex) {
        return redisTemplate
                .boundListOps(createTrackKey(jobId, mediaId, taskIndex, actionIndex))
                .range(0, -1)
                .stream()
                .map(o -> jsonUtils.deserialize((byte[]) o, Track.class))
                .collect(toCollection(TreeSet::new));
    }


    @Override
    public void clearTracks(BatchJob job) {
        Collection<String> trackKeys = new ArrayList<>();
        int taskCount = job.getPipelineElements().getPipeline().getTasks().size();
        for (Media media : job.getMedia()) {
            for (int taskIndex = 0; taskIndex < taskCount; taskIndex++) {
                int actionCount = job.getPipelineElements().getTask(taskIndex).getActions().size();
                for (int actionIndex = 0; actionIndex < actionCount; actionIndex++) {
                    trackKeys.add(createTrackKey(job.getId(), media.getId(), taskIndex, actionIndex));
                }
            }
        }
        redisTemplate.delete(trackKeys);
    }


    private static String createTrackKey(Track track) {
        return createTrackKey(track.getJobId(), track.getMediaId(), track.getTaskIndex(), track.getActionIndex());

    }

    private static String createTrackKey(long jobId, long mediaId, int taskIndex, int actionIndex) {
        return String.format("BATCH_JOB:%s:MEDIA:%s:TASK:%s:ACTION:%s:TRACKS",
                             jobId, mediaId, taskIndex, actionIndex);
    }
}
