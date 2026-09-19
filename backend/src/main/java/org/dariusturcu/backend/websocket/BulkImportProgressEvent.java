package org.dariusturcu.backend.websocket;

// Published by BulkImportService for every video id its on-the-spot import loop
// processes, so the submitting user's own client can render live per-song progress
// (see docs/design/source/ImportPlaylistProcessingDark.dc.html). Targeted at the
// submitting user alone: a bulk import belongs to one user, not a shared group or
// session, so this does not follow the per-group/per-session broadcast pattern
// GroupBroadcastEvent and SessionBroadcastEvent use. username is the STOMP
// Principal name (UserPrincipal#getUsername) BulkImportProgressListener targets
// with SimpMessagingTemplate#convertAndSendToUser.
public record BulkImportProgressEvent(String username, String importJobId, String youtubeId,
                                      BulkImportProgressOutcome outcome) {
}
