package com.nevis.search.search;

import java.util.ArrayList;
import java.util.List;

// Splits a document into passages short enough to embed well: the title, then one passage per paragraph,
// with long paragraphs cut at sentence ends.
final class Passages {

    static final int MAX_CHARS = 800;

    private Passages() {
    }

    static List<String> of(String title, String content) {
        List<String> passages = new ArrayList<>();
        passages.add(title);
        for (String paragraph : content.split("\\R\\s*\\R")) {
            if (!paragraph.isBlank()) {
                passages.addAll(split(paragraph.strip().replaceAll("\\s+", " ")));
            }
        }
        return passages;
    }

    private static List<String> split(String paragraph) {
        if (paragraph.length() <= MAX_CHARS) {
            return List.of(paragraph);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            if (current.length() > 0 && current.length() + 1 + sentence.length() > MAX_CHARS) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(sentence);
            while (current.length() > MAX_CHARS) { // a single sentence longer than the limit
                parts.add(current.substring(0, MAX_CHARS));
                current.delete(0, MAX_CHARS);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }
}
