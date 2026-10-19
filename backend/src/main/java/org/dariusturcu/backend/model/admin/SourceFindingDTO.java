package org.dariusturcu.backend.model.admin;

import org.dariusturcu.backend.pipeline.PipelineStage;
import org.dariusturcu.backend.pipeline.PipelineJobStatus;
import java.util.Map;

public record SourceFindingDTO(PipelineStage stage, PipelineJobStatus status, Map<String, Object> result, String failureReason) {
}
