package com.ecommerce.searchservice.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns text into lower-case words of letters and digits ("Pixel 8 Pro (128GB)" -> pixel, 8, pro, 128gb), for both
 * indexing and searching. Search queries become a PostgreSQL tsquery where every word must match, and every word
 * may be the start of a longer one: "pix 8" -> 'pix':* & '8':*. Only letters and digits reach the tsquery, so user
 * input can never break its syntax.
 */
public final class QueryText {

    public static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_WORDS = 8;
    private static final int MAX_WORD_LENGTH = 50;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private QueryText() {
    }

    /** All words, in order, duplicates kept. */
    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String word = m.group();
            words.add(word.length() > MAX_WORD_LENGTH ? word.substring(0, MAX_WORD_LENGTH) : word);
        }
        return words;
    }

    /** The words of a search query: at most {@value #MAX_WORDS}, each once. */
    public static List<String> queryWords(String query) {
        Set<String> unique = new LinkedHashSet<>(words(query));
        return unique.stream().limit(MAX_WORDS).toList();
    }

    /** Words worth putting in the spelling dictionary (2+ characters). */
    public static Set<String> dictionaryWords(String... texts) {
        Set<String> result = new LinkedHashSet<>();
        for (String text : texts) {
            for (String w : words(text)) {
                if (w.length() >= 2) {
                    result.add(w);
                }
            }
        }
        return result;
    }

    /** 'pixel':* & '8':* for to_tsquery('english', ...). Empty list -> null (no text condition). */
    public static String tsQuery(List<String> words) {
        if (words.isEmpty()) {
            return null;
        }
        return String.join(" & ", words.stream().map(w -> w + ":*").toList());
    }

    /** The words joined with single spaces: how a query is shown back and counted in popular searches. */
    public static String normalized(List<String> words) {
        return String.join(" ", words);
    }
}
