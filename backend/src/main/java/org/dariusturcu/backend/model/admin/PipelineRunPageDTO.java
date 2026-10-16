package org.dariusturcu.backend.model.admin;

import java.util.List;
import java.util.Map;
import org.dariusturcu.backend.pipeline.RunQueueState;

public record PipelineRunPageDTO(List<PipelineRunDTO> items, int page, int size, long totalElements, int totalPages,
        Map<RunQueueState, Long> counts) {
}
