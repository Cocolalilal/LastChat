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

function clampUnit(value: unknown, fallback: number): number {
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(1.5, Math.max(0.6, n));
}

// Static SVG only: the animated Android canvas engine is not ported to web yet.
// Body flat colour, light eyes (never holes) so it reads on any background.
function blobSvg(avatar: AssistantAvatar): string {
  const color = avatar.color?.trim() || "#009FE0";
  const eyeColor = avatar.eyeColor?.trim() || "#FBFDFF";
  const pack = avatar.eyes?.toLowerCase() === "grok" ? "grok" : "generical";
  const size = clampUnit(avatar.eyeSize, 1);
  const spacing = clampUnit(avatar.eyeSpacing, 1);
  const round = clampUnit(avatar.eyeRoundness, 1);
  if (pack === "grok") {
    // sphere rest gaze: eyes lean "\\" ~26°, inner eye a touch bigger than outer
    const split = 7.6 * spacing;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
      <circle cx="32" cy="32" r="30" fill="${color}"/>
      <rect x="${32 - split - 3.4 * size}" y="${25 - 6.6 * size}" width="${6.8 * size}" height="${13.2 * size}" rx="${3.4 * size}" fill="${eyeColor}" transform="rotate(-26 ${32 - split} 27)"/>
      <rect x="${32 + split - 2.9 * size}" y="${25 - 6.6 * size}" width="${5.8 * size}" height="${12.6 * size}" rx="${2.9 * size}" fill="${eyeColor}" transform="rotate(-26 ${32 + split} 26)"/>
    </svg>`;
  }
  const w = 7 * size;
  const h = 15.6 * size;
  const gap = 7.4 * spacing;
  const rx = Math.max(1.2, (w / 2) * round);
  const left = 32 - gap - w / 2;
  const right = 32 + gap - w / 2;
  const top = 32 - h / 2;
  const accent = avatar.accentColor?.trim() || "#EAF7FF";
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
    <circle cx="32" cy="32" r="30" fill="${color}"/>
    <rect x="${left}" y="${top}" width="${w}" height="${h}" rx="${rx}" fill="${eyeColor}"/>
    <rect x="${right}" y="${top}" width="${w}" height="${h}" rx="${rx}" fill="${eyeColor}"/>
    <rect x="${left + w * 0.2}" y="${top + 1.2}" width="${w * 0.6}" height="${h * 0.34}" rx="${rx * 0.7}" fill="${accent}" opacity="0.55"/>
    <rect x="${right + w * 0.2}" y="${top + 1.2}" width="${w * 0.6}" height="${h * 0.34}" rx="${rx * 0.7}" fill="${accent}" opacity="0.55"/>
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
