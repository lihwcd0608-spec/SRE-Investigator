package com.maiya.investigator.api;

public record ConversationAttachment(
        String name,
        String mediaType,
        String text,
        String dataBase64
) {
    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    public boolean hasBinary() {
        return dataBase64 != null && !dataBase64.isBlank();
    }
}
