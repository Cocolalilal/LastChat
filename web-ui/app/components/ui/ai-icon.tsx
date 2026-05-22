import * as React from "react";

import { cn } from "~/lib/utils";

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
  return (
    url.toLowerCase().includes("/lastchat/main/catalog/icons/") ||
    url.toLowerCase().includes("/lastchat/refs/heads/main/catalog/icons/")
  );
}

export function AIIcon({
  name,
  iconUrl,
  customIconUri,
  size = 24,
  loading = false,
  className,
  imageClassName,
  allowNameIconFallback = false,
}: AIIconProps) {
  const normalizedName = name.trim() || "auto";
  const fallbackText = toFallbackText(normalizedName);
  
  const srcStack = React.useMemo(() => {
    const stack: string[] = [];
    if (customIconUri && isCatalogIconUrl(customIconUri)) {
      stack.push(customIconUri);
    }
    if (iconUrl && isCatalogIconUrl(iconUrl)) {
      stack.push(iconUrl);
    }
    if (allowNameIconFallback) {
      stack.push(`/api/ai-icon?name=${encodeURIComponent(normalizedName)}`);
    }
    return stack;
  }, [customIconUri, iconUrl, allowNameIconFallback, normalizedName]);

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
        "relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-secondary",
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
