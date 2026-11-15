import type { ReactNode } from "react";
import { motion } from "motion/react";
import {
  CHAT_MOTION_DURATION,
  getChatLayoutTransition,
  useChatReducedMotion,
} from "~/lib/chat-motion";
import { cn } from "~/lib/utils";

export const COMPOSER_POPUP_CLASSNAME =
  "overflow-hidden rounded-[var(--radius-card-inner)] border border-border/70 bg-secondary/50";

export function ComposerOption({
  label,
  description,
  selected = false,
  onClick,
}: {
  label: string;
  description?: string | null;
  selected?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onMouseDown={(event) => event.preventDefault()}
      onClick={onClick}
      className={cn(
        "w-full rounded-[var(--radius-card-inner)] border px-3 py-2 text-left transition active:scale-[0.97]",
        selected
          ? "border-primary/30 bg-primary/10 text-primary"
          : "border-border/70 bg-background/70 text-foreground hover:bg-accent",
      )}
    >
      <div className="text-sm font-medium">{label}</div>
      {description ? <div className="mt-1 text-muted-foreground text-xs">{description}</div> : null}
    </button>
  );
}

export function ComposerPopup({ children }: { children: ReactNode }) {
  const reducedMotion = useChatReducedMotion();
  return (
    <motion.div
      layout
      initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: -10, height: 0 }}
      animate={{
        opacity: 1,
        y: 0,
        height: "auto",
        transition: reducedMotion
          ? { duration: 0.01 }
          : {
              opacity: { duration: CHAT_MOTION_DURATION.fast, ease: "easeOut" },
              y: getChatLayoutTransition(false),
              height: getChatLayoutTransition(false),
            },
      }}
      exit={
        reducedMotion
          ? { opacity: 0 }
          : { opacity: 0, y: -6, height: 0, transition: { duration: 0.12 } }
      }
      className={COMPOSER_POPUP_CLASSNAME}
    >
      {children}
    </motion.div>
  );
}
