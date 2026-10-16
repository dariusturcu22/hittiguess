package org.dariusturcu.backend.model.usage;

/**
 * An external API whose usage the AI service records in api_usage_daily. The names match the
 * provider column values the AI service writes.
 */
public enum UsageProvider {
    YOUTUBE,
    DEEPINFRA,
    OPENAI
}
