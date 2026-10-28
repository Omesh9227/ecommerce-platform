package com.ecommerce.reviewservice.dto.response;

import java.util.UUID;

/** After voting or un-voting "helpful". */
public record HelpfulVoteResponse(UUID reviewId, int helpfulCount, boolean votedByMe) {
}
