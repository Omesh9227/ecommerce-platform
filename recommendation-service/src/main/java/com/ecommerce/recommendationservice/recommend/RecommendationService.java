package com.ecommerce.recommendationservice.recommend;

import com.ecommerce.recommendationservice.config.RecommendationProperties;
import com.ecommerce.recommendationservice.dto.response.RecommendationList;
import com.ecommerce.recommendationservice.dto.response.RecommendationStatsResponse;
import com.ecommerce.recommendationservice.dto.response.RecommendedProduct;
import com.ecommerce.recommendationservice.exception.BadRequestException;
import com.ecommerce.recommendationservice.exception.ResourceNotFoundException;
import com.ecommerce.recommendationservice.recommend.RecommendationRepository.Counted;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The recommendation lists. Every list only contains products that can be bought right now (ACTIVE, in stock) and
 * says why each one is there. Lists that would come out short are filled with bestsellers, then top-rated products,
 * so a new shop or a new customer still sees something useful.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    /** How many of a customer's latest purchases and views personal recommendations start from. */
    static final int SEEDS = 10;

    private final RecommendationRepository repository;
    private final RecommendationProperties properties;
    private final Clock clock;

    public RecommendationList boughtTogether(UUID productId, Integer limit) {
        List<RecommendedProduct> products = repository.boughtTogether(productId, limit(limit)).stream()
                .map(c -> c.product().withReason("Bought together in " + plural(c.count(), "order")))
                .toList();
        return new RecommendationList("Frequently bought together", products);
    }

    public RecommendationList alsoViewed(UUID productId, Integer limit) {
        List<RecommendedProduct> products = repository.alsoViewed(productId, limit(limit)).stream()
                .map(c -> c.product().withReason("Viewed by " + plural(c.count(), "customer") + " who viewed this"))
                .toList();
        return new RecommendationList("Customers who viewed this also viewed", products);
    }

    public RecommendationList similar(UUID productId, Integer limit) {
        RecommendationRepository.Base base = repository.base(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        List<RecommendedProduct> products = repository.similar(base, limit(limit)).stream()
                .map(c -> c.product().withReason(similarReason(base, c.product())))
                .toList();
        return new RecommendationList("Similar products", products);
    }

    public RecommendationList trending(UUID categoryId, Integer limit) {
        int n = limit(limit);
        String title = "Trending now";
        if (categoryId != null) {
            title = "Trending in " + repository.categoryName(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));
        }
        List<RecommendedProduct> products = new ArrayList<>(repository.bestsellers(categoryId, since(), Set.of(), n).stream()
                .map(c -> c.product().withReason(plural(c.count(), "sold") + " in the last " + properties.trendingDays() + " days"))
                .toList());
        fillWithTopRated(products, categoryId, n);
        return new RecommendationList(title, products);
    }

    /**
     * From the customer's latest purchases and views: what others bought with them, what others viewed with them,
     * similar products and bestsellers in the same categories. New customers get bestsellers and top-rated products.
     */
    public RecommendationList forYou(UUID userId, Integer limit) {
        int n = limit(limit);
        Map<UUID, String> bought = repository.purchased(userId, SEEDS);
        Map<UUID, String> viewed = repository.viewed(userId, properties.viewHistory());
        Map<UUID, String> recentViews = first(viewed, SEEDS);

        List<Signal> signals = new ArrayList<>();
        signals.addAll(repository.boughtWith(bought));
        signals.addAll(repository.viewedWith(recentViews));
        Map<UUID, String> seeds = new LinkedHashMap<>(bought);
        recentViews.forEach(seeds::putIfAbsent);
        signals.addAll(repository.similarTo(first(seeds, SEEDS)));
        signals.addAll(repository.popularIn(first(seeds, SEEDS), since()));

        Set<UUID> exclude = new HashSet<>(repository.purchased(userId, Integer.MAX_VALUE).keySet());
        exclude.addAll(viewed.keySet());
        List<ForYouRanker.Ranked> ranked = ForYouRanker.rank(signals, exclude, n);

        Map<UUID, String> reasons = new HashMap<>();
        ranked.forEach(r -> reasons.put(r.productId(), r.reason()));
        List<RecommendedProduct> products = new ArrayList<>(repository.cards(ranked.stream().map(ForYouRanker.Ranked::productId).toList())
                .stream().map(p -> p.withReason(reasons.get(p.productId()))).toList());

        if (products.size() < n) {
            Set<UUID> taken = new HashSet<>(exclude);
            products.forEach(p -> taken.add(p.productId()));
            for (Counted c : repository.bestsellers(null, since(), taken, n - products.size())) {
                products.add(c.product().withReason("Bestseller"));
                taken.add(c.product().productId());
            }
            if (products.size() < n) {
                repository.topRated(null, taken, n - products.size())
                        .forEach(c -> products.add(c.product().withReason("Top rated")));
            }
        }
        return new RecommendationList("Recommended for you", products);
    }

    public RecommendationList recentlyViewed(UUID userId, Integer limit) {
        return new RecommendationList("Recently viewed", repository.recentlyViewed(userId, Math.min(limit(limit), properties.viewHistory())));
    }

    public RecommendationStatsResponse stats() {
        return repository.stats();
    }

    // ---------- Helpers ----------

    private void fillWithTopRated(List<RecommendedProduct> products, UUID categoryId, int n) {
        if (products.size() < n) {
            Set<UUID> taken = new HashSet<>();
            products.forEach(p -> taken.add(p.productId()));
            repository.topRated(categoryId, taken, n - products.size())
                    .forEach(c -> products.add(c.product().withReason("Top rated")));
        }
    }

    private static String similarReason(RecommendationRepository.Base base, RecommendedProduct p) {
        boolean sameCategory = base.categoryId() != null && base.categoryId().equals(p.categoryId());
        boolean sameBrand = base.brand() != null && base.brand().equalsIgnoreCase(p.brand());
        String where = sameCategory ? "Also in " + p.categoryName() : "Related: " + p.categoryName();
        return sameBrand ? where + ", also by " + p.brand() : where;
    }

    private int limit(Integer requested) {
        int n = requested == null ? properties.defaultLimit() : requested;
        if (n < 1 || n > properties.maxLimit()) {
            throw new BadRequestException("limit must be between 1 and " + properties.maxLimit());
        }
        return n;
    }

    private LocalDate since() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(properties.trendingDays() - 1L);
    }

    private static Map<UUID, String> first(Map<UUID, String> map, int n) {
        Map<UUID, String> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> e : map.entrySet()) {
            if (result.size() == n) {
                break;
            }
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    private static String plural(long count, String word) {
        if (word.equals("sold")) {
            return count + " sold";
        }
        return count + " " + word + (count == 1 ? "" : "s");
    }
}
