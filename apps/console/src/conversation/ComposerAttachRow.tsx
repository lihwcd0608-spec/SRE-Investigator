import { IconX } from "../icons";
import type { ConversationAttachmentPayload } from "../types";
import { imageSrc, isImageMedia, type PendingFile } from "./composerAttachments";

export function ComposerAttachRow({
  files,
  onRemove,
}: {
  files: PendingFile[];
  onRemove: (id: string) => void;
}) {
  if (files.length === 0) return null;
  return (
    <div className="attach-row" aria-label="待发送附件">
      {files.map((item) => {
        const image = isImageMedia(item.mediaType, item.name);
        return (
          <span className={`attach-chip${image ? " is-image" : ""}`} key={item.id}>
            {image ? <img src={imageSrc(item)} alt={item.name} /> : null}
            <span>{item.name}</span>
            <button type="button" aria-label={`移除 ${item.name}`} onClick={() => onRemove(item.id)}>
              <IconX size={12} />
            </button>
          </span>
        );
      })}
    </div>
  );
}

export function UserMessageMedia({ attachments }: { attachments?: ConversationAttachmentPayload[] }) {
  if (!attachments?.length) return null;
  const images = attachments.filter((item) => isImageMedia(item.mediaType, item.name) && item.dataBase64);
  const texts = attachments.filter((item) => !isImageMedia(item.mediaType, item.name));
  if (images.length === 0 && texts.length === 0) return null;
  return (
    <div className="bubble-attach">
      {images.length > 0 ? (
        <div className="bubble-images">
          {images.map((item, index) => {
            const src = imageSrc(item);
            return (
              <button
                key={`${item.name}-${index}`}
                type="button"
                className="bubble-image"
                title={item.name}
                onClick={() => window.open(src, "_blank", "noopener,noreferrer")}
              >
                <img src={src} alt={item.name} />
              </button>
            );
          })}
        </div>
      ) : null}
      {texts.map((item, index) => (
        <span className="attach-chip" key={`${item.name}-${index}`}>{item.name}</span>
      ))}
    </div>
  );
}
