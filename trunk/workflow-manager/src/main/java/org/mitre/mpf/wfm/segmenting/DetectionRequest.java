package org.mitre.mpf.wfm.segmenting;

import java.util.Optional;

import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.data.entities.transients.Track;

public record DetectionRequest(
        DetectionProtobuf.DetectionRequest protobuf, Optional<Track> feedForwardTrack) {

    public DetectionRequest(DetectionProtobuf.DetectionRequest protobuf) {
        this(protobuf, Optional.empty());
    }

    public DetectionRequest(
            DetectionProtobuf.DetectionRequest protobuf, Track feedForwardTrack) {
        this(protobuf, Optional.of(feedForwardTrack));
    }
}
