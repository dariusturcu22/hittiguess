package org.dariusturcu.backend.model.admin;

import java.util.List;

public record ImportBatchPageDTO(List<ImportBatchDTO> items, int page, int size, long totalElements, int totalPages) {
}
