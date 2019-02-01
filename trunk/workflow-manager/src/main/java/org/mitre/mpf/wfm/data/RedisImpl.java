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

package org.mitre.mpf.wfm.data;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
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

    // The following constants are provided to avoid making typographical errors when formulating keys.
    private static final String
            BATCH_JOB = "BATCH_JOB",
            MEDIA = "MEDIA",
            TRACK = "TRACK";

    /**
     * Creates a "key" from one or more components. This is a convenience method for creating
     * keys like BATCH_JOB:1:MEDIA:15:DETECTION_ERRORS or STREAMING_JOB:1:STREAM:15
     * @param component The required, non-null and non-empty root of the key.
     * @param components The optional collection of additional components in the key.
     * @return A single string built from the concatenation of all of the specified parameters and joined by a delimiter.
     */
    private static String key(Object component, Object... components) {
        return Stream.concat(Stream.of(component), Stream.of(components))
                .map(Object::toString)
                .collect(joining(":"));
    }


    @Override
    public void addTrack(Track track) throws WfmProcessingException {
        String key = key(BATCH_JOB, track.getJobId(),
                         MEDIA, track.getMediaId(),
                         TRACK, track.getStageIndex(),
                         track.getActionIndex());
        redisTemplate.boundListOps(key)
                .rightPush(jsonUtils.serialize(track));
    }


    @Override
    public synchronized void setTracks(long jobId, long mediaId, int taskIndex, int actionIndex,
                                       Collection<Track> tracks) {
        String key = key(BATCH_JOB, jobId, MEDIA, mediaId, TRACK, taskIndex, actionIndex);
        redisTemplate.delete(key);
        BoundListOperations<String, Object> redisTracks = redisTemplate.boundListOps(key);
        for (Track track : tracks) {
            redisTracks.rightPush(jsonUtils.serialize(track));
        }
    }


    @Override
    public synchronized SortedSet<Track> getTracks(long jobId, long mediaId, int taskIndex, int actionIndex) {
        String key = key(BATCH_JOB, jobId, MEDIA, mediaId, TRACK, taskIndex, actionIndex);
        return redisTemplate
                .boundListOps(key)
                .range(0, -1)
                .stream()
                .map(o -> jsonUtils.deserialize((byte[]) o, Track.class))
                .collect(toCollection(TreeSet::new));
    }



    @Override
    public void clearTracks(TransientJob job) {
        List<String> trackKeys = new ArrayList<>();
        int taskCount = job.getPipeline().getStages().size();
        for (TransientMedia media : job.getMedia()) {
            for (int taskIndex = 0; taskIndex < taskCount; taskIndex++) {
                int actionCount = job.getPipeline().getStages().get(taskIndex).getActions().size();
                for (int actionIndex = 0; actionIndex < actionCount; actionIndex++) {
                    trackKeys.add(key(BATCH_JOB, job.getId(), MEDIA, media.getId(), TRACK, taskIndex, actionIndex));
                }
            }
        }
        redisTemplate.delete(trackKeys);
    }

}
