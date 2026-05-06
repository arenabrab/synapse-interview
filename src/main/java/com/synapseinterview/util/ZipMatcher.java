package com.synapseinterview.util;

import com.synapseinterview.model.ZipRange;

import java.util.ArrayList;
import java.util.List;

public final class ZipMatcher {

    private ZipMatcher() {}

    /**
     * Parses a raw service_zips CSV field into a list of ZipRange.
     * Supports discrete zips ("11410, 11419"), single ranges ("11232-11305"),
     * and multiple ranges ("2164-2213, 2143-2193").
     */
    public static List<ZipRange> parse(String rawServiceZips) {
        List<ZipRange> ranges = new ArrayList<>();
        for (String token : rawServiceZips.split(",")) {
            String trimmed = token.trim();
            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-", 2);
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                ranges.add(new ZipRange(min, max));
            } else {
                int zip = Integer.parseInt(trimmed);
                ranges.add(new ZipRange(zip, zip));
            }
        }
        return ranges;
    }

    /**
     * Returns true if customerZip (parsed as integer) falls within any of the ranges.
     * Integer comparison handles leading-zero-stripped CSV values correctly.
     */
    public static boolean matches(List<ZipRange> ranges, String customerZip) {
        int zipInt = Integer.parseInt(customerZip);
        return ranges.stream().anyMatch(r -> r.contains(zipInt));
    }
}
