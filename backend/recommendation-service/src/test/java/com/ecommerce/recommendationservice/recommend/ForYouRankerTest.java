package com.ecommerce.recommendationservice.recommend;

import com.ecommerce.recommendationservice.config.RecommendationProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForYouRankerTest {

    private final UUID pixel = UUID.randomUUID();
    private final UUID buds = UUID.randomUUID();
    private final UUID galaxy = UUID.randomUUID();
    private final UUID owned = UUID.randomUUID();

    @Test
    void signalsAddUp_andTheStrongestOneGivesTheReason() {
        List<Signal> signals = List.of(
                new Signal(buds, Signal.Kind.BOUGHT_WITH, pixel, "Pixel 9", 2),
                new Signal(galaxy, Signal.Kind.SIMILAR_TO, pixel, "Pixel 9", 4.5),
                new Signal(galaxy, Signal.Kind.POPULAR_IN, UUID.randomUUID(), "Phones", 10),
                new Signal(owned, Signal.Kind.BOUGHT_WITH, pixel, "Pixel 9", 50));

        List<ForYouRanker.Ranked> ranked = ForYouRanker.rank(signals, Set.of(owned), 10);

        assertThat(ranked).extracting(ForYouRanker.Ranked::productId).containsExactly(buds, galaxy);
        assertThat(ranked.get(0).reason()).isEqualTo("Because you bought Pixel 9");
        assertThat(ranked.get(1).reason()).isEqualTo("Similar to Pixel 9");             // 1.45 beats 1.0
        assertThat(ranked.get(1).score()).isEqualTo(1.45 + 1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void boughtTogetherWeighsMost_andCountsAreCapped() {
        assertThat(ForYouRanker.weight(new Signal(buds, Signal.Kind.BOUGHT_WITH, pixel, "x", 1)))
                .isGreaterThan(ForYouRanker.weight(new Signal(buds, Signal.Kind.VIEWED_WITH, pixel, "x", 1)));
        // One "viewed together" beats the best "similar" + "popular" a product can get
        assertThat(ForYouRanker.weight(new Signal(buds, Signal.Kind.VIEWED_WITH, pixel, "x", 1)))
                .isGreaterThan(ForYouRanker.weight(new Signal(buds, Signal.Kind.SIMILAR_TO, pixel, "x", 5))
                        + ForYouRanker.weight(new Signal(buds, Signal.Kind.POPULAR_IN, pixel, "x", 1000)));
        assertThat(ForYouRanker.weight(new Signal(buds, Signal.Kind.BOUGHT_WITH, pixel, "x", 1000)))
                .isEqualTo(ForYouRanker.weight(new Signal(buds, Signal.Kind.BOUGHT_WITH, pixel, "x", 10)));
        assertThat(ForYouRanker.reason(new Signal(buds, Signal.Kind.VIEWED_WITH, pixel, "Pixel 9", 1)))
                .isEqualTo("Because you viewed Pixel 9");
        assertThat(ForYouRanker.reason(new Signal(buds, Signal.Kind.POPULAR_IN, pixel, "Phones", 1))).isEqualTo("Popular in Phones");
    }

    @Test
    void limitIsRespected() {
        List<Signal> signals = List.of(new Signal(buds, Signal.Kind.SIMILAR_TO, pixel, "a", 1),
                new Signal(galaxy, Signal.Kind.SIMILAR_TO, pixel, "a", 2));
        assertThat(ForYouRanker.rank(signals, Set.of(), 1)).hasSize(1);
    }

    @Test
    void defaultLimitCantExceedMaxLimit() {
        assertThatThrownBy(() -> new RecommendationProperties("INR", 30, 20, 7, 20)).isInstanceOf(IllegalArgumentException.class);
    }
}
