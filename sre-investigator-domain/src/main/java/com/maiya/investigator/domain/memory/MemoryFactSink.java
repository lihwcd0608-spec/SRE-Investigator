package com.maiya.investigator.domain.memory;

import com.maiya.investigator.domain.conversation.ConversationThread;

public interface MemoryFactSink {
    void afterTurn(ConversationThread thread, String user, String assistant);

    static MemoryFactSink noop() {
        return (thread, user, assistant) -> {
        };
    }
}
