package org.dariusturcu.backend.pipeline;

import tools.jackson.databind.JsonNode;

public interface AiStageClient {
    JsonNode execute(PipelineStage stage, JsonNode request);
}
