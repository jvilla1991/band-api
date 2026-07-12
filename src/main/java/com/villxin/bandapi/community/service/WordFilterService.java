package com.villxin.bandapi.community.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Auto word-filter (the only moderation at launch). The blocklist is a
 * comma-separated property ({@code community.word-filter.blocklist}) — a
 * property was chosen over a DB table for simplicity at dozens-of-users scale.
 * Matched words are starred out keeping the first letter ("damn" -> "d***").
 * The FILTERED text is what gets stored (filter-at-write, chosen for
 * consistency: every read path sees the same clean text).
 */
@Service
public class WordFilterService {

    private final Pattern pattern; // null when the blocklist is empty

    public WordFilterService(@Value("${community.word-filter.blocklist:}") String blocklist) {
        List<String> words = Arrays.stream(blocklist.split(","))
                .map(String::trim)
                .filter(w -> !w.isEmpty())
                .toList();
        if (words.isEmpty()) {
            this.pattern = null;
        } else {
            String alternation = words.stream()
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|"));
            this.pattern = Pattern.compile("\\b(" + alternation + ")\\b", Pattern.CASE_INSENSITIVE);
        }
    }

    /** Stars out blocklisted words: first letter kept, rest replaced with '*'. */
    public String filter(String text) {
        if (text == null || pattern == null) return text;
        return pattern.matcher(text).replaceAll(m -> {
            String word = m.group();
            return word.charAt(0) + "*".repeat(word.length() - 1);
        });
    }
}
