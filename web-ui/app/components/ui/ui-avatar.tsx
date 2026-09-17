import { Avatar, AvatarFallback, AvatarImage } from "~/components/ui/avatar";
import { resolveFileUrl } from "~/lib/files";
import type { AssistantAvatar } from "~/types";

export interface UIAvatarProps {
  name: string;
  avatar?: AssistantAvatar | null;
  size?: "default" | "sm" | "lg";
  className?: string;
}

function getDisplayName(name: string): string {
  const trimmed = name.trim();
  if (trimmed.length > 0) {
    return trimmed;
  }

  return "A";
}

function getAvatarFallback(name: string, avatar?: AssistantAvatar | null): string {
  const content = avatar?.content?.trim();
  if (content) {
    return content;
  }

  return getDisplayName(name).slice(0, 1).toUpperCase();
}

function getAvatarImage(avatar?: AssistantAvatar | null): string | null {
  const url = avatar?.url?.trim();
  if (!url) {
    return null;
  }

  return resolveFileUrl(url);
}

function blobSvg(avatar: AssistantAvatar): string {
  const color = avatar.color?.trim() || "#009FE0";
  const pack = avatar.eyes?.toLowerCase() === "grok" ? "grok" : "generical";
  // Static SVG only — the Android canvas engine is not ported to web yet.
  if (pack === "grok") {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
      <circle cx="32" cy="32" r="32" fill="${color}"/>
      <ellipse cx="24.5" cy="30" rx="5.2" ry="11.4" fill="white" transform="rotate(-26 24.5 30)"/>
      <ellipse cx="39.5" cy="30" rx="3.6" ry="11.4" fill="white" transform="rotate(-26 39.5 30)"/>
    </svg>`;
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
    <circle cx="32" cy="32" r="32" fill="${color}"/>
    <rect x="16.5" y="20" width="12" height="20" rx="6" fill="white" stroke="#d7f4ff" stroke-width="1.4"/>
    <rect x="35.5" y="20" width="12" height="20" rx="6" fill="white" stroke="#d7f4ff" stroke-width="1.4"/>
  </svg>`;
}

export function UIAvatar({ name, avatar, size = "default", className }: UIAvatarProps) {
  const imageUrl = getAvatarImage(avatar);
  const fallback = getAvatarFallback(name, avatar);
  const isBlob = avatar?.type === "blob";
  const svg = isBlob && avatar ? blobSvg(avatar) : null;
  const dataUrl = svg ? `data:image/svg+xml;utf8,${encodeURIComponent(svg)}` : null;

  // Radix Avatar keeps image loading state on the root; force remount when source changes.
  const avatarIdentity = `${avatar?.type ?? "dummy"}:${avatar?.url ?? ""}:${avatar?.content ?? ""}:${avatar?.color ?? ""}:${avatar?.eyes ?? ""}:${name}`;

  return (
    <Avatar key={avatarIdentity} size={size} className={className}>
      {(dataUrl || imageUrl) && <AvatarImage src={dataUrl ?? imageUrl ?? ""} alt={name} />}
      <AvatarFallback>{fallback}</AvatarFallback>
    </Avatar>
  );
}
