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

package org.mitre.mpf.wfm.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.util.JobPart;

public class TrackCache {

    private final long _jobId;

    private final int _taskIndex;

    private final InProgressBatchJobsService _inProgressJobs;

    private record CacheKey(long mediaId, int actionIndex) {}

    private final Map<CacheKey, SortedSet<Track>> _cache = new HashMap<>();

    private final List<CacheKey> _updatedKeys = new ArrayList<>();

    public TrackCache(long jobId, int taskIndex, InProgressBatchJobsService inProgressJobs) {
        _jobId = jobId;
        _taskIndex = taskIndex;
        _inProgressJobs = inProgressJobs;
    }

    public long getJobId() {
        return _jobId;
    }

    public int getTaskIndex() {
        return _taskIndex;
    }


    public synchronized SortedSet<Track> getTracks(long mediaId, int actionIndex) {
        return _cache.computeIfAbsent(
                new CacheKey(mediaId, actionIndex),
                k -> _inProgressJobs.getTracks(_jobId, mediaId, _taskIndex, actionIndex));
    }

    public SortedSet<Track> getTracks(JobPart jobPart) {
        return getTracks(jobPart.media().getId(), jobPart.actionIndex());
    }


    public synchronized void updateTracks(long mediaId, int actionIndex, SortedSet<Track> tracks) {
        var key = new CacheKey(mediaId, actionIndex);
        _cache.put(key, tracks);
        _updatedKeys.add(key);
    }


    public synchronized void commit() {
        for (var key : _updatedKeys) {
            _inProgressJobs.setTracks(
                    _jobId, key.mediaId, _taskIndex, key.actionIndex, _cache.get(key));
        }
    }
}
