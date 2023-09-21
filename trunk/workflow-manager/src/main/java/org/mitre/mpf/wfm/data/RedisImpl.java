/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.javasimon.aop.Monitored;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.util.JobPartsIter;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Monitored
@Component
public class RedisImpl implements Redis {

    private static final Logger log = LoggerFactory.getLogger(RedisImpl.class);

    @Autowired
    private RedisTemplate<String, byte[]> redisTemplate;

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
        BoundListOperations<String, byte[]> redisTracks = redisTemplate.boundListOps(key);
        for (Track track : tracks) {
            redisTracks.rightPush(jsonUtils.serialize(track));
        }
    }


    @Override
    public synchronized SortedSet<Track> getTracks(
            long jobId, long mediaId, int taskIndex, int actionIndex) {
        return getTracksStream(jobId, mediaId, taskIndex, actionIndex)
                .collect(toCollection(TreeSet::new));
    }


    public synchronized Stream<Track> getTracksStream(
            long jobId, long mediaId, int taskIndex, int actionIndex) {
        return getTrackListOps(jobId, mediaId, taskIndex, actionIndex)
                .range(0, -1)
                .stream()
                .map(o -> jsonUtils.deserialize(o, Track.class));
    }


    public synchronized int getTrackCount(
            long jobId, long mediaId, int taskIndex, int actionIndex) {
        Long size = getTrackListOps(jobId, mediaId, taskIndex, actionIndex).size();
        return size == null
                ? 0
                : size.intValue();
    }


    @Override
    public void clearTracks(BatchJob job) {
        var trackKeys = JobPartsIter.stream(job)
                .map(jp -> createTrackKey(
                        job.getId(), jp.getMedia().getId(), jp.getTaskIndex(),
                        jp.getActionIndex()))
                .toList();
        redisTemplate.delete(trackKeys);
    }


    private static String createTrackKey(Track track) {
        return createTrackKey(track.getJobId(), track.getMediaId(), track.getTaskIndex(), track.getActionIndex());

    }

    private static String createTrackKey(long jobId, long mediaId, int taskIndex, int actionIndex) {
        return String.format("BATCH_JOB:%s:MEDIA:%s:TASK:%s:ACTION:%s:TRACKS",
                             jobId, mediaId, taskIndex, actionIndex);
    }

    private BoundListOperations<String, byte[]> getTrackListOps(
            long jobId, long mediaId, int taskIndex, int actionIndex) {
        return redisTemplate.boundListOps(createTrackKey(jobId, mediaId, taskIndex, actionIndex));
    }
}
