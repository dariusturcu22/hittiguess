package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.usage.ApiUsageRecord;
import org.dariusturcu.backend.model.usage.UsageProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * Estimates the cost of recorded token usage from the providers' published per-token prices.
 * The cost is computed when usage is read, never stored, so correcting a price here also
 * corrects every past figure. An operation without a known price costs nothing.
 */
@Component
public class ApiUsagePricing {

    public static final LocalDate PRICES_VERIFIED_ON = LocalDate.of(2026, 10, 16);

    public static final String CHAT_COMPLETION_OPERATION = "chat_completion";
    public static final String EMBEDDINGS_OPERATION = "embeddings";

    // https://deepinfra.com/deepseek-ai/DeepSeek-V4-Flash, standard tier.
    public static final BigDecimal DEEPINFRA_DEEPSEEK_V4_FLASH_INPUT_USD_PER_MILLION_TOKENS = new BigDecimal("0.09");
    public static final BigDecimal DEEPINFRA_DEEPSEEK_V4_FLASH_OUTPUT_USD_PER_MILLION_TOKENS = new BigDecimal("0.18");
    // https://developers.openai.com/api/docs/pricing, standard tier.
    public static final BigDecimal OPENAI_GPT_5_NANO_INPUT_USD_PER_MILLION_TOKENS = new BigDecimal("0.05");
    public static final BigDecimal OPENAI_GPT_5_NANO_OUTPUT_USD_PER_MILLION_TOKENS = new BigDecimal("0.40");
    public static final BigDecimal OPENAI_TEXT_EMBEDDING_3_SMALL_USD_PER_MILLION_TOKENS = new BigDecimal("0.02");

    public static final int COST_SCALE = 6;
    public static final RoundingMode COST_ROUNDING = RoundingMode.HALF_UP;

    private static final int TOKENS_PER_PRICE_UNIT_EXPONENT = 6;

    private record TokenPrices(BigDecimal inputPerMillionTokens, BigDecimal outputPerMillionTokens) {
    }

    private record PricedOperation(UsageProvider provider, String operation) {
    }

    private static final Map<PricedOperation, TokenPrices> PRICES = Map.of(
            new PricedOperation(UsageProvider.DEEPINFRA, CHAT_COMPLETION_OPERATION),
            new TokenPrices(DEEPINFRA_DEEPSEEK_V4_FLASH_INPUT_USD_PER_MILLION_TOKENS,
                    DEEPINFRA_DEEPSEEK_V4_FLASH_OUTPUT_USD_PER_MILLION_TOKENS),
            new PricedOperation(UsageProvider.OPENAI, CHAT_COMPLETION_OPERATION),
            new TokenPrices(OPENAI_GPT_5_NANO_INPUT_USD_PER_MILLION_TOKENS,
                    OPENAI_GPT_5_NANO_OUTPUT_USD_PER_MILLION_TOKENS),
            new PricedOperation(UsageProvider.OPENAI, EMBEDDINGS_OPERATION),
            new TokenPrices(OPENAI_TEXT_EMBEDDING_3_SMALL_USD_PER_MILLION_TOKENS, BigDecimal.ZERO));

    /** The exact, unrounded cost of one operation's usage. */
    public BigDecimal estimatedCostUsd(ApiUsageRecord usage) {
        TokenPrices prices = PRICES.get(new PricedOperation(usage.provider(), usage.operation()));
        if (prices == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal inputCost = BigDecimal.valueOf(usage.inputTokens()).multiply(prices.inputPerMillionTokens());
        BigDecimal outputCost = BigDecimal.valueOf(usage.outputTokens()).multiply(prices.outputPerMillionTokens());
        return inputCost.add(outputCost).movePointLeft(TOKENS_PER_PRICE_UNIT_EXPONENT);
    }

    public static BigDecimal roundCost(BigDecimal exactCostUsd) {
        return exactCostUsd.setScale(COST_SCALE, COST_ROUNDING);
    }
}
