import * as React from "react";

import { cn } from "~/lib/utils";
import { appendWebAuthQuery } from "~/services/api";

export interface AIIconProps {
  name: string;
  size?: number;
  loading?: boolean;
  className?: string;
  imageClassName?: string;
}

function toFallbackText(name: string): string {
  const trimmed = name.trim();
  if (trimmed.length === 0) {
    return "A";
  }

  return trimmed.slice(0, 1).toUpperCase();
}

export function AIIcon({
  name,
  size = 24,
  loading = false,
  className,
  imageClassName,
}: AIIconProps) {
  const normalizedName = name.trim() || "auto";
  const fallbackText = toFallbackText(normalizedName);
  const src = React.useMemo(
    () => appendWebAuthQuery(`/api/ai-icon?name=${encodeURIComponent(normalizedName)}`),
    [normalizedName],
  );
  const [loaded, setLoaded] = React.useState(false);
  const [loadFailed, setLoadFailed] = React.useState(false);

  React.useEffect(() => {
    setLoaded(false);
    setLoadFailed(false);
  }, [src]);

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
      {!loadFailed ? (
        <img
          src={src}
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
            setLoadFailed(true);
          }}
        />
      ) : null}
    </span>
  );
}
