import * as React from "react";
import { AnimatePresence, motion } from "motion/react";
import {
  Build,
  Category,
  ChevronDown,
  Computer,
  Globe,
  Image,
  Lightbulb,
  Memory,
  Terminal,
} from "~/lib/material-icons";
import { useTranslation } from "react-i18next";

import Markdown from "~/components/markdown/markdown";
import {
  CHAT_MOTION_DURATION,
  getChatFadeTransition,
  getChatLayoutTransition,
  useChatReducedMotion,
} from "~/lib/chat-motion";
import type { ActivityType, TimelineEntry } from "~/lib/message-turns";
import {
  getStringField,
  safeJsonParse,
  ToolApprovalActions,
  ToolDetailContent,
  ToolPreviewContent,
} from "~/lib/tool-activity";
import { cn } from "~/lib/utils";
import type { DisplaySetting } from "~/types";

function formatDuration(durationMs: number): string {
  const seconds = durationMs / 1000;
  return seconds < 10 ? `${seconds.toFixed(1)}s` : `${seconds.toFixed(0)}s`;
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

function entryMatchesType(entry: TimelineEntry, type: ActivityType | null) {
  if (!type) return false;
  if (entry.type === "reasoning") return type === "reasoning";
  if (entry.type === "ocr") return type === "ocr";
  return entry.activityType === type;
}

function buildOcrPreview(
  entry: Extract<TimelineEntry, { type: "ocr" }>,
  t: (key: string, options?: Record<string, unknown>) => string,
) {
  const pages =
    entry.pageNumbers.length > 0
      ? t("activity.timeline_ocr_pages", { pages: entry.pageNumbers.join(", ") })
      : null;
  return [entry.fileName, pages].filter(Boolean).join(" - ");
}

function TimelineDetail({
  children,
  reducedMotion,
}: {
  children: React.ReactNode;
  reducedMotion: boolean;
}) {
  return (
    <motion.div
      initial={reducedMotion ? { opacity: 0 } : { opacity: 0, height: 0, y: -4 }}
      animate={{
        opacity: 1,
        height: "auto",
        y: 0,
        transition: reducedMotion
          ? { duration: 0.01 }
          : {
              opacity: { duration: CHAT_MOTION_DURATION.fast, ease: "easeOut" },
              height: getChatLayoutTransition(false),
              y: getChatLayoutTransition(false),
            },
      }}
      exit={
        reducedMotion
          ? { opacity: 0 }
          : { opacity: 0, height: 0, y: -4, transition: { duration: 0.12 } }
      }
      className="overflow-hidden border-t border-border/60 bg-secondary/25"
    >
      <div className="px-3.5 py-3">{children}</div>
    </motion.div>
  );
}

export function ActivityTimeline({
  entries,
  displaySetting,
  open,
  onOpenChange: _onOpenChange,
  initialExpandedType = null,
  resetKey = 0,
  onToolApproval,
}: {
  entries: TimelineEntry[];
  displaySetting?: DisplaySetting | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialExpandedType?: ActivityType | null;
  resetKey?: number;
  onToolApproval?: (
    toolCallId: string,
    approved: boolean,
    reason: string,
    answer?: string,
  ) => void | Promise<void>;
}) {
  const { t } = useTranslation("message");
  const reducedMotion = useChatReducedMotion();
  const [expandedIds, setExpandedIds] = React.useState<Set<string>>(new Set());
  const [hasOpenedOnce, setHasOpenedOnce] = React.useState(false);

  React.useEffect(() => {
    if (!open) return;
    setHasOpenedOnce(true);
    const nextExpanded = new Set<string>();
    if (initialExpandedType) {
      entries.forEach((entry) => {
        if (entryMatchesType(entry, initialExpandedType)) {
          nextExpanded.add(entry.id);
        }
      });
    }
    setExpandedIds(nextExpanded);
  }, [entries, initialExpandedType, open, resetKey]);

  if (entries.length === 0) {
    return null;
  }

  return (
    <AnimatePresence initial={false}>
      {open ? (
        <motion.div
          initial={reducedMotion ? { opacity: 0 } : { opacity: 0, height: 0, y: -8 }}
          animate={{
            opacity: 1,
            height: "auto",
            y: 0,
            transition: reducedMotion
              ? { duration: 0.01 }
              : {
                  opacity: getChatFadeTransition(false),
                  height: getChatLayoutTransition(false),
                  y: getChatLayoutTransition(false),
                },
          }}
          exit={
            reducedMotion
              ? { opacity: 0 }
              : { opacity: 0, height: 0, y: -6, transition: { duration: 0.14 } }
          }
          aria-hidden={!open}
          className="overflow-hidden"
        >
          <motion.div className="space-y-2.5 rounded-[var(--radius-activity-large)] border border-border/70 bg-card/90 px-3 py-3 shadow-sm">
            <div>
              <div className="text-sm font-semibold">{t("activity.timeline_title")}</div>
              <div className="text-xs text-muted-foreground">
                {t("activity.timeline_description", { count: entries.length })}
              </div>
            </div>

            {entries.map((entry, index) => {
              const expanded = expandedIds.has(entry.id);
              const toggleExpanded = () => {
                setExpandedIds((prev) => {
                  const next = new Set(prev);
                  if (next.has(entry.id)) {
                    next.delete(entry.id);
                  } else {
                    next.add(entry.id);
                  }
                  return next;
                });
              };

              const initialAnimation = hasOpenedOnce
                ? false
                : reducedMotion
                  ? { opacity: 0 }
                  : { opacity: 0, y: 10, scale: 0.985 };

              if (entry.type === "reasoning") {
                const Icon = getActivityIcon("reasoning");
                return (
                  <motion.div
                    key={entry.id}
                    initial={initialAnimation}
                    animate={{
                      opacity: 1,
                      y: 0,
                      scale: 1,
                      transition: reducedMotion
                        ? { duration: 0.01 }
                        : {
                            opacity: {
                              duration: CHAT_MOTION_DURATION.fast,
                              delay: index * CHAT_MOTION_DURATION.stagger,
                            },
                            y: {
                              ...getChatLayoutTransition(false),
                              delay: index * CHAT_MOTION_DURATION.stagger,
                            },
                            scale: {
                              ...getChatLayoutTransition(false),
                              delay: index * CHAT_MOTION_DURATION.stagger,
                            },
                          },
                    }}
                    className="overflow-hidden rounded-[var(--radius-input-inner)] border border-border/70 bg-background/80"
                  >
                    <button
                      type="button"
                      onClick={toggleExpanded}
                      className="flex w-full items-center gap-3 px-3.5 py-2.5 text-left"
                    >
                      <Icon className="size-4 text-primary" />
                      <div className="min-w-0 flex-1">
                        <div className="text-sm font-medium">{t("activity.type.reasoning")}</div>
                        {entry.durationMs ? (
                          <div className="text-xs text-muted-foreground">
                            {t("activity.reasoning_done", {
                              duration: formatDuration(entry.durationMs),
                            })}
                          </div>
                        ) : null}
                      </div>
                      <motion.span
                        animate={{ rotate: expanded ? 180 : 0 }}
                        transition={getChatLayoutTransition(reducedMotion)}
                        className="text-muted-foreground"
                      >
                        <ChevronDown className="size-4" />
                      </motion.span>
                    </button>
                    <AnimatePresence initial={false}>
                      {expanded ? (
                        <TimelineDetail reducedMotion={reducedMotion}>
                          <Markdown
                            content={entry.reasoning.reasoning}
                            className="text-sm"
                            displaySetting={displaySetting}
                          />
                        </TimelineDetail>
                      ) : null}
                    </AnimatePresence>
                  </motion.div>
                );
              }

              if (entry.type === "ocr") {
                const Icon = getActivityIcon("ocr");
                const preview = buildOcrPreview(entry, t);
                const sourceLabel =
                  entry.source === "image"
                    ? t("activity.timeline_ocr_source_image")
                    : t("activity.timeline_ocr_source_pdf");

                return (
                  <motion.div
                    key={entry.id}
                    initial={initialAnimation}
                    animate={{
                      opacity: 1,
                      y: 0,
                      scale: 1,
                      transition: reducedMotion
                        ? { duration: 0.01 }
                        : {
                            opacity: {
                              duration: CHAT_MOTION_DURATION.fast,
                              delay: index * CHAT_MOTION_DURATION.stagger,
                            },
                            y: {
                              ...getChatLayoutTransition(false),
                              delay: index * CHAT_MOTION_DURATION.stagger,
                            },
                            scale: {
                              ...getChatLayoutTransition(false),
                              delay: index * CHAT_MOTION_DURATION.stagger,
                            },
                          },
                    }}
                    className="overflow-hidden rounded-[var(--radius-input-inner)] border border-border/70 bg-background/80"
                  >
                    <button
                      type="button"
                      onClick={toggleExpanded}
                      className="flex w-full items-center gap-3 px-3.5 py-2.5 text-left"
                    >
                      <Icon
                        className={cn("size-4 text-primary", entry.isLoading && "animate-pulse")}
                      />
                      <div className="min-w-0 flex-1">
                        <div className="text-sm font-medium">{t("activity.type.ocr")}</div>
                        {preview ? (
                          <div className="line-clamp-1 text-xs text-muted-foreground">
                            {preview}
                          </div>
                        ) : null}
                      </div>
                      <motion.span
                        animate={{ rotate: expanded ? 180 : 0 }}
                        transition={getChatLayoutTransition(reducedMotion)}
                        className="text-muted-foreground"
                      >
                        <ChevronDown className="size-4" />
                      </motion.span>
                    </button>
                    <AnimatePresence initial={false}>
                      {expanded ? (
                        <TimelineDetail reducedMotion={reducedMotion}>
                          <div className="space-y-3 text-sm text-muted-foreground">
                            {entry.fileName ? (
                              <div className="space-y-1">
                                <div className="text-xs font-medium text-foreground/80">
                                  {t("chat_message.copy_document")}
                                </div>
                                <div>{entry.fileName}</div>
                              </div>
                            ) : null}
                            <div className="space-y-1">
                              <div className="text-xs font-medium text-foreground/80">
                                {t("activity.timeline_ocr_source")}
                              </div>
                              <div>{sourceLabel}</div>
                            </div>
                            {entry.pageNumbers.length > 0 ? (
                              <div className="space-y-1">
                                <div className="text-xs font-medium text-foreground/80">
                                  {t("activity.timeline_ocr_pages_label")}
                                </div>
                                <div>{entry.pageNumbers.join(", ")}</div>
                              </div>
                            ) : null}
                          </div>
                        </TimelineDetail>
                      ) : null}
                    </AnimatePresence>
                  </motion.div>
                );
              }

              const Icon = getActivityIcon(entry.activityType);
              const toolArgs = safeJsonParse(entry.tool.input);
              const preview = getStringField(toolArgs, "query");

              return (
                <motion.div
                  key={entry.id}
                  initial={initialAnimation}
                  animate={{
                    opacity: 1,
                    y: 0,
                    scale: 1,
                    transition: reducedMotion
                      ? { duration: 0.01 }
                      : {
                          opacity: {
                            duration: CHAT_MOTION_DURATION.fast,
                            delay: index * CHAT_MOTION_DURATION.stagger,
                          },
                          y: {
                            ...getChatLayoutTransition(false),
                            delay: index * CHAT_MOTION_DURATION.stagger,
                          },
                          scale: {
                            ...getChatLayoutTransition(false),
                            delay: index * CHAT_MOTION_DURATION.stagger,
                          },
                        },
                  }}
                  className="overflow-hidden rounded-[var(--radius-input-inner)] border border-border/70 bg-background/80"
                >
                  <button
                    type="button"
                    onClick={toggleExpanded}
                    className="flex w-full items-start gap-3 px-3.5 py-2.5 text-left"
                  >
                    <Icon
                      className={cn(
                        "mt-0.5 size-4 text-primary",
                        entry.isLoading && "animate-pulse",
                      )}
                    />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <div className="text-sm font-medium">{entry.displayName}</div>
                          {preview ? (
                            <div className="line-clamp-1 text-xs text-muted-foreground">
                              {preview}
                            </div>
                          ) : null}
                        </div>
                        <ToolApprovalActions
                          tool={entry.tool}
                          onToolApproval={onToolApproval}
                          t={t}
                        />
                      </div>
                      <div className="pt-2">
                        <ToolPreviewContent tool={entry.tool} t={t} />
                      </div>
                    </div>
                    <motion.span
                      animate={{ rotate: expanded ? 180 : 0 }}
                      transition={getChatLayoutTransition(reducedMotion)}
                      className="mt-0.5 text-muted-foreground"
                    >
                      <ChevronDown className="size-4" />
                    </motion.span>
                  </button>
                  <AnimatePresence initial={false}>
                    {expanded ? (
                      <TimelineDetail reducedMotion={reducedMotion}>
                        <ToolDetailContent
                          tool={entry.tool}
                          t={t}
                          displaySetting={displaySetting}
                          onToolApproval={onToolApproval}
                        />
                      </TimelineDetail>
                    ) : null}
                  </AnimatePresence>
                </motion.div>
              );
            })}
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
