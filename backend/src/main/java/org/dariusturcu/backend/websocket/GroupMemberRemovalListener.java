package org.dariusturcu.backend.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GroupMemberRemovalListener {

    private final UserSocketCloser userSocketCloser;

    @TransactionalEventListener(fallbackExecution = true)
    public void onMemberRemoved(GroupMemberRemovedEvent event) {
        userSocketCloser.closeSocketsOf(event.removedPrincipalName());
    }
}
