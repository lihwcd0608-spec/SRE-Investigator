import { useState } from "react";
import type { ConversationAttachmentPayload } from "../types";

export interface PendingFile {
  id: string;
  name: string;
  mediaType: string;
  text?: string;
  dataBase64?: string;
  preview?: string;
}

const TEXT_TYPES = new Set([
  "text/plain",
  "text/markdown",
  "text/csv",
  "application/json",
  "application/xml",
  "text/xml",
  "application/x-yaml",
  "text/yaml",
]);
const TEXT_EXT = /\.(txt|md|log|json|ya?ml|xml|csv|sql|properties|conf)$/i;
const MAX_TEXT = 80_000;
const MAX_IMAGE = 2 * 1024 * 1024;
const MAX_FILES = 8;

export function isImageMedia(mediaType?: string, name?: string) {
  return Boolean(mediaType?.startsWith("image/") || (name && /\.(png|jpe?g|gif|webp|bmp)$/i.test(name)));
}

export function filesFromClipboard(data: DataTransfer | null): File[] {
  if (!data) return [];
  const files: File[] = [];
  const seen = new Set<File>();
  const add = (file: File | null) => {
    if (!file || seen.has(file)) return;
    seen.add(file);
    files.push(file);
  };
  for (const item of Array.from(data.items || [])) {
    if (item.kind === "file") add(item.getAsFile());
  }
  for (const file of Array.from(data.files || [])) add(file);
  return files;
}

export function consumePastedImages(event: { clipboardData: DataTransfer | null; preventDefault: () => void }): File[] {
  const images = filesFromClipboard(event.clipboardData).filter((file) => isImageMedia(file.type, file.name));
  if (images.length === 0) return [];
  event.preventDefault();
  return images.map(nameScreenshot);
}

function nameScreenshot(file: File): File {
  const generic = !file.name || /^image\.(png|jpe?g|gif|webp)$/i.test(file.name);
  if (!generic) return file;
  const stamp = new Date().toISOString().slice(11, 19).replace(/:/g, "");
  const ext = (file.type.split("/")[1] || "png").replace("jpeg", "jpg");
  return new File([file], `截图-${stamp}.${ext}`, { type: file.type || "image/png" });
}

export async function readComposerFile(file: File): Promise<PendingFile> {
  const mediaType = file.type || "application/octet-stream";
  const id = `${file.name}-${file.size}-${file.lastModified}-${Math.random().toString(36).slice(2, 8)}`;
  if (isImageMedia(mediaType, file.name)) {
    if (file.size > MAX_IMAGE) throw new Error(`${file.name} 超过 2MB`);
    const dataBase64 = await fileToBase64(file);
    return { id, name: file.name, mediaType: mediaType.startsWith("image/") ? mediaType : "image/png", dataBase64, preview: URL.createObjectURL(file) };
  }
  if (TEXT_TYPES.has(mediaType) || TEXT_EXT.test(file.name)) {
    const text = (await file.text()).slice(0, MAX_TEXT);
    return { id, name: file.name, mediaType: mediaType.startsWith("text/") ? mediaType : "text/plain", text };
  }
  throw new Error(`暂不支持 ${file.name}，请上传图片或文本文件`);
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error(`读取 ${file.name} 失败`));
    reader.onload = () => {
      const result = String(reader.result || "");
      const comma = result.indexOf(",");
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.readAsDataURL(file);
  });
}

export function toAttachmentPayloads(files: PendingFile[]): ConversationAttachmentPayload[] {
  return files.map((item) => ({
    name: item.name,
    mediaType: item.mediaType,
    text: item.text,
    dataBase64: item.dataBase64,
  }));
}

export function imageSrc(item: { mediaType?: string; dataBase64?: string; preview?: string }) {
  if (item.preview) return item.preview;
  if (item.dataBase64) return `data:${item.mediaType || "image/png"};base64,${item.dataBase64}`;
  return "";
}

function revokePreview(file: PendingFile) {
  if (file.preview) URL.revokeObjectURL(file.preview);
}

export function useComposerAttachments() {
  const [files, setFiles] = useState<PendingFile[]>([]);
  const [attachError, setAttachError] = useState("");

  const addFiles = async (list: FileList | File[]) => {
    setAttachError("");
    try {
      const next = await Promise.all(Array.from(list).map(readComposerFile));
      setFiles((current) => {
        const merged = [...current];
        for (const item of next) {
          if (!merged.some((existing) => existing.name === item.name && existing.mediaType === item.mediaType && existing.dataBase64 === item.dataBase64 && existing.text === item.text)) {
            merged.push(item);
          }
        }
        const kept = merged.slice(0, MAX_FILES);
        merged.slice(MAX_FILES).forEach(revokePreview);
        return kept;
      });
    } catch (cause) {
      setAttachError(cause instanceof Error ? cause.message : "添加附件失败");
    }
  };

  const onPaste = (event: { clipboardData: DataTransfer | null; preventDefault: () => void }) => {
    const images = consumePastedImages(event);
    if (images.length) void addFiles(images);
  };

  const remove = (id: string) => {
    setFiles((current) => {
      const gone = current.find((item) => item.id === id);
      if (gone) revokePreview(gone);
      return current.filter((item) => item.id !== id);
    });
  };

  const clear = () => {
    setFiles((current) => {
      current.forEach(revokePreview);
      return [];
    });
  };

  return { files, attachError, addFiles, onPaste, remove, clear, payloads: () => toAttachmentPayloads(files) };
}
