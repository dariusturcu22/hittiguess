package org.dariusturcu.backend.model.usage;

/**
 * One token-billed provider's usage for the current UTC day and the current UTC calendar month.
 */
public record ProviderUsageDTO(
        UsageProvider provider,
        TokenUsageDTO today,
        TokenUsageDTO month) {
}
