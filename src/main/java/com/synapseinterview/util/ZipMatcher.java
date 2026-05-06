package com.synapseinterview.util;

import com.synapseinterview.model.ZipRange;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.List;

@UtilityClass
public class ZipMatcher {

    /**
     * Parses a raw service_zips CSV field into a list of ZipRange.
     * Supports discrete zips ("11410, 11419"), single ranges ("11232-11305"),
     * and multiple ranges ("2164-2213, 2143-2193").
     */
    public List<ZipRange> parse(String rawServiceZips) {
        return Arrays.stream(rawServiceZips.split(","))
                .map(String::trim)
                .map(ZipMatcher::parseToken)
                .toList();
    }

    private ZipRange parseToken(String token) {
        if (token.contains("-")) {
            var parts = token.split("-", 2);
            var min = Integer.parseInt(parts[0].trim());
            var max = Integer.parseInt(parts[1].trim());
            return new ZipRange(min, max);
        }
        var zip = Integer.parseInt(token);
        return new ZipRange(zip, zip);
    }

    /**
     * Returns true if customerZip (parsed as integer) falls within any of the ranges.
     * Integer comparison handles leading-zero-stripped CSV values correctly.
     */
    public boolean matches(List<ZipRange> ranges, String customerZip) {
        var zipInt = Integer.parseInt(customerZip);
        return ranges.stream().anyMatch(r -> r.contains(zipInt));
    }
}