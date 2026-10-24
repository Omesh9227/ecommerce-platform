package com.ecommerce.searchservice.search;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "Did you mean ...?" for queries that found nothing. Each word that isn't in the dictionary (all words of product
 * names, brands and categories) and isn't the start of one is replaced by the closest dictionary word:
 * candidates come from trigram similarity (pg_trgm, fast with its GIN index), and the winner is the one with the
 * fewest typing mistakes (edit distance, where swapping two neighbouring letters counts as one: pixle -> pixel).
 * Up to 1 mistake for words of 4 letters or fewer, 2 for longer ones. Numbers are never changed.
 */
@Component
@RequiredArgsConstructor
public class SpellingCorrector {

    static final double CANDIDATE_SIMILARITY = 0.2;
    private static final int CANDIDATES = 20;

    private final NamedParameterJdbcTemplate jdbc;

    /** @return the corrected words, if at least one word changed */
    @Transactional(readOnly = true)
    public Optional<List<String>> correct(List<String> words) {
        // Lowers pg_trgm's match threshold for this transaction only, so "%" finds candidates with typos
        jdbc.queryForObject("SELECT set_config('pg_trgm.similarity_threshold', :t, true)",
                new MapSqlParameterSource("t", String.valueOf(CANDIDATE_SIMILARITY)), String.class);
        List<String> corrected = new ArrayList<>(words.size());
        boolean changed = false;
        for (String word : words) {
            String replacement = word;
            if (word.length() >= 3 && !word.chars().allMatch(Character::isDigit) && !isKnown(word)) {
                replacement = closest(word).orElse(word);
            }
            changed |= !replacement.equals(word);
            corrected.add(replacement);
        }
        return changed ? Optional.of(corrected) : Optional.empty();
    }

    private boolean isKnown(String word) {
        Boolean known = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM search_words WHERE word LIKE :prefix)",
                new MapSqlParameterSource("prefix", word + "%"), Boolean.class);
        return Boolean.TRUE.equals(known);
    }

    private Optional<String> closest(String word) {
        List<String> candidates = jdbc.queryForList("""
                SELECT word FROM search_words WHERE word % :word
                ORDER BY similarity(word, :word) DESC, word LIMIT :limit
                """, new MapSqlParameterSource("word", word).addValue("limit", CANDIDATES), String.class);
        int allowed = word.length() <= 4 ? 1 : 2;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = editDistance(word, candidate);
            if (distance <= allowed && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Optimal string alignment distance: insert, delete, replace or swap two neighbouring letters = 1 each. */
    static int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
