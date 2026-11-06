package org.dariusturcu.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SecurityConfig permits every request that arrives on the management port. If that port
 * were the application port, the permit rule would open every endpoint, so startup fails
 * unless the two ports differ. A management port that is unset or negative adds no such
 * rule, and port 0 asks for a random free port, which cannot collide with the application.
 */
@Component
public class ManagementPortStartupGuard {

    private static final String SAME_PORT_MESSAGE =
            "management.server.port must differ from server.port: the management port bypasses authentication.";
    private static final String UNPARSEABLE_PORT_MESSAGE =
            "management.server.port must be a number: ";

    public ManagementPortStartupGuard(
            @Value("${server.port:8080}") int applicationPort,
            @Value("${management.server.port:8081}") String managementPort
    ) {
        verifyPortsDiffer(applicationPort, managementPort);
    }

    private static void verifyPortsDiffer(int applicationPort, String managementPort) {
        if (managementPort == null || managementPort.isBlank()) {
            return;
        }
        int parsedManagementPort;
        try {
            parsedManagementPort = Integer.parseInt(managementPort.trim());
        } catch (NumberFormatException unparseablePort) {
            throw new IllegalStateException(UNPARSEABLE_PORT_MESSAGE + managementPort, unparseablePort);
        }
        if (parsedManagementPort > 0 && parsedManagementPort == applicationPort) {
            throw new IllegalStateException(SAME_PORT_MESSAGE);
        }
    }
}
