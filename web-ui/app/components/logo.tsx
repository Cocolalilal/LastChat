import { cn } from "~/lib/utils";

import type { ComponentPropsWithoutRef } from "react";

export default function Logo({
  className,
  alt = "LastChat",
  ...props
}: ComponentPropsWithoutRef<"img">) {
  return (
    <img
      src="/favicon.png"
      alt={alt}
      className={cn("h-auto w-auto object-contain", className)}
      draggable={false}
      {...props}
    />
  );
}
