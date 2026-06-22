import * as React from "react";

import { cn } from "~/lib/utils";
import { resolveFileUrl } from "~/lib/files";
import { useTheme } from "~/components/theme-provider";

export interface AIIconProps {
  name: string;
  iconUrl?: string | null;
  providerSlug?: string | null;
  customIconUri?: string | null;
  size?: number;
  loading?: boolean;
  className?: string;
  imageClassName?: string;
  allowNameIconFallback?: boolean;
}

function toFallbackText(name: string): string {
  const trimmed = name.trim();
  if (trimmed.length === 0) {
    return "A";
  }

  return trimmed.slice(0, 1).toUpperCase();
}

function isCatalogIconUrl(url: string): boolean {
  const lower = url.toLowerCase();
  return (
    lower.includes("/catalog/icons/") ||
    lower.includes("catalog/icons/") ||
    lower.includes("raw.githubusercontent.com/cocolalilal/lastchat") ||
    lower.includes("jsdelivr.net/gh/cocolalilal/lastchat") ||
    lower.startsWith("icons/") ||
    lower.startsWith("/icons/") ||
    lower.includes("file:///android_asset/icons/")
  );
}

function isRemoteUrl(url: string): boolean {
  return url.startsWith("https://") || url.startsWith("http://");
}

function isLocalFileUrl(url: string): boolean {
  return (
    url.startsWith("file://") ||
    url.startsWith("content://") ||
    url.startsWith("android.resource://")
  );
}

function buildApiIconSrc({
  name,
  icon,
  providerSlug,
  theme,
}: {
  name: string;
  icon?: string | null;
  providerSlug?: string | null;
  theme: "dark" | "light";
}) {
  const params = new URLSearchParams({ name });
  if (icon) params.set("icon", icon);
  if (providerSlug) params.set("providerSlug", providerSlug);
  params.set("theme", theme);
  return `/api/ai-icon?${params.toString()}`;
}

export function AIIcon({
  name,
  iconUrl,
  customIconUri,
  providerSlug,
  size = 24,
  loading = false,
  className,
  imageClassName,
  allowNameIconFallback = true,
}: AIIconProps) {
  const normalizedName = name.trim() || "auto";
  const fallbackText = toFallbackText(normalizedName);
  const { resolvedMode } = useTheme();
  
  const srcStack = React.useMemo(() => {
    const stack: string[] = [];
    if (customIconUri) {
      if (customIconUri.startsWith("lobehub://")) {
        stack.push(buildApiIconSrc({
          name: normalizedName,
          providerSlug: customIconUri.replace(/^lobehub:\/\//i, ""),
          theme: resolvedMode,
        }));
      } else if (isCatalogIconUrl(customIconUri)) {
        stack.push(buildApiIconSrc({ name: normalizedName, icon: customIconUri, providerSlug, theme: resolvedMode }));
      } else if (isRemoteUrl(customIconUri)) {
        stack.push(customIconUri);
      } else if (isLocalFileUrl(customIconUri)) {
        stack.push(resolveFileUrl(customIconUri));
      }
    }
    if (iconUrl) {
      if (isCatalogIconUrl(iconUrl)) {
        stack.push(buildApiIconSrc({ name: normalizedName, icon: iconUrl, providerSlug, theme: resolvedMode }));
      } else if (isRemoteUrl(iconUrl)) {
        stack.push(iconUrl);
      } else if (isLocalFileUrl(iconUrl)) {
        stack.push(resolveFileUrl(iconUrl));
      }
    }
    if (allowNameIconFallback) {
      stack.push(buildApiIconSrc({ name: normalizedName, providerSlug, theme: resolvedMode }));
    }
    return stack;
  }, [allowNameIconFallback, customIconUri, iconUrl, normalizedName, providerSlug, resolvedMode]);

  const [srcIndex, setSrcIndex] = React.useState(0);
  const [loaded, setLoaded] = React.useState(false);

  React.useEffect(() => {
    setSrcIndex(0);
    setLoaded(false);
  }, [srcStack]);

  const currentSrc = srcIndex < srcStack.length ? srcStack[srcIndex] : null;
  const loadFailed = currentSrc === null;

  return (
    <span
      className={cn(
        "relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full",
        loading && "animate-pulse",
        className,
      )}
      style={{ width: size, height: size }}
      aria-label={normalizedName}
      title={normalizedName}
    >
      <span
        className={cn(
          "text-[10px] font-medium text-muted-foreground transition-opacity",
          loaded && !loadFailed && "opacity-0",
        )}
      >
        {fallbackText}
      </span>
      {!loadFailed && currentSrc ? (
        <img
          key={currentSrc}
          src={currentSrc}
          alt={normalizedName}
          className={cn(
            "absolute h-[72%] w-[72%] object-contain transition-opacity",
            loaded ? "opacity-100" : "opacity-0",
            imageClassName,
          )}
          decoding="async"
          onLoad={() => {
            setLoaded(true);
          }}
          onError={() => {
            // Unmounts this img and mounts a new one with next URL
            setSrcIndex((prev) => prev + 1);
          }}
        />
      ) : null}
    </span>
  );
}
