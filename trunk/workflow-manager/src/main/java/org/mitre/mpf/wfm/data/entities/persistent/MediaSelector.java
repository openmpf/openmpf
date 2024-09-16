package org.mitre.mpf.wfm.data.entities.persistent;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import org.mitre.mpf.interop.util.CompareUtils;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.util.Utils;

public record MediaSelector(
        String expression,
        MediaSelectorType type,
        Map<String, String> selectionProperties,
        String resultDetectionProperty,
        UUID id) {

    public MediaSelector {
        selectionProperties = Utils.toImmutableMap(selectionProperties);
    }

    public MediaSelector(
            String expression,
            MediaSelectorType type,
            Map<String, String> selectionProperties,
            String resultDetectionProperty) {
        this(expression, type, selectionProperties, resultDetectionProperty, UUID.randomUUID());
    }


    private static final Comparator<MediaSelector> DEFAULT_COMPARATOR = Comparator
            .nullsFirst(Comparator
                .comparing(MediaSelector::expression)
                .thenComparing(MediaSelector::type)
                .thenComparing(MediaSelector::resultDetectionProperty)
                .thenComparing(MediaSelector::selectionProperties, CompareUtils.MAP_COMPARATOR));

    public static Comparator<MediaSelector> comparator() {
        // We return a Comparator here instead of making the class implement Comparable because
        // when implementing Comparable, you generally need to also override .equals() amd
        // .hashCode() to make them consistent with .compareTo().
        return DEFAULT_COMPARATOR;
    }
}
