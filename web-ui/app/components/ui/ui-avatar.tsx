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
  const size = clampUnit(avatar.eyeSize, 1);
  const spacing = clampUnit(avatar.eyeSpacing, 1);
  const round = clampUnit(avatar.eyeRoundness, 1);
  // Static SVG only — the Android canvas engine is not ported to web yet.
  if (pack === "grok") {
    const split = 7 * spacing;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
      <defs>
        <mask id="grok-eyes">
          <rect width="64" height="64" fill="white"/>
          <ellipse cx="${32 - split}" cy="28" rx="${4.2 * size}" ry="${9.6 * size}" fill="black" transform="rotate(-26 ${32 - split} 28)"/>
          <ellipse cx="${32 + split}" cy="27" rx="${3.1 * size}" ry="${9.6 * size}" fill="black" transform="rotate(-26 ${32 + split} 27)"/>
        </mask>
      </defs>
      <circle cx="32" cy="32" r="32" fill="${color}" mask="url(#grok-eyes)"/>
    </svg>`;
  }
  const w = 7.4 * size;
  const h = 20.4 * size;
  const gap = 8 * spacing;
  const rx = Math.max(1.2, (w / 2) * round);
  const left = 32 - gap - w / 2;
  const right = 32 + gap - w / 2;
  const top = 32 - h / 2;
  const accent = avatar.accentColor?.trim() || "#E8F7FF";
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
    <circle cx="32" cy="32" r="32" fill="${color}"/>
    <rect x="${left}" y="${top}" width="${w}" height="${h}" rx="${rx}" fill="white"/>
    <rect x="${right}" y="${top}" width="${w}" height="${h}" rx="${rx}" fill="white"/>
    <rect x="${left + w * 0.18}" y="${top + 1.2}" width="${w * 0.64}" height="${h * 0.38}" rx="${rx * 0.7}" fill="${accent}" opacity="0.55"/>
    <rect x="${right + w * 0.18}" y="${top + 1.2}" width="${w * 0.64}" height="${h * 0.38}" rx="${rx * 0.7}" fill="${accent}" opacity="0.55"/>
  </svg>`;
}

function clampUnit(value: unknown, fallback: number): number {
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(1.5, Math.max(0.6, n));
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
