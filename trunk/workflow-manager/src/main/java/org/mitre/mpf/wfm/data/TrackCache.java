package org.mitre.mpf.wfm.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.mitre.mpf.wfm.data.entities.transients.Track;

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
