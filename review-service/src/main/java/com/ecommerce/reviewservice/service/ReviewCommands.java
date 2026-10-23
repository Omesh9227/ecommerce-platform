package com.ecommerce.reviewservice.service;

import com.ecommerce.reviewservice.dto.request.ReviewRequest;
import com.ecommerce.reviewservice.dto.response.HelpfulVoteResponse;
import com.ecommerce.reviewservice.entity.Review;
import com.ecommerce.reviewservice.exception.BadRequestException;
import com.ecommerce.reviewservice.exception.ConflictException;
import com.ecommerce.reviewservice.exception.ResourceNotFoundException;
import com.ecommerce.reviewservice.repository.ReviewRepository;
import com.ecommerce.reviewservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Every change to reviews, each in one short transaction together with the product's rating recalculation
 * (and its event). Network calls happen before, in ReviewService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCommands {

    static final String ALREADY_REVIEWED = "You have already reviewed this product. Edit your review instead.";

    private final ReviewRepository reviewRepository;
    private final RatingService ratingService;
    private final Clock clock;

    @Transactional
    public Review create(UUID productId, AuthenticatedUser user, String reviewerName, ReviewRequest request,
                         boolean verifiedPurchase) {
        if (reviewRepository.findByProductIdAndUserId(productId, user.id()).isPresent()) {
            throw new ConflictException(ALREADY_REVIEWED);
        }
        Review review = reviewRepository.saveAndFlush(new Review(productId, user.id(), reviewerName,
                request.rating(), request.trimmedTitle(), request.trimmedComment(), verifiedPurchase, clock.instant()));
        ratingService.recalculate(productId);
        log.info("Review {} created: product {} by user {} ({} stars)", review.getId(), productId, user.id(),
                request.rating());
        return review;
    }

    /** Only the author can edit; for anyone else the review "doesn't exist" (404). */
    @Transactional
    public Review edit(UUID reviewId, AuthenticatedUser user, ReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getUserId().equals(user.id()))
                .orElseThrow(() -> notFound(reviewId));
        review.edit(request.rating(), request.trimmedTitle(), request.trimmedComment(), clock.instant());
        reviewRepository.flush();
        ratingService.recalculate(review.getProductId());
        return review;
    }

    /** The author or an admin. */
    @Transactional
    public void delete(UUID reviewId, AuthenticatedUser user) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getUserId().equals(user.id()) || user.isAdmin())
                .orElseThrow(() -> notFound(reviewId));
        reviewRepository.delete(review);
        reviewRepository.flush();
        ratingService.recalculate(review.getProductId());
        log.info("Review {} deleted by user {}", reviewId, user.id());
    }

    @Transactional
    public Review hide(UUID reviewId, String reason) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> notFound(reviewId));
        review.hide(reason.strip());
        reviewRepository.flush();
        ratingService.recalculate(review.getProductId());
        log.info("Review {} hidden: {}", reviewId, reason);
        return review;
    }

    @Transactional
    public Review unhide(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> notFound(reviewId));
        review.unhide();
        reviewRepository.flush();
        ratingService.recalculate(review.getProductId());
        return review;
    }

    /** "Helpful": once per customer, never on your own review. Voting twice changes nothing. */
    @Transactional
    public HelpfulVoteResponse voteHelpful(UUID reviewId, AuthenticatedUser user) {
        Review review = visibleReview(reviewId);
        if (review.getUserId().equals(user.id())) {
            throw new BadRequestException("You can't mark your own review as helpful");
        }
        if (reviewRepository.addVote(reviewId, user.id(), clock.instant()) == 1) {
            reviewRepository.changeHelpfulCount(reviewId, 1);
        }
        return new HelpfulVoteResponse(reviewId, reviewRepository.helpfulCount(reviewId), true);
    }

    @Transactional
    public HelpfulVoteResponse removeHelpfulVote(UUID reviewId, AuthenticatedUser user) {
        visibleReview(reviewId);
        if (reviewRepository.removeVote(reviewId, user.id()) == 1) {
            reviewRepository.changeHelpfulCount(reviewId, -1);
        }
        return new HelpfulVoteResponse(reviewId, reviewRepository.helpfulCount(reviewId), false);
    }

    private Review visibleReview(UUID reviewId) {
        return reviewRepository.findById(reviewId).filter(Review::isVisible).orElseThrow(() -> notFound(reviewId));
    }

    private static ResourceNotFoundException notFound(UUID reviewId) {
        return new ResourceNotFoundException("Review not found: " + reviewId);
    }
}
