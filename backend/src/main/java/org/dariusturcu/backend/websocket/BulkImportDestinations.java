package org.dariusturcu.backend.websocket;

// Naming convention for the per-user bulk-import progress destination (story 40). A
// client subscribes to "/user" plus this queue, and BulkImportProgressListener sends
// through SimpMessagingTemplate#convertAndSendToUser with this same queue name; Spring
// resolves both to the same user-specific destination, the standard STOMP
// per-user-messaging convention. See GroupDestinations and SessionDestinations for the
// parallel per-group and per-session naming conventions.
public final class BulkImportDestinations {

    private static final String BULK_IMPORT_PROGRESS_QUEUE = "/queue/bulk-import-progress";

    private BulkImportDestinations() {
    }

    public static String progressQueue() {
        return BULK_IMPORT_PROGRESS_QUEUE;
    }
}
