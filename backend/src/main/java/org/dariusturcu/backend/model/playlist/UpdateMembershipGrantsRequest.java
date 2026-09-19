package org.dariusturcu.backend.model.playlist;

public record UpdateMembershipGrantsRequest(
        Boolean canRead,
        Boolean canWrite,
        Boolean canDelete) {
}
