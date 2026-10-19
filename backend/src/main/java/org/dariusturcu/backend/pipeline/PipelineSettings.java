package org.dariusturcu.backend.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record PipelineSettings(long youtubeReserveUnits) {
    public static final long DEFAULT_YOUTUBE_RESERVE_UNITS = 1_500;

    public PipelineSettings(@Value("${pipeline.youtube.reserve-units:" + DEFAULT_YOUTUBE_RESERVE_UNITS + "}") long youtubeReserveUnits) {
        if (youtubeReserveUnits < 0) {
            throw new IllegalArgumentException("YouTube reserve cannot be negative");
        }
        this.youtubeReserveUnits = youtubeReserveUnits;
    }
}
