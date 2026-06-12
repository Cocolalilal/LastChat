import * as React from "react";
import {
  AnimatePresence,
  motion,
} from "motion/react";

import { useTranslation } from "react-i18next";

import { ActivityPill } from "~/components/message/activity-pill";
import { ActivityTimeline } from "~/components/message/activity-timeline";
import { ChatMessageActionsRow, ChatMessageNerdLineRow } from "~/components/message/chat-message";
import { ChatMessageAnnotationsRow } from "~/components/message/chat-message-annotations";
import { ChatMessageAvatarRow } from "~/components/message/chat-message-avatar-row";
import { MessageParts } from "~/components/message/message-part";
import { TypingIndicator } from "~/components/ui/typing-indicator";
import {
  buildTimelineEntries,
  deriveActivityState,
  type ActivityType,
  type AssistantTurnGroup,
} from "~/lib/message-turns";
import { getChatLayoutTransition, useChatReducedMotion } from "~/lib/chat-motion";
import { cn } from "~/lib/utils";
import type { AssistantProfile, DisplaySetting, ProviderModel, TokenUsage, UIMessagePart } from "~/types";

function hasRenderableContentPart(part: UIMessagePart): boolean {
  switch (part.type) {
    case "text":
      return part.text.trim().length > 0;
    case "image":
    case "video":
    case "audio":
      return part.url.trim().length > 0;
    case "document":
      return part.url.trim().length > 0 || part.fileName.trim().length > 0;
    case "reasoning":
    case "tool":
      return false;
  }
}

function parseToolOutputJson(text: string): unknown {
  const trimmed = text.trim();
  if (!trimmed) return null;

  try {
    return JSON.parse(trimmed);
  } catch {
    const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
    if (!fenced) return null;
    try {
      return JSON.parse(fenced[1]);
    } catch {
      return null;
    }
  }
}

function buildCitationUrlMap(parts: UIMessagePart[]): Map<string, string> {
  const map = new Map<string, string>();

  parts.forEach((part) => {
    if (part.type !== "tool" || part.toolName !== "search_web") return;
    const outputText = part.output
      .filter((outputPart): outputPart is { type: "text"; text: string } => outputPart.type === "text")
      .map((outputPart) => outputPart.text)
      .join("\n");
    const parsed = parseToolOutputJson(outputText);
    if (!parsed || typeof parsed !== "object") return;
    const items = (parsed as { items?: unknown }).items;
    if (!Array.isArray(items)) return;

    items.forEach((item) => {
      if (!item || typeof item !== "object") return;
      const id = String((item as { id?: unknown }).id ?? "").trim();
      const url = String((item as { url?: unknown }).url ?? "").trim();
      if (!id || !url || map.has(id)) return;
      map.set(id, url);
    });
  });

  return map;
}

function combineUsage(turn: AssistantTurnGroup): TokenUsage | null {
  const usages = turn.items.map((item) => item.message.usage).filter((usage): usage is TokenUsage => Boolean(usage));
  if (usages.length === 0) return null;

  return {
    promptTokens: usages.reduce((total, usage) => total + usage.promptTokens, 0),
    completionTokens: usages.reduce((total, usage) => total + usage.completionTokens, 0),
    cachedTokens: usages.reduce((total, usage) => total + usage.cachedTokens, 0),
    totalTokens: usages.reduce((total, usage) => total + usage.totalTokens, 0),
  };
}

function buildStatsMessage(turn: AssistantTurnGroup) {
  const usage = combineUsage(turn);
  const createdAt = turn.items[0]?.message.createdAt ?? turn.displayMessage.createdAt;
  const finishedAt = [...turn.items]
    .reverse()
    .map((item) => item.message.finishedAt)
    .find((value) => value != null) ?? turn.displayMessage.finishedAt;

  return {
    ...turn.displayMessage,
    usage,
    createdAt,
    finishedAt,
  };
}

export function AssistantTurnMessage({
  turn,
  loading = false,
  assistant,
  displaySetting,
  onEdit,
  onRegenerate,
  onSelectBranch,
  onDelete,
  onFork,
  onToolApproval,
}: {
  turn: AssistantTurnGroup;
  loading?: boolean;
  assistant?: AssistantProfile | null;
  displaySetting?: DisplaySetting | null;
  model?: ProviderModel | null;
  onEdit?: (message: AssistantTurnGroup["actionTarget"]["message"]) => void | Promise<void>;
  onRegenerate?: (messageId: string) => void | Promise<void>;
  onSelectBranch?: (nodeId: string, selectIndex: number) => void | Promise<void>;
  onDelete?: (messageId: string) => void | Promise<void>;
  onFork?: (messageId: string) => void | Promise<void>;
  onToolApproval?: (toolCallId: string, approved: boolean, reason: string, answer?: string) => void | Promise<void>;
}) {
  const { t } = useTranslation("message");
  const reducedMotion = useChatReducedMotion();
  const [timelineOpen, setTimelineOpen] = React.useState(false);
  const [initialExpandedType, setInitialExpandedType] = React.useState<ActivityType | null>(null);
  const [timelineResetKey, setTimelineResetKey] = React.useState(0);

  const activityState = React.useMemo(
    () => deriveActivityState(turn.allParts, turn.annotations, loading),
    [loading, turn.allParts, turn.annotations],
  );
  const timelineEntries = React.useMemo(
    () => buildTimelineEntries(turn.allParts, turn.annotations, loading),
    [loading, turn.allParts, turn.annotations],
  );
  const citationUrlMap = React.useMemo(() => buildCitationUrlMap(turn.allParts), [turn.allParts]);
  const statsMessage = React.useMemo(() => buildStatsMessage(turn), [turn]);
  const hasMessageContent = turn.contentParts.some(hasRenderableContentPart) || activityState.type !== "hidden";
  const allowLayoutAnimation = !loading;
  const latestToolActivityType = React.useMemo(
    () => {
      const latestToolEntry = [...timelineEntries]
        .reverse()
        .find((entry) => entry.type === "tool" && entry.isLoading);
      return latestToolEntry?.type === "tool" ? latestToolEntry.activityType : null;
    },
    [timelineEntries],
  );

  const handleClickCitation = React.useCallback(
    (citationId: string) => {
      const url = citationUrlMap.get(citationId.trim());
      if (!url || typeof window === "undefined") return;
      window.open(url, "_blank", "noopener,noreferrer");
    },
    [citationUrlMap],
  );

  const showActions = loading ? false : hasMessageContent;

  return (
    <motion.div
      layout={allowLayoutAnimation}
      transition={getChatLayoutTransition(reducedMotion)}
      className="flex flex-col gap-2.5"
      data-message-role="assistant"
      data-message-loading={loading || undefined}
    >
      <motion.div layout={allowLayoutAnimation} className="flex w-full flex-col gap-1.5">
        <ChatMessageAvatarRow
          message={turn.displayMessage}
          hasMessageContent={hasMessageContent}
          assistant={assistant}
          displaySetting={displaySetting}
        />

        <AnimatePresence initial={false} mode="popLayout">
          {activityState.type === "waiting" ? (
            <motion.div
              key="assistant-waiting"
              layout={allowLayoutAnimation}
              initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reducedMotion ? { opacity: 0 } : { opacity: 0, y: -4, transition: { duration: 0.12 } }}
              transition={getChatLayoutTransition(reducedMotion)}
              className="flex w-full justify-start"
            >
              <TypingIndicator className="px-1 py-1.5" />
            </motion.div>
          ) : null}

          {activityState.type !== "hidden" && activityState.type !== "waiting" ? (
            <motion.div
              key="assistant-activity-pill"
              layout={allowLayoutAnimation}
              initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reducedMotion ? { opacity: 0 } : { opacity: 0, y: -4, transition: { duration: 0.12 } }}
              transition={getChatLayoutTransition(reducedMotion)}
              className="flex w-full justify-start"
            >
              <ActivityPill
                state={activityState}
                onClick={() => {
                  switch (activityState.type) {
                    case "completed_single":
                    case "completed_multiple":
                      setInitialExpandedType(null);
                      setTimelineResetKey((key) => key + 1);
                      setTimelineOpen((open) => !open);
                      return;
                    case "reasoning":
                      setInitialExpandedType("reasoning");
                      break;
                    case "ocr":
                      setInitialExpandedType("ocr");
                      break;
                    case "tool_use":
                      setInitialExpandedType(latestToolActivityType);
                      break;
                    case "replying":
                      setInitialExpandedType(null);
                      break;
                  }
                  setTimelineOpen((open) => !open);
                }}
              />
            </motion.div>
          ) : null}
        </AnimatePresence>

        <ActivityTimeline
          entries={timelineEntries}
          displaySetting={displaySetting}
          open={timelineOpen}
          onOpenChange={setTimelineOpen}
          initialExpandedType={initialExpandedType}
          resetKey={timelineResetKey}
          onToolApproval={onToolApproval}
        />

        {turn.contentParts.some(hasRenderableContentPart) ? (
          <motion.div layout={allowLayoutAnimation} className="flex w-full justify-start">
            <motion.div
              layout={allowLayoutAnimation}
              data-message-bubble
              className={cn(
                "flex w-full flex-col gap-2 rounded-[var(--radius-bubble)] bg-transparent px-0 py-0 text-sm",
              )}
            >
              <MessageParts
                parts={turn.contentParts}
                assistant={assistant}
                displaySetting={displaySetting}
                loading={loading}
                onToolApproval={onToolApproval}
                onClickCitation={handleClickCitation}
              />
            </motion.div>
          </motion.div>
        ) : null}
      </motion.div>

      {showActions ? (
        <div data-message-actions>
          <ChatMessageActionsRow
            node={turn.actionTarget.node}
            message={turn.actionTarget.message}
            loading={loading}
            alignRight={false}
            onEdit={onEdit}
            onRegenerate={onRegenerate}
            onSelectBranch={onSelectBranch}
            onDelete={onDelete}
            onFork={onFork}
          />
        </div>
      ) : null}

      <ChatMessageAnnotationsRow annotations={turn.annotations} alignRight={false} />
      <ChatMessageNerdLineRow
        message={statsMessage}
        alignRight={false}
        displaySetting={displaySetting}
      />
    </motion.div>
  );
}
