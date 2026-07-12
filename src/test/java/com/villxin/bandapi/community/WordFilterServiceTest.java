package com.villxin.bandapi.community;

import com.villxin.bandapi.community.service.WordFilterService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WordFilterServiceTest {

    private final WordFilterService filter = new WordFilterService("damn, hell,crud");

    @Test
    void starsOutBlockedWordsKeepingFirstLetter() {
        assertEquals("d*** it", filter.filter("damn it"));
        assertEquals("go to h***", filter.filter("go to hell"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals("D*** it to H***", filter.filter("DAMN it to HELL"));
    }

    @Test
    void matchesWholeWordsOnly() {
        // "hello" contains "hell" but is not a blocked word
        assertEquals("hello world", filter.filter("hello world"));
        assertEquals("shellfish", filter.filter("shellfish"));
    }

    @Test
    void filtersMultipleOccurrences() {
        assertEquals("d*** d*** d***", filter.filter("damn damn damn"));
    }

    @Test
    void handlesNullAndEmptyBlocklist() {
        assertNull(filter.filter(null));
        WordFilterService noop = new WordFilterService("");
        assertEquals("damn", noop.filter("damn"));
    }
}
