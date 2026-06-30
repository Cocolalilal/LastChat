import * as React from "react";
import { AnimatePresence, motion } from "motion/react";

import { useNavigate, useParams } from "react-router";

import {
  ConversationQuickJump,
  getConversationMessageAnchorId,
} from "~/components/conversation-quick-jump";
import { ConversationGreeting } from "~/components/conversation-greeting";
import { ConversationSidebar } from "~/components/conversation-sidebar";
import {
  Conversation,
  ConversationContent,
  ConversationEmptyState,
  ConversationScrollButton,
} from "~/components/extended/conversation";
import { ChatInput } from "~/components/input/chat-input";
import { AssistantTurnMessage } from "~/components/message/assistant-turn-message";
import { ChatMessage } from "~/components/message/chat-message";
import { parseAskUserQuestions, safeJsonParse, TOOL_NAMES } from "~/lib/tool-activity";
import { Drawer, DrawerContent } from "~/components/ui/drawer";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "~/components/ui/resizable";
import { TypingIndicator } from "~/components/ui/typing-indicator";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "~/components/ui/sidebar";
import { useIsMobile } from "~/hooks/use-mobile";
import { toConversationSummaryUpdate, useConversationList } from "~/hooks/use-conversation-list";
import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { resolveEffectiveDisplaySetting } from "~/lib/chat-appearance";
import {
  getChatLayoutTransition,
  getChatLiftVariants,
  useChatReducedMotion,
} from "~/lib/chat-motion";
import { convertConversationToMarkdown, downloadMarkdown } from "~/lib/export-markdown";
import { groupSelectedNodesIntoTurns, type SelectedNodeMessage } from "~/lib/message-turns";
import { CHAT_COLUMN_CLASSNAME, CHAT_PAGE_PADDING_CLASSNAME } from "~/lib/chat-layout";
import {
  buildEditedParts,
  createHomeDraftId,
  getQuickJumpPreview,
  shouldDeleteAttachmentFileOnRemove,
  stripEditDraftMetadata,
  toEditDraft,
  type EditingSession,
} from "~/routes/conversation-draft-support";
import { cn } from "~/lib/utils";
import api, { sse } from "~/services/api";
import { useChatInputStore, useAppStore } from "~/stores";
import {
  useWorkbench,
  useWorkbenchController,
  WorkbenchProvider,
} from "~/components/workbench/workbench-context";
import {
  type ConversationDto,
  type MessageNodeDto,
  type MessageDto,
  type ConversationNodeUpdateEventDto,
  type ConversationErrorEventDto,
  type ConversationSnapshotEventDto,
  type ContextRefreshResponseDto,
  type ProviderModel,
  type Settings,
  type UIMessagePart,
} from "~/types";
import { MessageSquare } from "lucide-react";
import type { PanelImperativeHandle } from "react-resizable-panels";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import i18n from "~/i18n";

type ConversationStreamEvent =
  | ConversationSnapshotEventDto
  | ConversationNodeUpdateEventDto
  | ConversationErrorEventDto;

type ConversationSummaryUpdater = (update: ReturnType<typeof toConversationSummaryUpdate>) => void;

const EMPTY_INPUT_ATTACHMENTS: UIMessagePart[] = [];
const EMPTY_SUGGESTIONS: string[] = [];
const LazyWorkbenchHost = React.lazy(async () => {
  const module = await import("~/components/workbench/workbench-host");
  return { default: module.WorkbenchHost };
});

function WorkbenchHostFallback({ className }: { className?: string }) {
  return <div className={cn("min-h-0 flex-1 bg-background/60", className)} aria-hidden />;
}

function applyNodeUpdate(
  conversation: ConversationDto,
  event: ConversationNodeUpdateEventDto,
): ConversationDto {
  if (conversation.id !== event.conversationId) {
    return conversation;
  }

  const nextNodes = [...conversation.messages];
  const indexById = nextNodes.findIndex((node) => node.id === event.nodeId);
  const targetIndex = indexById >= 0 ? indexById : event.nodeIndex;

  if (targetIndex < 0) {
    return conversation;
  }

  if (targetIndex < nextNodes.length) {
    nextNodes[targetIndex] = event.node;
  } else if (targetIndex === nextNodes.length) {
    nextNodes.push(event.node);
  } else {
    return conversation;
  }

  return {
    ...conversation,
    messages: nextNodes,
    updateAt: event.updateAt,
    isGenerating: event.isGenerating,
  };
}

function useConversationDetail(activeId: string | null, updateSummary: ConversationSummaryUpdater) {
  const { t } = useTranslation("page");
  const [detail, setDetail] = React.useState<ConversationDto | null>(null);
  const [detailLoading, setDetailLoading] = React.useState(false);
  const [detailError, setDetailError] = React.useState<string | null>(null);

  const resetDetail = React.useCallback(() => {
    setDetail(null);
    setDetailError(null);
    setDetailLoading(false);
  }, []);

  React.useEffect(() => {
    if (!activeId) {
      resetDetail();
      return;
    }

    let mounted = true;
    setDetail(null);
    setDetailLoading(true);
    setDetailError(null);

    const abortController = new AbortController();

    api
      .get<ConversationDto>(`conversations/${activeId}`, { signal: abortController.signal })
      .then((data) => {
        if (!mounted) return;
        setDetail(data);
        updateSummary(toConversationSummaryUpdate(data));
      })
      .catch((err: Error) => {
        if (!mounted) return;
        setDetailError(err.message || t("conversations.errors.load_detail_failed"));
        setDetail(null);
      })
      .finally(() => {
        if (!mounted) return;
        setDetailLoading(false);
      });

    void sse<ConversationStreamEvent>(
      `conversations/${activeId}/stream`,
      {
        onMessage: ({ event, data }) => {
          if (!mounted) return;

          if (event === "error" && data.type === "error") {
            toast.error(data.message);
            return;
          }

          if (event === "snapshot" && data.type === "snapshot") {
            useAppStore.getState().setClockOffset(data.serverTime);
            setDetail(data.conversation);
            updateSummary(toConversationSummaryUpdate(data.conversation));
            setDetailError(null);
            setDetailLoading(false);
            return;
          }

          if (event !== "node_update" || data.type !== "node_update") return;

          useAppStore.getState().setClockOffset(data.serverTime);
          setDetail((prev) => {
            if (!prev) return prev;
            const next = applyNodeUpdate(prev, data);
            if (next === prev) return prev;
            if (prev.isGenerating !== next.isGenerating) {
              updateSummary(toConversationSummaryUpdate(next));
            }
            return next;
          });
          setDetailError(null);
          setDetailLoading(false);
        },
        onError: (streamError) => {
          if (!mounted) return;
          console.error("Conversation detail SSE error:", streamError);
        },
      },
      { signal: abortController.signal },
    );

    return () => {
      mounted = false;
      abortController.abort();
    };
  }, [activeId, resetDetail, t, updateSummary]);

  const selectedNodeMessages = React.useMemo<SelectedNodeMessage[]>(() => {
    if (!detail) return [];
    return detail.messages.map((node) => ({
      node,
      message: node.messages[node.selectIndex] ?? node.messages[0],
    }));
  }, [detail]);

  return {
    detail,
    detailLoading,
    detailError,
    selectedNodeMessages,
    resetDetail,
  };
}

function useDraftInputController({
  activeId,
  isHomeRoute,
  homeDraftId,
  setHomeDraftId,
  navigate,
  refreshList,
}: {
  activeId: string | null;
  isHomeRoute: boolean;
  homeDraftId: string;
  setHomeDraftId: React.Dispatch<React.SetStateAction<string>>;
  navigate: ReturnType<typeof useNavigate>;
  refreshList: () => void;
}) {
  const draftKey = activeId ?? (isHomeRoute ? homeDraftId : null);
  const draft = useChatInputStore(
    React.useCallback((state) => (draftKey ? state.drafts[draftKey] : undefined), [draftKey]),
  );

  const setDraftText = useChatInputStore((state) => state.setText);
  const addDraftParts = useChatInputStore((state) => state.addParts);
  const removeDraftPart = useChatInputStore((state) => state.removePartAt);
  const getSubmitParts = useChatInputStore((state) => state.getSubmitParts);
  const clearDraft = useChatInputStore((state) => state.clearDraft);

  const inputText = draft?.text ?? "";
  const inputAttachments = draft?.parts ?? EMPTY_INPUT_ATTACHMENTS;

  const handleInputTextChange = React.useCallback(
    (text: string) => {
      if (!draftKey) return;
      setDraftText(draftKey, text);
    },
    [draftKey, setDraftText],
  );

  const handleAddInputParts = React.useCallback(
    (parts: UIMessagePart[]) => {
      if (!draftKey || parts.length === 0) return;
      addDraftParts(draftKey, parts);
    },
    [addDraftParts, draftKey],
  );

  const handleRemoveInputPart = React.useCallback(
    (index: number) => {
      if (!draftKey) return;
      removeDraftPart(draftKey, index);
    },
    [draftKey, removeDraftPart],
  );

  const submitCurrentDraft = React.useCallback(
    async (options?: { beforeSend?: (conversationId: string) => Promise<void> }) => {
      if (!draftKey) return;

      const parts = getSubmitParts(draftKey);
      if (parts.length === 0) return;

      if (activeId) {
        await options?.beforeSend?.(activeId);
        await api.post<{ status: string }>(`conversations/${activeId}/messages`, { parts });
        clearDraft(draftKey);
        return activeId;
      }

      const response = await api.post<{ id: string; assistantId: string }>("conversations", {});
      const conversationId = response.id;
      setHomeDraftId(createHomeDraftId());

      await options?.beforeSend?.(conversationId);
      await api.post<{ status: string }>(`conversations/${conversationId}/messages`, { parts });
      clearDraft(draftKey);

      navigate(`/c/${conversationId}`);
      refreshList();
      return conversationId;
    },
    [activeId, clearDraft, draftKey, getSubmitParts, navigate, refreshList, setHomeDraftId],
  );

  const handleSubmit = React.useCallback(async () => {
    await submitCurrentDraft();
  }, [submitCurrentDraft]);

  const replaceDraft = React.useCallback(
    (text: string, parts: UIMessagePart[]) => {
      if (!draftKey) return;
      clearDraft(draftKey);
      setDraftText(draftKey, text);
      addDraftParts(draftKey, parts);
    },
    [addDraftParts, clearDraft, draftKey, setDraftText],
  );

  const clearCurrentDraft = React.useCallback(() => {
    if (!draftKey) return;
    clearDraft(draftKey);
  }, [clearDraft, draftKey]);

  const getCurrentSubmitParts = React.useCallback(() => {
    if (!draftKey) return [];
    return getSubmitParts(draftKey);
  }, [draftKey, getSubmitParts]);

  return {
    draftKey,
    inputText,
    inputAttachments,
    handleInputTextChange,
    handleAddInputParts,
    handleRemoveInputPart,
    handleSubmit,
    submitCurrentDraft,
    replaceDraft,
    clearCurrentDraft,
    getCurrentSubmitParts,
  };
}

const ConversationTimeline = React.memo(
  ({
    activeId,
    isHomeRoute,
    detailLoading,
    detailError,
    selectedNodeMessages,
    isGenerating,
    settings,
    conversationAssistantId,
    displaySetting,
    contentClassName,
    onEdit,
    onDelete,
    onFork,
    onRegenerate,
    onSelectBranch,
    onToolApproval,
  }: {
    activeId: string | null;
    isHomeRoute: boolean;
    detailLoading: boolean;
    detailError: string | null;
    selectedNodeMessages: SelectedNodeMessage[];
    isGenerating: boolean;
    settings: Settings | null;
    conversationAssistantId: string | null;
    displaySetting: Settings["displaySetting"] | null;
    contentClassName?: string;
    onEdit: (message: MessageDto) => void | Promise<void>;
    onDelete: (messageId: string) => Promise<void>;
    onFork: (messageId: string) => Promise<void>;
    onRegenerate: (messageId: string) => Promise<void>;
    onSelectBranch: (nodeId: string, selectIndex: number) => Promise<void>;
    onToolApproval: (
      toolCallId: string,
      approved: boolean,
      reason: string,
      answer?: string,
    ) => Promise<void>;
  }) => {
    const { t } = useTranslation("page");
    const reducedMotion = useChatReducedMotion();
    const assistant = React.useMemo(() => {
      if (!settings || !conversationAssistantId) return null;
      return settings.assistants.find((item) => item.id === conversationAssistantId) ?? null;
    }, [conversationAssistantId, settings]);
    const modelById = React.useMemo(() => {
      const map = new Map<string, ProviderModel>();
      if (!settings) return map;

      for (const provider of settings.providers) {
        for (const model of provider.models) {
          if (!map.has(model.id)) {
            map.set(model.id, model);
          }
        }
      }

      return map;
    }, [settings]);
    const displayTurns = React.useMemo(
      () => groupSelectedNodesIntoTurns(selectedNodeMessages),
      [selectedNodeMessages],
    );
    const canQuickJump =
      Boolean(activeId) && !detailLoading && !detailError && displayTurns.length > 1;
    const showTrailingTypingIndicator =
      Boolean(activeId) &&
      !detailLoading &&
      !detailError &&
      isGenerating &&
      (displayTurns.length === 0 || displayTurns[displayTurns.length - 1]?.kind !== "assistant");
    const [newlyAppendedTurnIds, setNewlyAppendedTurnIds] = React.useState<Set<string>>(new Set());
    const hasSeededTurnIdsRef = React.useRef(false);
    const seenTurnIdsRef = React.useRef<Set<string>>(new Set());

    React.useEffect(() => {
      hasSeededTurnIdsRef.current = false;
      seenTurnIdsRef.current = new Set();
      setNewlyAppendedTurnIds(new Set());
    }, [activeId]);

    React.useEffect(() => {
      const nextIds = displayTurns.map((turn) => turn.id);
      if (!hasSeededTurnIdsRef.current) {
        seenTurnIdsRef.current = new Set(nextIds);
        hasSeededTurnIdsRef.current = true;
        return;
      }

      const appendedIds = nextIds.filter((id) => !seenTurnIdsRef.current.has(id));
      seenTurnIdsRef.current = new Set(nextIds);
      if (appendedIds.length === 0) return;

      setNewlyAppendedTurnIds(new Set(appendedIds));
      const timeoutId = window.setTimeout(
        () => {
          setNewlyAppendedTurnIds((prev) => {
            const next = new Set(prev);
            appendedIds.forEach((id) => next.delete(id));
            return next;
          });
        },
        reducedMotion ? 20 : 700,
      );

      return () => window.clearTimeout(timeoutId);
    }, [displayTurns, reducedMotion]);
    const getTurnPreview = React.useCallback(
      (turn: ReturnType<typeof groupSelectedNodesIntoTurns>[number]) =>
        getQuickJumpPreview(
          turn.kind === "assistant"
            ? { ...turn.displayMessage, parts: turn.allParts, annotations: turn.annotations }
            : turn.message,
          t,
        ),
      [t],
    );

    return (
      <Conversation className="flex-1 min-h-0">
        <ConversationContent
          className={cn(
            CHAT_COLUMN_CLASSNAME,
            CHAT_PAGE_PADDING_CLASSNAME,
            "gap-6 py-8",
            contentClassName,
          )}
        >
          {!activeId && !isHomeRoute && (
            <ConversationEmptyState
              icon={<MessageSquare className="size-10" />}
              title={t("conversations.empty_state.select_title")}
              description={t("conversations.empty_state.select_description")}
            />
          )}
          {activeId && detailLoading && (
            <ConversationEmptyState
              title={t("conversations.empty_state.loading_title")}
              description={t("conversations.empty_state.loading_description")}
            />
          )}
          {activeId && detailError && (
            <ConversationEmptyState
              title={t("conversations.empty_state.error_title")}
              description={detailError}
            />
          )}
          {!detailLoading &&
            !detailError &&
            activeId &&
            displayTurns.length === 0 &&
            !isHomeRoute && (
              <ConversationEmptyState
                icon={<MessageSquare className="size-10" />}
                title={t("conversations.empty_state.no_message_title")}
                description={t("conversations.empty_state.no_message_description")}
              />
            )}
          {!detailLoading &&
            !detailError &&
            (activeId || isHomeRoute) &&
            displayTurns.map((turn, index) => {
              const message = turn.kind === "assistant" ? turn.displayMessage : turn.message;
              const model = message.modelId ? (modelById.get(message.modelId) ?? null) : null;
              const turnLoading =
                isGenerating && index === displayTurns.length - 1 && turn.kind === "assistant";
              const shouldAnimateOnMount = newlyAppendedTurnIds.has(turn.id);

              return (
                <motion.div
                  key={turn.id}
                  layout={turnLoading ? false : "position"}
                  id={getConversationMessageAnchorId(turn.anchorMessageId)}
                  className="scroll-mt-24"
                  variants={getChatLiftVariants(reducedMotion, 12)}
                  initial={shouldAnimateOnMount ? "initial" : false}
                  animate="animate"
                  exit="exit"
                  transition={getChatLayoutTransition(reducedMotion)}
                >
                  {turn.kind === "assistant" ? (
                    <AssistantTurnMessage
                      turn={turn}
                      loading={turnLoading}
                      displaySetting={displaySetting}
                      assistant={assistant}
                      model={model}
                      onEdit={onEdit}
                      onDelete={onDelete}
                      onFork={onFork}
                      onRegenerate={onRegenerate}
                      onSelectBranch={onSelectBranch}
                      onToolApproval={onToolApproval}
                    />
                  ) : (
                    <ChatMessage
                      node={turn.node}
                      message={turn.message}
                      loading={false}
                      isLastMessage={index === displayTurns.length - 1}
                      displaySetting={displaySetting}
                      assistant={assistant}
                      model={model}
                      onEdit={onEdit}
                      onDelete={onDelete}
                      onFork={onFork}
                      onRegenerate={onRegenerate}
                      onSelectBranch={onSelectBranch}
                      onToolApproval={onToolApproval}
                    />
                  )}
                </motion.div>
              );
            })}
          <AnimatePresence initial={false}>
            {showTrailingTypingIndicator ? (
              <motion.div
                key="trailing-typing-indicator"
                layout="position"
                initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: 10, scale: 0.985 }}
                animate={{
                  opacity: 1,
                  y: 0,
                  scale: 1,
                  transition: reducedMotion
                    ? { duration: 0.01 }
                    : {
                        opacity: { duration: 0.16, ease: "easeOut" },
                        y: getChatLayoutTransition(false),
                        scale: getChatLayoutTransition(false),
                      },
                }}
                exit={
                  reducedMotion
                    ? { opacity: 0 }
                    : { opacity: 0, y: 8, transition: { duration: 0.12 } }
                }
                className="flex items-start py-2"
              >
                <TypingIndicator className="py-2" />
              </motion.div>
            ) : null}
          </AnimatePresence>
        </ConversationContent>

        {canQuickJump ? (
          <ConversationQuickJump
            items={displayTurns.map((turn) => ({
              id: turn.anchorMessageId,
              role: turn.kind === "assistant" ? turn.displayMessage.role : turn.message.role,
              preview: getTurnPreview(turn),
            }))}
          />
        ) : null}

        <ConversationScrollButton />
      </Conversation>
    );
  },
);

export function meta() {
  return [
    { title: i18n.t("page:conversations.meta.title") },
    {
      name: "description",
      content: i18n.t("page:conversations.meta.description"),
    },
  ];
}

export default function ConversationsPage() {
  const workbench = useWorkbenchController();

  return (
    <WorkbenchProvider value={workbench}>
      <ConversationsPageInner />
    </WorkbenchProvider>
  );
}

function ConversationsPageInner() {
  const { t } = useTranslation("page");
  const navigate = useNavigate();
  const { id: routeId } = useParams();
  const isMobile = useIsMobile();
  const { panel, closePanel } = useWorkbench();

  const { settings, assistants, currentAssistantId } = useCurrentAssistant();
  const [switchingAssistantId, setSwitchingAssistantId] = React.useState<string | null>(null);
  const effectiveCurrentAssistantId = switchingAssistantId ?? currentAssistantId;
  const isAssistantSwitching =
    switchingAssistantId !== null && switchingAssistantId !== currentAssistantId;
  const effectiveRouteId = isAssistantSwitching ? null : (routeId ?? null);
  const isHomeRoute = !effectiveRouteId;
  const {
    conversations,
    activeId,
    setActiveId,
    loading,
    error,
    hasMore,
    loadMore,
    refreshList,
    updateConversationSummary,
  } = useConversationList({
    currentAssistantId,
    routeId: effectiveRouteId,
    autoSelectFirst: !isHomeRoute,
  });

  const [homeDraftId, setHomeDraftId] = React.useState(() => createHomeDraftId());
  const [editingSession, setEditingSession] = React.useState<EditingSession | null>(null);

  const {
    detail,
    detailLoading,
    detailError,
    selectedNodeMessages: actualSelectedNodeMessages,
    resetDetail,
  } = useConversationDetail(activeId, updateConversationSummary);

  React.useEffect(() => {
    if (switchingAssistantId !== null && switchingAssistantId === currentAssistantId) {
      setSwitchingAssistantId(null);
    }
  }, [currentAssistantId, switchingAssistantId]);

  const selectedNodeMessages = React.useMemo(() => {
    if (activeId || !settings || !effectiveCurrentAssistantId) return actualSelectedNodeMessages;
    const assistant = settings.assistants.find((a) => a.id === effectiveCurrentAssistantId);
    const presets = assistant?.presetMessages;
    if (!presets || presets.length === 0) return actualSelectedNodeMessages;
    return presets.map((msg, index) => ({
      node: { id: `preset-${index}`, messages: [msg], selectIndex: 0 } as MessageNodeDto,
      message: msg,
    }));
  }, [activeId, settings, effectiveCurrentAssistantId, actualSelectedNodeMessages]);

  const {
    draftKey,
    inputText,
    inputAttachments,
    handleInputTextChange,
    handleAddInputParts,
    handleRemoveInputPart,
    handleSubmit,
    submitCurrentDraft,
    replaceDraft,
    clearCurrentDraft,
    getCurrentSubmitParts,
  } = useDraftInputController({
    activeId,
    isHomeRoute,
    homeDraftId,
    setHomeDraftId,
    navigate,
    refreshList,
  });

  const chatSuggestions = detail?.chatSuggestions ?? EMPTY_SUGGESTIONS;
  const conversationAssistant = React.useMemo(() => {
    const assistantId = detail?.assistantId ?? effectiveCurrentAssistantId;
    if (!settings || !assistantId) {
      return null;
    }
    return settings.assistants.find((assistant) => assistant.id === assistantId) ?? null;
  }, [detail?.assistantId, effectiveCurrentAssistantId, settings]);
  const effectiveDisplaySetting = React.useMemo(
    () => resolveEffectiveDisplaySetting(settings?.displaySetting, conversationAssistant),
    [conversationAssistant, settings?.displaySetting],
  );
  const pendingQuestionnaire = React.useMemo(() => {
    for (const item of selectedNodeMessages) {
      for (const part of item.message.parts) {
        if (
          part.type === "tool" &&
          part.toolName === TOOL_NAMES.ASK_USER &&
          part.approvalState.type === "pending"
        ) {
          const questions = parseAskUserQuestions(safeJsonParse(part.input));
          if (questions.length > 0) {
            return {
              toolCallId: part.toolCallId,
              questions,
            };
          }
        }
      }
    }

    return null;
  }, [selectedNodeMessages]);

  React.useEffect(() => {
    const base = t("conversations.meta.title");
    document.title = detail?.title ? `${detail.title} - ${base}` : base;
    return () => {
      document.title = base;
    };
  }, [detail?.title, t]);
  const isNewChat = isHomeRoute && !activeId;
  const showSuggestions =
    Boolean(activeId) &&
    !detailLoading &&
    !detailError &&
    chatSuggestions.length > 0 &&
    !pendingQuestionnaire;
  const displaySuggestions = showSuggestions ? chatSuggestions : EMPTY_SUGGESTIONS;

  const handleSelect = React.useCallback(
    (id: string) => {
      setActiveId(id);
      if (routeId !== id) {
        navigate(`/c/${id}`);
      }
    },
    [navigate, routeId, setActiveId],
  );

  React.useEffect(() => {
    setEditingSession(null);
  }, [activeId]);

  const handleAssistantChange = React.useCallback(
    async (assistantId: string) => {
      setSwitchingAssistantId(assistantId);
      setActiveId(null);
      resetDetail();
      setHomeDraftId(createHomeDraftId());
      if (routeId) {
        navigate("/", { replace: true });
      }
      try {
        await api.post<{ status: string }>("settings/assistant", { assistantId });
        refreshList();
      } catch (error) {
        setSwitchingAssistantId(null);
        throw error;
      }
    },
    [navigate, refreshList, resetDetail, routeId, setActiveId, setHomeDraftId],
  );

  const handleToolApproval = React.useCallback(
    async (toolCallId: string, approved: boolean, reason: string, answer?: string) => {
      if (!activeId) return;
      await api.post<{ status: string }>(`conversations/${activeId}/tool-approval`, {
        toolCallId,
        approved,
        reason,
        ...(answer != null ? { answer } : {}),
      });
    },
    [activeId],
  );

  const handleRegenerate = React.useCallback(
    async (messageId: string) => {
      if (!activeId) return;
      await api.post<{ status: string }>(`conversations/${activeId}/regenerate`, {
        messageId,
      });
    },
    [activeId],
  );

  const handleSelectBranch = React.useCallback(
    async (nodeId: string, selectIndex: number) => {
      if (!activeId) return;
      await api.post<{ status: string }>(`conversations/${activeId}/nodes/${nodeId}/select`, {
        selectIndex,
      });
    },
    [activeId],
  );

  const handleDeleteMessage = React.useCallback(
    async (messageId: string) => {
      if (!activeId) return;
      await api.delete<{ status: string }>(`conversations/${activeId}/messages/${messageId}`);
    },
    [activeId],
  );

  const handleForkMessage = React.useCallback(
    async (messageId: string) => {
      if (!activeId) return;
      const response = await api.post<{ conversationId: string }>(
        `conversations/${activeId}/fork`,
        {
          messageId,
        },
      );
      setActiveId(response.conversationId);
      navigate(`/c/${response.conversationId}`);
      refreshList();
    },
    [activeId, navigate, refreshList, setActiveId],
  );

  const handleStartEdit = React.useCallback(
    (message: MessageDto) => {
      if (!activeId || (message.role !== "USER" && message.role !== "ASSISTANT")) return;

      const draft = toEditDraft(message);
      if (!draft) return;

      setEditingSession({
        messageId: message.id,
        sourceParts: draft.sourceParts,
        textPartIndex: draft.textPartIndex,
      });
      replaceDraft(draft.text, draft.attachments);
    },
    [activeId, replaceDraft],
  );

  const handleCancelEdit = React.useCallback(() => {
    setEditingSession(null);
    clearCurrentDraft();
  }, [clearCurrentDraft]);

  const handleClickSuggestion = React.useCallback(
    (suggestion: string) => {
      if (editingSession) {
        setEditingSession(null);
      }
      handleInputTextChange(suggestion);
    },
    [editingSession, handleInputTextChange],
  );

  const handleSend = React.useCallback(async () => {
    if (!editingSession) {
      await handleSubmit();
      return;
    }

    if (!activeId) return;

    const draftParts = getCurrentSubmitParts();
    if (draftParts.length === 0) return;

    const nextParts = buildEditedParts(editingSession, draftParts);

    await api.post<{ status: string }>(
      `conversations/${activeId}/messages/${editingSession.messageId}/edit`,
      { parts: stripEditDraftMetadata(nextParts) },
    );

    setEditingSession(null);
    clearCurrentDraft();
  }, [
    activeId,
    clearCurrentDraft,
    detail?.enabledSkillIds,
    editingSession,
    getCurrentSubmitParts,
    handleSubmit,
  ]);

  const handleTogglePinConversation = React.useCallback(
    async (conversationId: string) => {
      await api.post<{ status: string }>(`conversations/${conversationId}/pin`);
      refreshList();
    },
    [refreshList],
  );

  const handleRegenerateConversationTitle = React.useCallback(
    async (conversationId: string) => {
      await api.post<{ status: string }>(`conversations/${conversationId}/regenerate-title`);
      refreshList();
    },
    [refreshList],
  );

  const handleMoveConversation = React.useCallback(
    async (conversationId: string, assistantId: string) => {
      await api.post<{ status: string }>(`conversations/${conversationId}/move`, { assistantId });
      if (conversationId === activeId) {
        setActiveId(null);
        resetDetail();
        setHomeDraftId(createHomeDraftId());
        if (routeId === conversationId) {
          navigate("/", { replace: true });
        }
      }
      refreshList();
    },
    [activeId, navigate, refreshList, resetDetail, routeId, setActiveId],
  );

  const handleUpdateConversationTitle = React.useCallback(
    async (conversationId: string, title: string) => {
      await api.post<{ status: string }>(`conversations/${conversationId}/title`, { title });
      refreshList();
    },
    [refreshList],
  );

  const handleConsolidateConversation = React.useCallback(
    async (conversationId: string) => {
      await api.post<{ status: string }>(`conversations/${conversationId}/consolidate`);
      refreshList();
    },
    [refreshList],
  );

  const handleRefreshConversationContext = React.useCallback(
    async (conversationId: string) => {
      const result = await api.post<ContextRefreshResponseDto>(
        `conversations/${conversationId}/context-refresh`,
      );
      if (!result.success) {
        throw new Error(result.error ?? t("conversation_sidebar.context_refresh_failed"));
      }
      toast.success(
        result.messagesSummarized > 0
          ? t("conversation_sidebar.context_refresh_success_count", {
              count: result.messagesSummarized,
            })
          : t("conversation_sidebar.context_refresh_success"),
      );
      refreshList();
    },
    [refreshList, t],
  );

  const handleDeleteConversation = React.useCallback(
    async (conversationId: string) => {
      await api.delete<Record<string, never>>(`conversations/${conversationId}`, {
        parseJson: (raw) => (raw ? JSON.parse(raw) : {}),
      });
      if (conversationId === activeId) {
        setActiveId(null);
        resetDetail();
        setHomeDraftId(createHomeDraftId());
        if (routeId === conversationId) {
          navigate("/", { replace: true });
        }
      }
      refreshList();
    },
    [activeId, navigate, refreshList, resetDetail, routeId, setActiveId],
  );

  const handleCreateConversation = React.useCallback(() => {
    closePanel();
    setActiveId(null);
    resetDetail();
    setHomeDraftId(createHomeDraftId());

    if (routeId) {
      navigate("/");
    }
  }, [closePanel, navigate, resetDetail, routeId, setActiveId]);

  const handleStop = React.useCallback(async () => {
    if (!activeId) return;
    await api.post<{ status: string }>(`conversations/${activeId}/stop`);
  }, [activeId]);

  const hasWorkbenchPanel = Boolean(panel);
  const workbenchPanelRef = React.useRef<PanelImperativeHandle | null>(null);

  React.useEffect(() => {
    if (isMobile) return;

    const workbenchPanel = workbenchPanelRef.current;
    if (!workbenchPanel) return;

    if (hasWorkbenchPanel) {
      workbenchPanel.expand();
      if (panel?.preferredDesktopSize != null) {
        workbenchPanel.resize(panel.preferredDesktopSize);
      }
    } else {
      workbenchPanel.collapse();
    }
  }, [hasWorkbenchPanel, isMobile, panel]);

  const chatContent = (
    <div
      className={cn(
        "flex min-h-0 flex-1 flex-col overflow-hidden bg-background pt-12",
        isNewChat && selectedNodeMessages.length === 0 && "justify-center",
      )}
    >
      {(!isNewChat || selectedNodeMessages.length > 0) && (
        <div className="relative flex min-h-0 flex-1">
          <ConversationTimeline
            activeId={activeId}
            isHomeRoute={isHomeRoute}
            detailLoading={detailLoading}
            detailError={detailError}
            selectedNodeMessages={selectedNodeMessages}
            isGenerating={detail?.isGenerating ?? false}
            settings={settings}
            conversationAssistantId={detail?.assistantId ?? effectiveCurrentAssistantId}
            displaySetting={effectiveDisplaySetting}
            onEdit={handleStartEdit}
            onDelete={handleDeleteMessage}
            onFork={handleForkMessage}
            onRegenerate={handleRegenerate}
            onSelectBranch={handleSelectBranch}
            onToolApproval={handleToolApproval}
          />
        </div>
      )}

      <div className={cn("relative z-10 pb-3 sm:pb-3.5", CHAT_PAGE_PADDING_CLASSNAME)}>
        {isNewChat && selectedNodeMessages.length === 0 && (
          <div className="mx-auto mb-6 max-w-2xl text-center">
            <p className="text-lg text-muted-foreground">
              <ConversationGreeting />
            </p>
          </div>
        )}
        <ChatInput
          value={inputText}
          attachments={inputAttachments}
          ready={draftKey !== null}
          isGenerating={detail?.isGenerating ?? false}
          disabled={detailLoading || Boolean(detailError)}
          assistantId={detail?.assistantId ?? effectiveCurrentAssistantId}
          conversationId={activeId}
          conversationSkillIds={detail?.enabledSkillIds ?? null}
          pendingQuestionnaire={pendingQuestionnaire}
          onValueChange={handleInputTextChange}
          onAddParts={handleAddInputParts}
          suggestions={displaySuggestions}
          onSuggestionClick={handleClickSuggestion}
          isEditing={Boolean(editingSession)}
          onCancelEdit={editingSession ? handleCancelEdit : undefined}
          shouldDeleteFileOnRemove={shouldDeleteAttachmentFileOnRemove}
          onRemovePart={handleRemoveInputPart}
          onSend={handleSend}
          onToolApproval={handleToolApproval}
          onStop={activeId ? handleStop : undefined}
          onExportConversation={
            detail && detail.messages.length > 0
              ? (includeReasoning: boolean) => {
                  const content = convertConversationToMarkdown(detail, includeReasoning);
                  const filename = `${detail.title || "conversation"}.md`;
                  downloadMarkdown(content, filename);
                }
              : undefined
          }
        />
      </div>
    </div>
  );

  return (
    <SidebarProvider defaultOpen className="h-svh overflow-hidden">
      <ConversationSidebar
        conversations={isAssistantSwitching ? [] : conversations}
        activeId={isAssistantSwitching ? null : activeId}
        loading={loading || isAssistantSwitching}
        error={isAssistantSwitching ? null : error}
        hasMore={hasMore}
        loadMore={loadMore}
        userName={
          settings?.displaySetting.userNickname?.trim() || t("conversations.user.default_name")
        }
        userAvatar={settings?.displaySetting.userAvatar}
        assistants={assistants}
        assistantTags={settings?.assistantTags ?? []}
        currentAssistantId={effectiveCurrentAssistantId}
        onSelect={handleSelect}
        onAssistantChange={handleAssistantChange}
        onPin={handleTogglePinConversation}
        onRegenerateTitle={handleRegenerateConversationTitle}
        onMoveToAssistant={handleMoveConversation}
        onUpdateTitle={handleUpdateConversationTitle}
        onDelete={handleDeleteConversation}
        onConsolidate={handleConsolidateConversation}
        onRefreshContext={handleRefreshConversationContext}
        onCreateConversation={handleCreateConversation}
        webAuthEnabled={settings?.webServerJwtEnabled === true}
      />
      <SidebarInset className="flex min-h-svh flex-col">
        <div className="pointer-events-none absolute top-1.5 left-1.5 z-20">
          <SidebarTrigger className="pointer-events-auto rounded-full border border-border/70 bg-background/90 text-foreground shadow-sm backdrop-blur hover:bg-accent" />
        </div>

        {!isMobile ? (
          <ResizablePanelGroup orientation="horizontal" className="min-h-0 flex-1 bg-background">
            <ResizablePanel
              defaultSize={hasWorkbenchPanel ? 64 : 100}
              minSize={40}
              className="flex min-h-0 flex-col"
            >
              {chatContent}
            </ResizablePanel>
            <ResizableHandle
              withHandle
              className={cn(!hasWorkbenchPanel && "pointer-events-none opacity-0")}
            />
            <ResizablePanel
              defaultSize={hasWorkbenchPanel ? 36 : 0}
              minSize={24}
              collapsible
              collapsedSize={0}
              panelRef={workbenchPanelRef}
              className="flex min-h-0 flex-col"
            >
              {panel ? (
                <React.Suspense
                  fallback={<WorkbenchHostFallback className="border-l border-border/60" />}
                >
                  <LazyWorkbenchHost panel={panel} onClose={closePanel} className="border-l-0" />
                </React.Suspense>
              ) : null}
            </ResizablePanel>
          </ResizablePanelGroup>
        ) : (
          chatContent
        )}

        {isMobile && panel ? (
          <Drawer
            open={hasWorkbenchPanel}
            onOpenChange={(open) => {
              if (!open) {
                closePanel();
              }
            }}
            direction="bottom"
          >
            <DrawerContent className="h-[85vh] max-h-[85vh]">
              <React.Suspense fallback={<WorkbenchHostFallback />}>
                <LazyWorkbenchHost panel={panel} onClose={closePanel} className="border-l-0" />
              </React.Suspense>
            </DrawerContent>
          </Drawer>
        ) : null}
      </SidebarInset>
    </SidebarProvider>
  );
}
