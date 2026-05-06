package com.synapseinterview.util;

import com.synapseinterview.model.ZipRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZipMatcherTest {

    @Test
    void parseExactZip() {
        List<ZipRange> ranges = ZipMatcher.parse("10014");
        assertEquals(1, ranges.size());
        assertEquals(new ZipRange(10014, 10014), ranges.get(0));
    }

    @Test
    void parseRange() {
        List<ZipRange> ranges = ZipMatcher.parse("11232-11305");
        assertEquals(1, ranges.size());
        assertEquals(new ZipRange(11232, 11305), ranges.get(0));
    }

    @Test
    void parseMultipleExact() {
        List<ZipRange> ranges = ZipMatcher.parse("11410, 11419, 11438");
        assertEquals(3, ranges.size());
        assertEquals(new ZipRange(11410, 11410), ranges.get(0));
        assertEquals(new ZipRange(11419, 11419), ranges.get(1));
        assertEquals(new ZipRange(11438, 11438), ranges.get(2));
    }

    @Test
    void parseMultipleRanges() {
        List<ZipRange> ranges = ZipMatcher.parse("2164-2213, 2143-2193");
        assertEquals(2, ranges.size());
        assertEquals(new ZipRange(2164, 2213), ranges.get(0));
        assertEquals(new ZipRange(2143, 2193), ranges.get(1));
    }

    @Test
    void matchesExactZip() {
        List<ZipRange> ranges = ZipMatcher.parse("11410, 11419, 11438");
        assertTrue(ZipMatcher.matches(ranges, "11419"));
        assertFalse(ZipMatcher.matches(ranges, "11420"));
    }

    @Test
    void matchesRange() {
        List<ZipRange> ranges = ZipMatcher.parse("11232-11305");
        assertTrue(ZipMatcher.matches(ranges, "11250"));
        assertFalse(ZipMatcher.matches(ranges, "11200"));
        assertFalse(ZipMatcher.matches(ranges, "11400"));
    }

    @Test
    void matchesNationalRange() {
        List<ZipRange> ranges = ZipMatcher.parse("00100-99999");
        assertTrue(ZipMatcher.matches(ranges, "10015"));
        assertTrue(ZipMatcher.matches(ranges, "77059"));
        assertTrue(ZipMatcher.matches(ranges, "02130"));
    }

    @Test
    void leadingZeroZipMatchesStrippedRange() {
        // CSV stores "2164-2213" but real zips are 02164-02213.
        // Numeric comparison makes "02164" == 2164.
        List<ZipRange> ranges = ZipMatcher.parse("2164-2213");
        assertTrue(ZipMatcher.matches(ranges, "02164"));
        assertTrue(ZipMatcher.matches(ranges, "02200"));
        assertFalse(ZipMatcher.matches(ranges, "02130")); // 2130 < 2164
    }
}
