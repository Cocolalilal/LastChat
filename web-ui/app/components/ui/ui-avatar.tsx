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
// Generical = white-border + pale vertical gradient. Grok = small dark slits.
function blobSvg(avatar: AssistantAvatar): string {
  const color = avatar.color?.trim() || "#009FE0";
  const pack = avatar.eyes?.toLowerCase() === "grok" ? "grok" : "generical";
  const size = clampUnit(avatar.eyeSize, 1);
  const spacing = clampUnit(avatar.eyeSpacing, 1);
  const round = clampUnit(avatar.eyeRoundness, 1);
  if (pack === "grok") {
    const eye = avatar.eyeColor?.trim() || "#171717";
    const slit = "#171717";
    const fill = eye.toLowerCase() === "#fbfdff" || eye.toLowerCase() === "#ffffff" ? slit : eye;
    const split = 6.4 * spacing;
    const w = 5.6 * size;
    const h = 2.1 * size;
    const y = 24;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
      <circle cx="32" cy="32" r="30" fill="${color}"/>
      <rect x="${32 - split - w / 2}" y="${y - h / 2}" width="${w}" height="${h}" rx="${h / 2}" fill="${fill}" transform="rotate(-20 ${32 - split} ${y})"/>
      <rect x="${32 + split - w / 2}" y="${y - h / 2}" width="${w}" height="${h}" rx="${h / 2}" fill="${fill}" transform="rotate(-20 ${32 + split} ${y})"/>
    </svg>`;
  }
  const w = 10.4 * size;
  const h = 20.8 * size;
  const gap = 9.2 * spacing;
  const rx = Math.max(1.2, (w / 2) * round);
  const left = 32 - gap - w / 2;
  const right = 32 + gap - w / 2;
  const top = 32 - h / 2;
  const stroke = avatar.eyeColor?.trim() || "#FBFDFF";
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
    <defs>
      <linearGradient id="ge" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="#FFFFFF"/>
        <stop offset="1" stop-color="#C7EAF8"/>
      </linearGradient>
    </defs>
    <circle cx="32" cy="32" r="30" fill="${color}"/>
    <rect x="${left}" y="${top}" width="${w}" height="${h}" rx="${rx}" fill="url(#ge)" stroke="${stroke}" stroke-width="2.4"/>
    <rect x="${right}" y="${top}" width="${w}" height="${h}" rx="${rx}" fill="url(#ge)" stroke="${stroke}" stroke-width="2.4"/>
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
