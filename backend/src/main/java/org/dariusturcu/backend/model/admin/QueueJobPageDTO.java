package org.dariusturcu.backend.model.admin;

import java.util.List;

public record QueueJobPageDTO(List<QueueJobDTO> items, int page, int size, long totalElements, int totalPages) {
}
