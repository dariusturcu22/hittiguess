package org.dariusturcu.backend.model.admin;

import java.util.List;

public record PipelineRunPageDTO(List<PipelineRunDTO> items, int page, int size, long totalElements, int totalPages) {
}
