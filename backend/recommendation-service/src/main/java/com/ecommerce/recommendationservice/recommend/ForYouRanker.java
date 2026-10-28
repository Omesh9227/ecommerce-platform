package com.ecommerce.recommendationservice.recommend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Blends a customer's signals into one ranking. Each signal adds to the product's score - bought together with
 * something they bought weighs most, then viewed together with something they viewed, then similar to it, then
 * popular in their categories - and the product keeps the reason of its strongest single signal. Weights are set so
 * that what other customers actually bought or viewed together always beats "same category" + "popular" combined
 * (at most 1.5 + 1.5 = 3 < 3.3).
 * Products they already bought or recently viewed are left out (those have their own lists).
 */
final class ForYouRanker {

    record Ranked(UUID productId, double score, String reason) {
    }

    private ForYouRanker() {
    }

    static double weight(Signal s) {
        return switch (s.kind()) {
            case BOUGHT_WITH -> 4 + 0.5 * Math.min(s.strength(), 10);   // 4.5 - 9
            case VIEWED_WITH -> 3 + 0.3 * Math.min(s.strength(), 10);   // 3.3 - 6
            case SIMILAR_TO -> 1 + 0.1 * Math.min(s.strength(), 5);     // 1 - 1.5 (strength = rating)
            case POPULAR_IN -> 0.5 + 0.05 * Math.min(s.strength(), 20); // 0.55 - 1.5 (strength = units sold)
        };
    }

    static String reason(Signal s) {
        return switch (s.kind()) {
            case BOUGHT_WITH -> "Because you bought " + s.sourceName();
            case VIEWED_WITH -> "Because you viewed " + s.sourceName();
            case SIMILAR_TO -> "Similar to " + s.sourceName();
            case POPULAR_IN -> "Popular in " + s.sourceName();
        };
    }

    static List<Ranked> rank(Collection<Signal> signals, Set<UUID> exclude, int limit) {
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, Signal> strongest = new LinkedHashMap<>();
        for (Signal s : signals) {
            if (exclude.contains(s.productId())) {
                continue;
            }
            double w = weight(s);
            scores.merge(s.productId(), w, Double::sum);
            strongest.merge(s.productId(), s, (a, b) -> weight(b) > weight(a) ? b : a);
        }
        List<Ranked> ranked = new ArrayList<>();
        strongest.forEach((id, s) -> ranked.add(new Ranked(id, scores.get(id), reason(s))));
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(r -> r.productId().toString()));
        return ranked.size() > limit ? ranked.subList(0, limit) : ranked;
    }
}
