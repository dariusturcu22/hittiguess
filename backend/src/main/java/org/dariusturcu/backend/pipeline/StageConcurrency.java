package org.dariusturcu.backend.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * How many jobs of each metadata stage may be in progress at once, across every core process.
 * Each limit is read from {@code pipeline.stages.concurrency.<stage>}, the stage name in lower
 * case with hyphens. The AI service paces each external source itself, so a limit above one
 * only lets slow calls overlap; it never raises a source's request rate.
 */
@Component
public class StageConcurrency {
    public static final String PROPERTY_PREFIX = "pipeline.stages.concurrency.";
    public static final int DEFAULT_YOUTUBE_METADATA_LIMIT = 4;
    public static final int DEFAULT_LLM_RECONCILIATION_LIMIT = 3;
    public static final int DEFAULT_SOURCE_LIMIT = 2;
    public static final int EMBEDDING_LIMIT = 1;
    private static final int MINIMUM_LIMIT = 1;
    private static final Map<PipelineStage, Integer> DEFAULT_LIMITS = defaultLimits();

    private final Map<PipelineStage, Integer> limits;

    @Autowired
    public StageConcurrency(Environment environment) {
        this(readLimits(environment));
    }

    public StageConcurrency(Map<PipelineStage, Integer> overrides) {
        Map<PipelineStage, Integer> resolved = new EnumMap<>(DEFAULT_LIMITS);
        overrides.forEach((stage, limit) -> {
            if (limit < MINIMUM_LIMIT) {
                throw new IllegalArgumentException("Concurrency of " + stage + " must be at least " + MINIMUM_LIMIT);
            }
            resolved.put(stage, limit);
        });
        this.limits = Collections.unmodifiableMap(resolved);
    }

    public static StageConcurrency defaults() {
        return new StageConcurrency(Map.of());
    }

    public static StageConcurrency uniform(int limit) {
        Map<PipelineStage, Integer> uniformLimits = new EnumMap<>(PipelineStage.class);
        HttpAiStageClient.PATHS.keySet().forEach(stage -> uniformLimits.put(stage, limit));
        return new StageConcurrency(uniformLimits);
    }

    public int limit(PipelineStage stage) {
        return limits.getOrDefault(stage, EMBEDDING_LIMIT);
    }

    /** The sum of the metadata stage limits, the most stage calls one process runs at once. */
    public int totalMetadataLimit() {
        return HttpAiStageClient.PATHS.keySet().stream().mapToInt(this::limit).sum();
    }

    public static String propertyName(PipelineStage stage) {
        return PROPERTY_PREFIX + stage.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static Map<PipelineStage, Integer> readLimits(Environment environment) {
        Map<PipelineStage, Integer> configured = new EnumMap<>(PipelineStage.class);
        for (PipelineStage stage : HttpAiStageClient.PATHS.keySet()) {
            Integer limit = environment.getProperty(propertyName(stage), Integer.class);
            if (limit != null) {
                configured.put(stage, limit);
            }
        }
        return configured;
    }

    private static Map<PipelineStage, Integer> defaultLimits() {
        Map<PipelineStage, Integer> defaults = new EnumMap<>(PipelineStage.class);
        defaults.put(PipelineStage.YOUTUBE_METADATA, DEFAULT_YOUTUBE_METADATA_LIMIT);
        defaults.put(PipelineStage.MUSICBRAINZ, DEFAULT_SOURCE_LIMIT);
        defaults.put(PipelineStage.DISCOGS, DEFAULT_SOURCE_LIMIT);
        defaults.put(PipelineStage.WIKIDATA, DEFAULT_SOURCE_LIMIT);
        defaults.put(PipelineStage.WIKIPEDIA, DEFAULT_SOURCE_LIMIT);
        defaults.put(PipelineStage.LLM_RECONCILIATION, DEFAULT_LLM_RECONCILIATION_LIMIT);
        defaults.put(PipelineStage.EMBEDDING, EMBEDDING_LIMIT);
        return defaults;
    }
}
