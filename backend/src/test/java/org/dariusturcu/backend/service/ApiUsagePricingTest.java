package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.usage.ApiUsageRecord;
import org.dariusturcu.backend.model.usage.UsageProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ApiUsagePricingTest {

    private static final long ONE_MILLION_TOKENS = 1_000_000;
    private static final long HALF_A_MILLION_TOKENS = 500_000;
    private static final long NO_TOKENS = 0;
    private static final long ONE_CALL = 1;

    private final ApiUsagePricing pricing = new ApiUsagePricing();

    private static ApiUsageRecord usage(UsageProvider provider, String operation, long inputTokens, long outputTokens) {
        return new ApiUsageRecord(provider, operation, ONE_CALL, NO_TOKENS, inputTokens, outputTokens);
    }

    @Test
    void deepInfraChatCostsTheInputAndOutputPricePerMillionTokens() {
        BigDecimal cost = pricing.estimatedCostUsd(usage(UsageProvider.DEEPINFRA,
                ApiUsagePricing.CHAT_COMPLETION_OPERATION, ONE_MILLION_TOKENS, HALF_A_MILLION_TOKENS));

        assertThat(cost).isEqualByComparingTo("0.18");
    }

    @Test
    void openAiChatAndEmbeddingsUseTheirOwnPrices() {
        BigDecimal chatCost = pricing.estimatedCostUsd(usage(UsageProvider.OPENAI,
                ApiUsagePricing.CHAT_COMPLETION_OPERATION, ONE_MILLION_TOKENS, ONE_MILLION_TOKENS));
        BigDecimal embeddingCost = pricing.estimatedCostUsd(usage(UsageProvider.OPENAI,
                ApiUsagePricing.EMBEDDINGS_OPERATION, ONE_MILLION_TOKENS, NO_TOKENS));

        assertThat(chatCost).isEqualByComparingTo("0.45");
        assertThat(embeddingCost).isEqualByComparingTo("0.02");
    }

    @Test
    void theExactCostKeepsFractionsOfAMicroDollar() {
        BigDecimal cost = pricing.estimatedCostUsd(usage(UsageProvider.DEEPINFRA,
                ApiUsagePricing.CHAT_COMPLETION_OPERATION, 1, NO_TOKENS));

        assertThat(cost).isEqualByComparingTo("0.00000009");
    }

    @Test
    void roundingUsesSixDecimalPlacesAndRoundsHalfUp() {
        assertThat(ApiUsagePricing.roundCost(new BigDecimal("0.0000025"))).isEqualTo(new BigDecimal("0.000003"));
        assertThat(ApiUsagePricing.roundCost(new BigDecimal("0.0000024"))).isEqualTo(new BigDecimal("0.000002"));
        assertThat(ApiUsagePricing.roundCost(BigDecimal.ZERO)).isEqualTo(new BigDecimal("0.000000"));
    }

    @Test
    void anOperationWithoutAKnownPriceCostsNothing() {
        BigDecimal youtubeCost = pricing.estimatedCostUsd(usage(UsageProvider.YOUTUBE, "videos.list",
                ONE_MILLION_TOKENS, ONE_MILLION_TOKENS));
        BigDecimal unknownOperationCost = pricing.estimatedCostUsd(usage(UsageProvider.DEEPINFRA, "image_generation",
                ONE_MILLION_TOKENS, ONE_MILLION_TOKENS));

        assertThat(youtubeCost).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(unknownOperationCost).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
