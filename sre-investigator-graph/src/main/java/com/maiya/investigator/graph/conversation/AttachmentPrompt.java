package com.maiya.investigator.graph.conversation;

import com.maiya.investigator.api.ConversationAttachment;
import java.util.List;

public final class AttachmentPrompt {
    private static final int MAX_TEXT_CHARS = 80_000;

    private AttachmentPrompt() {
    }

    public static String append(String question, List<ConversationAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return question;
        }
        StringBuilder builder = new StringBuilder(question == null ? "" : question);
        builder.append("\n\n## 用户附件\n");
        for (ConversationAttachment attachment : attachments) {
            String name = attachment.name() == null || attachment.name().isBlank() ? "未命名" : attachment.name();
            String type = attachment.mediaType() == null ? "application/octet-stream" : attachment.mediaType();
            builder.append("### ").append(name).append(" (").append(type).append(")\n");
            if (attachment.hasText()) {
                String text = attachment.text();
                if (text.length() > MAX_TEXT_CHARS) {
                    builder.append(text, 0, MAX_TEXT_CHARS).append("\n…(已截断)\n");
                } else {
                    builder.append(text).append('\n');
                }
            } else if (attachment.hasBinary() && type.startsWith("image/")) {
                builder.append("用户粘贴了一张截图。请结合问题文字阅读图中的报错、状态、用户 ID、接口路径或配置项。\n");
            } else {
                builder.append("二进制附件，未抽取文本内容。\n");
            }
        }
        return builder.toString();
    }
}
