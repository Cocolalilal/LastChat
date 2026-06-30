import * as React from "react";
import { AnimatePresence, motion } from "motion/react";
import type { Transition } from "motion/react";
import {
  Build,
  Category,
  Computer,
  Globe,
  Image,
  Lightbulb,
  Memory,
  Sparkles,
  Terminal,
} from "~/lib/material-icons";
import { useTranslation } from "react-i18next";

import {
  CHAT_MOTION_DURATION,
  getChatFadeTransition,
  getChatLayoutTransition,
  getChatTactileTransition,
  useChatReducedMotion,
} from "~/lib/chat-motion";
import { categorizeToolName, type ActivityState, type ActivityType } from "~/lib/message-turns";
import { cn, serverNow } from "~/lib/utils";

const getActivityLayoutTransition = (reducedMotion: boolean): Transition =>
  reducedMotion ? { duration: 0.01 } : { type: "tween", duration: 0.22, ease: [0.4, 0, 0.2, 1] };

function formatDuration(durationMs: number): string {
  const seconds = Math.max(0, Math.floor(durationMs / 1000));
  return `${seconds}s`;
}

function getActivityIcon(type: ActivityType) {
  switch (type) {
    case "reasoning":
      return Lightbulb;
    case "ocr":
      return Image;
    case "search":
      return Globe;
    case "memory_recall":
      return Memory;
    case "python":
      return Terminal;
    case "workspace":
      return Computer;
    case "skill":
      return Category;
    case "mcp":
      return Memory;
    case "loading_model":
      return Memory;
    case "tool_other":
      return Build;
  }
}

function useLiveNow(active: boolean) {
  const [now, setNow] = React.useState(() => serverNow());

  React.useEffect(() => {
    if (!active) return;
    const id = window.setInterval(() => {
      setNow(serverNow());
    }, 1000);
    return () => window.clearInterval(id);
  }, [active]);

  return now;
}

interface PillSegment {
  key: string;
  type: ActivityType | "sparkles" | "replying";
  variant: "full" | "mini";
  label?: string;
  showIcon?: boolean;
}

function getStateKey(state: ActivityState) {
  switch (state.type) {
    case "waiting":
      return "waiting";
    case "replying":
      return "replying";
    case "ocr":
      return "ocr";
    case "reasoning":
      return "reasoning";
    case "tool_use":
      return `tool-${categorizeToolName(state.toolName)}`;
    case "completed_single":
      return `completed-single-${state.activityType}`;
    case "completed_multiple":
      return `completed-multiple-${state.activityTypes.join("-")}-${state.reasoningDurationMs ?? "none"}`;
    case "hidden":
      return "hidden";
  }
}

function buildSegments(
  state: ActivityState,
  t: (key: string, options?: Record<string, unknown>) => string,
  now: number,
): PillSegment[] {
  switch (state.type) {
    case "waiting":
      return [
        {
          key: "waiting",
          type: "sparkles",
          label: t("activity.waiting"),
          variant: "full",
        },
      ];
    case "replying":
      return [
        {
          key: "replying",
          type: "replying",
          label: t("activity.replying"),
          variant: "full",
          showIcon: false,
        },
      ];
    case "ocr":
      return [
        {
          key: "ocr-live",
          type: "ocr",
          label: t("activity.ocr_live"),
          variant: "full",
        },
      ];
    case "reasoning":
      return [
        {
          key: "reasoning-live",
          type: "reasoning",
          label: t("activity.reasoning_live", {
            duration: formatDuration(Math.max(now - state.startTimeMs, 0)),
          }),
          variant: "full",
        },
      ];
    case "tool_use":
      return [
        {
          key: `tool-${state.toolName}`,
          type: categorizeToolName(state.toolName),
          label: state.displayName,
          variant: "full",
        },
      ];
    case "completed_single":
      return [
        {
          key: `completed-${state.activityType}`,
          type: state.activityType,
          label:
            state.activityType === "reasoning" && state.durationMs
              ? t("activity.reasoning_done", { duration: formatDuration(state.durationMs) })
              : state.activityType === "ocr"
                ? state.count && state.count > 1
                  ? t("activity.ocr_done_count", { count: state.count })
                  : t("activity.ocr_done")
                : state.activityType === "reasoning"
                  ? t("activity.type.reasoning")
                  : t(`activity.type.${state.activityType}`),
          variant: "full",
        },
      ];
    case "completed_multiple":
      if (state.reasoningDurationMs) {
        return [
          {
            key: "completed-multi-reasoning",
            type: "reasoning",
            label: t("activity.reasoning_done", {
              duration: formatDuration(state.reasoningDurationMs),
            }),
            variant: "full",
          },
          ...state.activityTypes.map((toolType) => ({
            key: `completed-multi-${toolType}`,
            type: toolType,
            label: t(`activity.type.${toolType}`),
            variant: "mini" as const,
          })),
        ];
      }

      return state.activityTypes.map((toolType) => ({
        key: `completed-multi-${toolType}`,
        type: toolType,
        label: t(`activity.type.${toolType}`),
        variant: "mini" as const,
      }));
    case "hidden":
      return [];
  }
}

function ActivitySegmentContent({
  segment,
  emphasizeLive,
}: {
  segment: PillSegment;
  emphasizeLive: boolean;
}) {
  const Icon =
    segment.type === "replying"
      ? null
      : segment.type === "sparkles"
        ? Sparkles
        : getActivityIcon(segment.type);

  return (
    <span
      className={cn(
        "flex min-w-0 items-center overflow-hidden",
        segment.variant === "full" ? "gap-2" : "justify-center",
      )}
    >
      {segment.showIcon !== false && Icon ? (
        <span className="flex shrink-0 items-center justify-center">
          <Icon className={cn("size-3.5", emphasizeLive && "text-primary")} />
        </span>
      ) : null}
      {segment.variant === "full" && segment.label ? (
        <span className="truncate whitespace-nowrap tabular-nums">{segment.label}</span>
      ) : null}
    </span>
  );
}

function ActivitySegmentButton({
  segment,
  onClick,
  live,
  reducedMotion,
  delay = 0,
  isFirst,
  isLast,
  isOnly,
}: {
  segment: PillSegment;
  onClick: () => void;
  live: boolean;
  reducedMotion: boolean;
  delay?: number;
  isFirst?: boolean;
  isLast?: boolean;
  isOnly?: boolean;
}) {
  const radiusClass = isOnly
    ? "rounded-[var(--radius-activity-large)]"
    : isFirst
      ? "rounded-l-[var(--radius-activity-large)] rounded-r-[var(--radius-activity-small)]"
      : isLast
        ? "rounded-r-[var(--radius-activity-large)] rounded-l-[var(--radius-activity-small)]"
        : "rounded-[var(--radius-activity-small)]";

  return (
    <motion.button
      type="button"
      layout
      initial={reducedMotion ? { opacity: 0 } : { opacity: 0, x: -8, scale: 0.98 }}
      animate={{
        opacity: 1,
        x: 0,
        scale: 1,
        transition: reducedMotion
          ? { duration: 0.01 }
          : {
              opacity: { duration: CHAT_MOTION_DURATION.fast, delay, ease: "easeOut" },
              x: { ...getActivityLayoutTransition(false), delay },
              scale: { ...getChatTactileTransition(false), delay },
            },
      }}
      exit={
        reducedMotion
          ? { opacity: 0 }
          : { opacity: 0, x: -6, scale: 0.985, transition: { duration: 0.12 } }
      }
      whileHover={reducedMotion ? undefined : { y: -1, scale: 1.01 }}
      whileTap={reducedMotion ? undefined : { scale: 0.975 }}
      transition={getActivityLayoutTransition(reducedMotion)}
      onClick={onClick}
      className={cn(
        "border text-card-foreground shadow-sm transition-colors hover:bg-card active:shadow-none",
        radiusClass,
        segment.variant === "mini"
          ? "inline-flex size-9 items-center justify-center border-border/70 bg-card/88"
          : "inline-flex h-9 max-w-full items-center border-border/70 bg-card/88 px-3.5 text-xs font-medium",
      )}
      title={segment.variant === "mini" ? segment.type : undefined}
      aria-label={segment.label ?? segment.type}
    >
      <ActivitySegmentContent segment={segment} emphasizeLive={live} />
    </motion.button>
  );
}

export function ActivityPill({
  state,
  onClick,
  className,
}: {
  state: ActivityState;
  onClick: () => void;
  className?: string;
}) {
  const { t } = useTranslation("message");
  const reducedMotion = useChatReducedMotion();
  const live =
    state.type === "ocr" ||
    state.type === "reasoning" ||
    state.type === "tool_use" ||
    state.type === "waiting" ||
    state.type === "replying";
  const now = useLiveNow(live);
  const segments = React.useMemo(() => buildSegments(state, t, now), [state, t, now]);

  if (state.type === "hidden" || segments.length === 0) return null;

  return (
    <AnimatePresence initial={false} mode="popLayout">
      <motion.div
        key={getStateKey(state)}
        layout
        transition={getActivityLayoutTransition(reducedMotion)}
        initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: 4, scale: 0.98 }}
        animate={{
          opacity: 1,
          y: 0,
          scale: 1,
          transition: reducedMotion
            ? { duration: 0.01 }
            : {
                opacity: getChatFadeTransition(false),
                y: getActivityLayoutTransition(false),
                scale: getChatTactileTransition(false),
              },
        }}
        exit={
          reducedMotion
            ? { opacity: 0 }
            : { opacity: 0, y: 4, scale: 0.985, transition: { duration: 0.14 } }
        }
        className={cn("inline-flex max-w-full", className)}
      >
        <motion.div
          layout
          transition={getActivityLayoutTransition(reducedMotion)}
          className="inline-flex max-w-full items-center gap-[2px]"
        >
          <AnimatePresence initial={false}>
            {segments.map((segment, index) => (
              <ActivitySegmentButton
                key={segment.key}
                segment={segment}
                onClick={onClick}
                live={live && index === 0}
                reducedMotion={reducedMotion}
                delay={reducedMotion ? 0 : index * CHAT_MOTION_DURATION.stagger}
                isOnly={segments.length === 1}
                isFirst={index === 0 && segments.length > 1}
                isLast={index === segments.length - 1 && segments.length > 1}
              />
            ))}
          </AnimatePresence>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}
