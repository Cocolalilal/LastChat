import * as React from "react";
import { AnimatePresence, motion } from "motion/react";

import { fileTypeFromBuffer } from "file-type";
import {
  AudioFile,
  ArrowUp,
  ChevronLeft,
  ChevronRight,
  File,
  FileDown,
  FlashOn,
  FolderOpen,
  Image,
  LoaderCircle,
  Plus,
  Square,
  Video,
  X,
} from "~/lib/material-icons";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { useCurrentModel } from "~/hooks/use-current-model";
import { ModelList } from "~/components/input/model-list";
import { ReasoningPickerButton } from "~/components/input/reasoning-picker";
import { SearchPickerButton } from "~/components/input/search-picker";
import { InjectionPickerButton } from "~/components/input/injection-picker";
import type { AskUserOption, AskUserQuestion } from "~/lib/tool-activity";
import { useSettingsStore } from "~/stores";
import { Button } from "~/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "~/components/ui/dropdown-menu";
import { Textarea } from "~/components/ui/textarea";
import { resolveFileUrl } from "~/lib/files";
import {
  CHAT_MOTION_DURATION,
  getChatLayoutTransition,
  getChatTactileTransition,
  useChatReducedMotion,
} from "~/lib/chat-motion";
import { CHAT_COLUMN_CLASSNAME } from "~/lib/chat-layout";
import { cn } from "~/lib/utils";
import api from "~/services/api";
import type { UIMessagePart, UploadFilesResponseDto } from "~/types";

export interface ChatInputProps {
  value: string;
  attachments: UIMessagePart[];
  suggestions?: string[];
  ready?: boolean;
  disabled?: boolean;
  isGenerating?: boolean;
  isEditing?: boolean;
  assistantId?: string | null;
  conversationId?: string | null;
  conversationSkillIds?: string[] | null;
  pendingQuestionnaire?: PendingQuestionnaire | null;
  onValueChange: (value: string) => void;
  onAddParts: (parts: UIMessagePart[]) => void;
  shouldDeleteFileOnRemove?: (part: UIMessagePart) => boolean;
  onRemovePart: (index: number, part: UIMessagePart) => Promise<void> | void;
  onSend: () => Promise<void> | void;
  onToolApproval?: (
    toolCallId: string,
    approved: boolean,
    reason: string,
    answer?: string,
  ) => Promise<void> | void;
  onStop?: () => Promise<void> | void;
  onCancelEdit?: () => void;
  onSuggestionClick?: (suggestion: string) => void;
  onExportConversation?: (includeReasoning: boolean) => void;
  className?: string;
}

const IMAGE_UPLOAD_ACCEPT = "image/*";
const COMPOSER_CONTROL_BUTTON_CLASSNAME =
  "h-9 rounded-full border border-border/70 bg-muted/70 text-foreground shadow-none hover:bg-accent hover:text-accent-foreground";
const COMPOSER_CHIP_CLASSNAME = "border border-border/70 bg-muted/65 text-foreground shadow-none";

export interface PendingQuestionnaire {
  toolCallId: string;
  questions: AskUserQuestion[];
}

async function isAllowedUploadFile(file: globalThis.File): Promise<boolean> {
  const buffer = await file.slice(0, 4100).arrayBuffer();
  const detected = await fileTypeFromBuffer(buffer);

  // 无法识别 magic bytes → 文本文件 → 允许
  if (!detected) return true;

  // 识别为图片 / 视频 / 音频 → 允许
  if (
    detected.mime.startsWith("image/") ||
    detected.mime.startsWith("video/") ||
    detected.mime.startsWith("audio/") ||
    detected.mime.startsWith("text/") ||
    detected.mime === "application/pdf" ||
    detected.mime === "application/epub+zip" ||
    detected.mime === "application/csv" ||
    detected.mime === "application/msword" ||
    detected.mime === "application/rtf" ||
    detected.mime.startsWith("application/vnd.openxmlformats-officedocument.") ||
    detected.mime.startsWith("application/vnd.ms-")
  ) {
    return true;
  }

  // 其他可识别的二进制格式（exe、zip 等）→ 拒绝
  return false;
}

function toMessagePart(file: UploadFilesResponseDto["files"][number]): UIMessagePart {
  if (file.mime.startsWith("image/")) {
    return {
      type: "image",
      url: file.url,
      metadata: { fileId: file.id },
    };
  }

  if (file.mime.startsWith("video/")) {
    return {
      type: "video",
      url: file.url,
      metadata: { fileId: file.id },
    };
  }

  if (file.mime.startsWith("audio/")) {
    return {
      type: "audio",
      url: file.url,
      metadata: { fileId: file.id },
    };
  }

  return {
    type: "document",
    url: file.url,
    fileName: file.fileName,
    mime: file.mime,
    metadata: { fileId: file.id },
  };
}

function partLabel(part: UIMessagePart, t: (key: string) => string): string {
  switch (part.type) {
    case "document":
      return part.fileName;
    case "image":
      return t("chat.attachment_image");
    case "video":
      return t("chat.attachment_video");
    case "audio":
      return t("chat.attachment_audio");
    default:
      return t("chat.attachment_file");
  }
}

function partIcon(part: UIMessagePart) {
  switch (part.type) {
    case "image":
      return <Image className="size-3.5" />;
    case "video":
      return <Video className="size-3.5" />;
    case "audio":
      return <AudioFile className="size-3.5" />;
    case "document":
      return <File className="size-3.5" />;
    default:
      return <File className="size-3.5" />;
  }
}

function getPartFileId(part: UIMessagePart): number | null {
  const value = part.metadata?.fileId;
  return typeof value === "number" ? value : null;
}

function hasFilesInDataTransfer(dataTransfer: DataTransfer | null): boolean {
  if (!dataTransfer) return false;
  if (dataTransfer.files.length > 0) return true;
  return Array.from(dataTransfer.items).some((item) => item.kind === "file");
}

function ChatInputInner({
  value,
  attachments,
  suggestions = [],
  ready = true,
  disabled = false,
  isGenerating = false,
  isEditing = false,
  assistantId = null,
  conversationId = null,
  conversationSkillIds = null,
  pendingQuestionnaire = null,
  onValueChange,
  onAddParts,
  shouldDeleteFileOnRemove,
  onRemovePart,
  onSend,
  onToolApproval,
  onStop,
  onCancelEdit,
  onSuggestionClick,
  onExportConversation,
  className,
}: ChatInputProps) {
  const { t } = useTranslation("input");
  const reducedMotion = useChatReducedMotion();
  const sendOnEnter = useSettingsStore(
    (state) => state.settings?.displaySetting.sendOnEnter ?? true,
  );
  const pasteLongTextAsFile = useSettingsStore(
    (state) => state.settings?.displaySetting.pasteLongTextAsFile ?? false,
  );
  const pasteLongTextThreshold = useSettingsStore(
    (state) => state.settings?.displaySetting.pasteLongTextThreshold ?? 1000,
  );
  const { currentAssistant } = useCurrentAssistant();
  const { currentModel } = useCurrentModel();

  const quickMessages = React.useMemo(() => {
    const source = currentAssistant?.quickMessages;
    if (!Array.isArray(source)) {
      return [] as QuickMessageOption[];
    }

    return source
      .map((item) => {
        const title = typeof item?.title === "string" ? item.title.trim() : "";
        const content = typeof item?.content === "string" ? item.content.trim() : "";
        if (!content) {
          return null;
        }

        return {
          title: title || t("chat.quick_message_default_title"),
          content,
        };
      })
      .filter((item): item is QuickMessageOption => item !== null);
  }, [currentAssistant?.quickMessages, t]);

  const imageInputRef = React.useRef<HTMLInputElement | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const [uploading, setUploading] = React.useState(false);
  const [uploadMenuOpen, setUploadMenuOpen] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [dragActive, setDragActive] = React.useState(false);
  const dragDepthRef = React.useRef(0);
  const questionnaireActive =
    Boolean(pendingQuestionnaire) && pendingQuestionnaire!.questions.length > 0;
  const [questionIndex, setQuestionIndex] = React.useState(0);
  const [selectedAnswers, setSelectedAnswers] = React.useState<Record<string, string>>({});
  const [customAnswers, setCustomAnswers] = React.useState<Record<string, string>>({});

  const isEmpty = value.trim().length === 0 && attachments.length === 0;
  const canStop = ready && Boolean(onStop) && isGenerating && !disabled;
  const canSend = ready && !isGenerating && !disabled && !isEmpty;
  const canUpload =
    ready && !disabled && !isGenerating && !uploading && !submitting && !questionnaireActive;
  const canSwitchModel =
    ready && !disabled && !isGenerating && !uploading && !submitting && !questionnaireActive;
  const canUseQuickMessage =
    ready && !disabled && !uploading && !submitting && !questionnaireActive;
  const actionDisabled = questionnaireActive
    ? !ready || disabled || uploading || submitting
    : submitting || uploading || (!canStop && !canSend);
  const activeQuestion = questionnaireActive
    ? pendingQuestionnaire!.questions[
        Math.min(questionIndex, pendingQuestionnaire!.questions.length - 1)
      ]
    : null;
  const isFinalQuestion = questionnaireActive
    ? questionIndex >= pendingQuestionnaire!.questions.length - 1
    : false;
  const questionnaireValue = activeQuestion ? (customAnswers[activeQuestion.id] ?? "") : "";
  const hasImageAttachment = attachments.some((part) => part.type === "image");
  const modelLacksImageInput =
    currentModel != null && !currentModel.inputModalities?.includes("IMAGE");
  const showNoImageInputHint = hasImageAttachment && modelLacksImageInput;

  React.useEffect(() => {
    setQuestionIndex(0);
    setSelectedAnswers({});
    setCustomAnswers({});
  }, [pendingQuestionnaire?.toolCallId]);

  React.useEffect(() => {
    if (!questionnaireActive || !pendingQuestionnaire) return;
    setQuestionIndex((current) => Math.min(current, pendingQuestionnaire.questions.length - 1));
  }, [pendingQuestionnaire, questionnaireActive]);

  React.useEffect(() => {
    if (!canUpload) {
      setUploadMenuOpen(false);
      setDragActive(false);
      dragDepthRef.current = 0;
    }
  }, [canUpload]);

  const uploadFiles = React.useCallback(
    async (fileList: FileList | globalThis.File[] | null) => {
      if (!ready || !fileList || fileList.length === 0) {
        return;
      }

      const allFiles = Array.from(fileList);
      const results = await Promise.all(
        allFiles.map(async (f) => ({ file: f, allowed: await isAllowedUploadFile(f) })),
      );
      const uploadableFiles = results.filter((r) => r.allowed).map((r) => r.file);
      const skippedFiles = results.filter((r) => !r.allowed).map((r) => r.file);

      if (skippedFiles.length > 0) {
        toast.warning(t("chat.unsupported_file_skipped", { count: skippedFiles.length }));
      }

      if (uploadableFiles.length === 0) {
        return;
      }

      const formData = new FormData();
      uploadableFiles.forEach((file) => {
        formData.append("files", file, file.name);
      });

      setUploading(true);
      setError(null);
      try {
        const response = await api.postMultipart<UploadFilesResponseDto>("files/upload", formData);
        const parts = response.files.map(toMessagePart);
        onAddParts(parts);
      } catch (uploadError) {
        const message =
          uploadError instanceof Error ? uploadError.message : t("chat.upload_failed");
        setError(message);
      } finally {
        setUploading(false);
      }
    },
    [onAddParts, ready, t],
  );

  const buildQuestionnairePayload = React.useCallback(
    (dismissed: boolean) => {
      if (!pendingQuestionnaire) {
        return null;
      }

      return JSON.stringify({
        answers: pendingQuestionnaire.questions.map((question) => {
          const customValue = (customAnswers[question.id] ?? "").trim();
          const selectedValue = (selectedAnswers[question.id] ?? "").trim();
          if (customValue) {
            return {
              id: question.id,
              status: "answered",
              source: "custom",
              value: customValue,
            };
          }
          if (selectedValue) {
            return {
              id: question.id,
              status: "answered",
              source: "option",
              value: selectedValue,
            };
          }
          return {
            id: question.id,
            status: "skipped",
          };
        }),
        dismissed,
      });
    },
    [customAnswers, pendingQuestionnaire, selectedAnswers],
  );

  const handleQuestionnaireAction = React.useCallback(
    async (dismissed = false) => {
      if (!pendingQuestionnaire || !onToolApproval) {
        return;
      }

      if (dismissed || isFinalQuestion) {
        const payload = buildQuestionnairePayload(dismissed);
        if (!payload) return;
        await onToolApproval(pendingQuestionnaire.toolCallId, true, "", payload);
        return;
      }

      setQuestionIndex((current) =>
        Math.min(current + 1, pendingQuestionnaire.questions.length - 1),
      );
    },
    [buildQuestionnairePayload, isFinalQuestion, onToolApproval, pendingQuestionnaire],
  );

  const handlePrimaryAction = React.useCallback(async () => {
    if (actionDisabled) {
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      if (questionnaireActive) {
        await handleQuestionnaireAction(false);
        return;
      }

      if (canStop) {
        await onStop?.();
        return;
      }

      if (canSend) {
        await onSend();
      }
    } catch (submitError) {
      const message = submitError instanceof Error ? submitError.message : t("chat.send_failed");
      setError(message);
    } finally {
      setSubmitting(false);
    }
  }, [
    actionDisabled,
    canSend,
    canStop,
    handleQuestionnaireAction,
    onSend,
    onStop,
    questionnaireActive,
    t,
  ]);

  const handleTextChange = React.useCallback(
    (event: React.ChangeEvent<HTMLTextAreaElement>) => {
      if (questionnaireActive && activeQuestion) {
        setCustomAnswers((prev) => ({ ...prev, [activeQuestion.id]: event.target.value }));
      } else {
        onValueChange(event.target.value);
      }
      if (error) {
        setError(null);
      }
    },
    [activeQuestion, error, onValueChange, questionnaireActive],
  );

  const handleQuickMessageSelect = React.useCallback(
    (content: string) => {
      if (!canUseQuickMessage || !content) {
        return;
      }

      const needLineBreak = value.length > 0 && !value.endsWith("\n");
      onValueChange(`${value}${needLineBreak ? "\n" : ""}${content}`);
      if (error) {
        setError(null);
      }
      textareaRef.current?.focus();
    },
    [canUseQuickMessage, error, onValueChange, value],
  );

  const handleSuggestionSelect = React.useCallback(
    (suggestion: string) => {
      if (!canUseQuickMessage || !suggestion) {
        return;
      }

      onSuggestionClick?.(suggestion);
      if (error) {
        setError(null);
      }
      textareaRef.current?.focus();
    },
    [canUseQuickMessage, error, onSuggestionClick],
  );

  const handleKeyDown = React.useCallback(
    (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key !== "Enter") return;
      if (isGenerating) return;
      if (event.nativeEvent.isComposing) return;

      // 镜像逻辑：
      // sendOnEnter = true: Enter 发送，Shift+Enter 换行
      // sendOnEnter = false: Shift+Enter 发送，Enter 换行
      const shouldSend = sendOnEnter ? !event.shiftKey : event.shiftKey;
      if (!shouldSend) return;

      event.preventDefault();
      void handlePrimaryAction();
    },
    [handlePrimaryAction, isGenerating, sendOnEnter],
  );

  const handleUploadInputChange = React.useCallback(
    async (event: React.ChangeEvent<HTMLInputElement>) => {
      await uploadFiles(event.target.files);
      event.currentTarget.value = "";
    },
    [uploadFiles],
  );

  const handlePaste = React.useCallback(
    async (event: React.ClipboardEvent<HTMLTextAreaElement>) => {
      if (!canUpload) return;

      // 粘贴长文本自动转换为文件
      if (pasteLongTextAsFile) {
        const text = event.clipboardData.getData("text/plain");
        if (text.length > pasteLongTextThreshold) {
          event.preventDefault();
          const file = new globalThis.File([text], "pasted_text.txt", {
            type: "text/plain",
          });
          toast.info(t("chat.long_text_as_file"));
          void uploadFiles([file]);
          return;
        }
      }

      const uploadableFiles = (
        await Promise.all(
          Array.from(event.clipboardData.items)
            .filter((item) => item.kind === "file")
            .map((item) => item.getAsFile())
            .filter((file): file is globalThis.File => file !== null)
            .map(async (file) => ({ file, allowed: await isAllowedUploadFile(file) })),
        )
      )
        .filter((r) => r.allowed)
        .map((r) => r.file);

      if (uploadableFiles.length === 0) {
        return;
      }

      event.preventDefault();
      void uploadFiles(uploadableFiles);
    },
    [canUpload, pasteLongTextAsFile, pasteLongTextThreshold, t, uploadFiles],
  );

  const handleDragEnter = React.useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      if (!canUpload || !hasFilesInDataTransfer(event.dataTransfer)) return;
      event.preventDefault();
      event.stopPropagation();
      dragDepthRef.current += 1;
      setDragActive(true);
    },
    [canUpload],
  );

  const handleDragOver = React.useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      if (!canUpload || !hasFilesInDataTransfer(event.dataTransfer)) return;
      event.preventDefault();
      event.stopPropagation();
      event.dataTransfer.dropEffect = "copy";
      if (!dragActive) {
        setDragActive(true);
      }
    },
    [canUpload, dragActive],
  );

  const handleDragLeave = React.useCallback((event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.stopPropagation();
    dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
    if (dragDepthRef.current === 0) {
      setDragActive(false);
    }
  }, []);

  const handleDrop = React.useCallback(
    async (event: React.DragEvent<HTMLDivElement>) => {
      if (!hasFilesInDataTransfer(event.dataTransfer)) return;
      event.preventDefault();
      event.stopPropagation();
      dragDepthRef.current = 0;
      setDragActive(false);
      if (!canUpload) return;
      await uploadFiles(event.dataTransfer.files);
    },
    [canUpload, uploadFiles],
  );

  const sendHint = sendOnEnter ? t("chat.send_hint_enter") : t("chat.send_hint_newline");
  const placeholder = questionnaireActive
    ? t("chat.questionnaire_placeholder")
    : ready
      ? t("chat.placeholder_ready")
      : t("chat.placeholder_not_ready");

  return (
    <div className={cn("bg-transparent", className)}>
      <div className={CHAT_COLUMN_CLASSNAME}>
        <motion.div
          layout
          className={cn(
            "relative flex min-h-12 flex-col gap-1.5 rounded-[var(--radius-input-capsule)] border border-border/80 bg-card/92 px-2 py-1.5 shadow-sm backdrop-blur-xl transition-shadow focus-within:border-ring/45 focus-within:shadow-md focus-within:ring-1 focus-within:ring-ring/25 sm:px-2.5 sm:py-2",
            dragActive && "border-primary/40 bg-primary/5 ring-2 ring-primary/20",
          )}
          transition={getChatLayoutTransition(reducedMotion)}
          onDragEnter={handleDragEnter}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={(event) => {
            void handleDrop(event);
          }}
        >
          <AnimatePresence initial={false}>
            {dragActive ? (
              <motion.div
                key="drag-upload-overlay"
                initial={reducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.985 }}
                animate={{
                  opacity: 1,
                  scale: 1,
                  transition: reducedMotion
                    ? { duration: 0.01 }
                    : {
                        opacity: { duration: CHAT_MOTION_DURATION.fast, ease: "easeOut" },
                        scale: getChatTactileTransition(false),
                      },
                }}
                exit={
                  reducedMotion
                    ? { opacity: 0 }
                    : { opacity: 0, scale: 0.985, transition: { duration: 0.12 } }
                }
                className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center rounded-[calc(var(--radius-bubble)-2px)] border-2 border-dashed border-primary/50 bg-background/80 px-4 text-center text-sm font-medium text-primary"
              >
                {t("chat.drop_to_upload")}
              </motion.div>
            ) : null}
          </AnimatePresence>

          <AnimatePresence initial={false}>
            {isEditing ? (
              <motion.div
                key="editing-banner"
                layout
                initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: -8 }}
                animate={{
                  opacity: 1,
                  y: 0,
                  transition: reducedMotion
                    ? { duration: 0.01 }
                    : {
                        opacity: { duration: CHAT_MOTION_DURATION.fast, ease: "easeOut" },
                        y: getChatLayoutTransition(false),
                      },
                }}
                exit={
                  reducedMotion
                    ? { opacity: 0 }
                    : { opacity: 0, y: -6, transition: { duration: 0.12 } }
                }
                className="mx-1 flex items-center justify-between rounded-[var(--radius-input-inner)] border border-border/70 bg-secondary/60 px-2.5 py-1.5 text-xs"
              >
                <span className="text-primary">{t("chat.editing_tip")}</span>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="h-6 px-2 text-xs"
                  onClick={onCancelEdit}
                  disabled={submitting || uploading}
                >
                  {t("chat.cancel_edit")}
                </Button>
              </motion.div>
            ) : null}
          </AnimatePresence>

          <AnimatePresence initial={false}>
            {questionnaireActive && pendingQuestionnaire ? (
              <motion.div
                key="questionnaire-panel"
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
                className="overflow-hidden rounded-[var(--radius-card-inner)] border border-border/70 bg-secondary/50"
              >
                <QuestionnairePanel
                  question={activeQuestion}
                  currentIndex={questionIndex}
                  totalQuestions={pendingQuestionnaire.questions.length}
                  selectedValue={
                    activeQuestion ? (selectedAnswers[activeQuestion.id] ?? null) : null
                  }
                  onPrevious={() => {
                    setQuestionIndex((current) => Math.max(0, current - 1));
                  }}
                  onNext={() => {
                    setQuestionIndex((current) =>
                      Math.min(current + 1, pendingQuestionnaire.questions.length - 1),
                    );
                  }}
                  onDismiss={() => {
                    void handleQuestionnaireAction(true);
                  }}
                  onSelectOption={(option) => {
                    if (!activeQuestion) return;
                    setSelectedAnswers((prev) => ({
                      ...prev,
                      [activeQuestion.id]: option.label,
                    }));
                    setCustomAnswers((prev) => ({
                      ...prev,
                      [activeQuestion.id]: "",
                    }));
                    if (!isFinalQuestion) {
                      setQuestionIndex((current) =>
                        Math.min(current + 1, pendingQuestionnaire.questions.length - 1),
                      );
                    }
                  }}
                />
              </motion.div>
            ) : null}
          </AnimatePresence>

          <AnimatePresence initial={false}>
            {suggestions.length > 0 && !questionnaireActive ? (
              <motion.div
                key="composer-suggestions"
                layout
                initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: -6 }}
                animate={{
                  opacity: 1,
                  y: 0,
                  transition: reducedMotion
                    ? { duration: 0.01 }
                    : {
                        opacity: { duration: CHAT_MOTION_DURATION.fast, ease: "easeOut" },
                        y: getChatLayoutTransition(false),
                      },
                }}
                exit={
                  reducedMotion
                    ? { opacity: 0 }
                    : { opacity: 0, y: -4, transition: { duration: 0.12 } }
                }
                className="flex gap-1.5 overflow-x-auto px-1 pb-0.5"
              >
                {suggestions.map((suggestion, index) => (
                  <motion.button
                    key={`${suggestion}-${index}`}
                    layout
                    type="button"
                    disabled={!canUseQuickMessage}
                    initial={reducedMotion ? false : { opacity: 0, x: -8, scale: 0.98 }}
                    animate={{
                      opacity: 1,
                      x: 0,
                      scale: 1,
                      transition: reducedMotion
                        ? { duration: 0.01 }
                        : {
                            opacity: {
                              duration: CHAT_MOTION_DURATION.fast,
                              delay: index * CHAT_MOTION_DURATION.stagger,
                            },
                            x: {
                              ...getChatLayoutTransition(false),
                              delay: index * CHAT_MOTION_DURATION.stagger,
                            },
                            scale: {
                              ...getChatTactileTransition(false),
                              delay: index * CHAT_MOTION_DURATION.stagger,
                            },
                          },
                    }}
                    whileTap={reducedMotion ? undefined : { scale: 0.97 }}
                    className={cn(
                      "shrink-0 rounded-full px-3 py-1.5 text-xs transition-colors hover:bg-accent hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50",
                      COMPOSER_CHIP_CLASSNAME,
                    )}
                    onClick={() => {
                      handleSuggestionSelect(suggestion);
                    }}
                  >
                    {suggestion}
                  </motion.button>
                ))}
              </motion.div>
            ) : null}
          </AnimatePresence>

          <AnimatePresence initial={false}>
            {attachments.length > 0 && !questionnaireActive ? (
              <motion.div
                key="composer-attachments"
                layout
                className="flex flex-wrap gap-2 px-1 pt-1"
              >
                {attachments.map((part, index) => {
                  const key = `${part.type}-${index}`;
                  return (
                    <motion.div
                      key={key}
                      layout
                      initial={reducedMotion ? false : { opacity: 0, y: 8, scale: 0.98 }}
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
                                ...getChatTactileTransition(false),
                                delay: index * CHAT_MOTION_DURATION.stagger,
                              },
                            },
                      }}
                      exit={
                        reducedMotion
                          ? { opacity: 0 }
                          : { opacity: 0, scale: 0.97, y: 6, transition: { duration: 0.12 } }
                      }
                      className={cn(
                        "group inline-flex max-w-[220px] items-center gap-1.5 rounded-[var(--radius-input-inner)] px-2.5 py-1.5 text-xs",
                        COMPOSER_CHIP_CLASSNAME,
                      )}
                    >
                      {part.type === "image" ? (
                        <img
                          alt="upload"
                          className="size-5 rounded object-cover"
                          src={resolveFileUrl(part.url)}
                        />
                      ) : (
                        partIcon(part)
                      )}
                      <span className="truncate">{partLabel(part, t)}</span>
                      <button
                        className="rounded p-0.5 text-muted-foreground hover:bg-muted hover:text-foreground"
                        onClick={async () => {
                          if (!ready || disabled || isGenerating || submitting) return;

                          const fileId = getPartFileId(part);
                          if (fileId != null && (shouldDeleteFileOnRemove?.(part) ?? true)) {
                            try {
                              await api.delete<{ status: string }>(`files/${fileId}`);
                            } catch (deleteError) {
                              const message =
                                deleteError instanceof Error
                                  ? deleteError.message
                                  : t("chat.delete_attachment_failed");
                              setError(message);
                              return;
                            }
                          }

                          await onRemovePart(index, part);
                        }}
                        type="button"
                      >
                        <X className="size-3" />
                      </button>
                    </motion.div>
                  );
                })}
              </motion.div>
            ) : null}
          </AnimatePresence>

          <AnimatePresence initial={false}>
            {showNoImageInputHint ? (
              <motion.div
                key="composer-no-image-hint"
                layout
                initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: 4 }}
                animate={{
                  opacity: 1,
                  y: 0,
                  transition: reducedMotion
                    ? { duration: 0.01 }
                    : {
                        opacity: { duration: CHAT_MOTION_DURATION.fast, ease: "easeOut" },
                        y: getChatLayoutTransition(false),
                      },
                }}
                exit={
                  reducedMotion
                    ? { opacity: 0 }
                    : { opacity: 0, y: -2, transition: { duration: 0.12 } }
                }
                className="px-2 text-xs text-muted-foreground"
              >
                {t("chat.no_image_input_hint")}
              </motion.div>
            ) : null}
          </AnimatePresence>

          <Textarea
            ref={textareaRef}
            value={questionnaireActive ? questionnaireValue : value}
            onChange={handleTextChange}
            onKeyDown={handleKeyDown}
            onPaste={(event) => {
              void handlePaste(event);
            }}
            placeholder={placeholder}
            disabled={!ready || disabled}
            className="min-h-6 max-h-[200px] resize-none border-0 bg-transparent px-3.5 py-2 pr-12 text-[15px] leading-6 shadow-none focus-visible:ring-0 dark:bg-transparent"
            rows={1}
          />
          <motion.div
            layout
            transition={getChatLayoutTransition(reducedMotion)}
            className="flex items-end justify-between gap-2 px-0.5 pt-0"
          >
            <div className="flex min-w-0 flex-wrap items-center gap-1">
              {!questionnaireActive ? (
                <>
                  <DropdownMenu open={uploadMenuOpen} onOpenChange={setUploadMenuOpen}>
                    <input
                      ref={fileInputRef}
                      className="hidden"
                      multiple
                      onChange={handleUploadInputChange}
                      type="file"
                    />
                    <input
                      ref={imageInputRef}
                      accept={IMAGE_UPLOAD_ACCEPT}
                      className="hidden"
                      multiple
                      onChange={handleUploadInputChange}
                      type="file"
                    />
                    <DropdownMenuTrigger asChild>
                      <Button
                        variant="ghost"
                        size="icon"
                        disabled={!canUpload}
                        className={cn("size-9 rounded-full", COMPOSER_CONTROL_BUTTON_CLASSNAME)}
                      >
                        <Plus
                          className={cn(
                            "size-4 transition-transform",
                            uploadMenuOpen && "rotate-45",
                          )}
                        />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent className="min-w-36" side="top" align="start">
                      <DropdownMenuItem
                        onClick={() => {
                          imageInputRef.current?.click();
                        }}
                      >
                        <Image className="size-4" />
                        {t("chat.upload_image")}
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        onClick={() => {
                          fileInputRef.current?.click();
                        }}
                      >
                        <FolderOpen className="size-4" />
                        {t("chat.upload_document")}
                      </DropdownMenuItem>
                      {onExportConversation && (
                        <DropdownMenuItem
                          onClick={() => {
                            onExportConversation(false);
                          }}
                        >
                          <FileDown className="size-4" />
                          {t("chat.export_conversation")}
                        </DropdownMenuItem>
                      )}
                      {onExportConversation && (
                        <DropdownMenuItem
                          onClick={() => {
                            onExportConversation(true);
                          }}
                        >
                          <FileDown className="size-4" />
                          {t("chat.export_conversation_with_reasoning")}
                        </DropdownMenuItem>
                      )}
                    </DropdownMenuContent>
                  </DropdownMenu>
                  <ModelList disabled={!canSwitchModel} className="max-w-64" />
                  <SearchPickerButton disabled={!canSwitchModel} />
                  <ReasoningPickerButton disabled={!canSwitchModel} />
                  <InjectionPickerButton
                    disabled={!canSwitchModel}
                    assistantId={assistantId}
                    conversationId={conversationId}
                    conversationSkillIds={conversationSkillIds}
                  />
                  <QuickMessageButton
                    quickMessages={quickMessages}
                    disabled={!canUseQuickMessage}
                    onSelect={handleQuickMessageSelect}
                  />
                </>
              ) : null}
            </div>
            <Button
              onClick={() => {
                void handlePrimaryAction();
              }}
              disabled={actionDisabled}
              size="icon"
              className={cn(
                "size-9 rounded-full shadow-sm",
                isGenerating && !submitting
                  ? "bg-destructive text-destructive-foreground hover:bg-destructive/90"
                  : "bg-primary text-primary-foreground hover:bg-primary/90",
              )}
            >
              <motion.span
                whileTap={reducedMotion ? undefined : { scale: 0.94 }}
                whileHover={reducedMotion ? undefined : { scale: 1.02 }}
                className="flex items-center justify-center"
              >
                <AnimatePresence initial={false} mode="popLayout">
                  {submitting || uploading ? (
                    <motion.span
                      key="composer-loading"
                      initial={
                        reducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.8, rotate: -16 }
                      }
                      animate={{ opacity: 1, scale: 1, rotate: 0 }}
                      exit={reducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.8, rotate: 16 }}
                      transition={getChatTactileTransition(reducedMotion)}
                    >
                      <LoaderCircle className="size-4.5 animate-spin" />
                    </motion.span>
                  ) : isGenerating ? (
                    <motion.span
                      key="composer-stop"
                      initial={
                        reducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.8, rotate: -12 }
                      }
                      animate={{ opacity: 1, scale: 1, rotate: 0 }}
                      exit={reducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.8, rotate: 12 }}
                      transition={getChatTactileTransition(reducedMotion)}
                    >
                      <Square className="size-4.5" />
                    </motion.span>
                  ) : (
                    <motion.span
                      key={
                        questionnaireActive
                          ? isFinalQuestion
                            ? "composer-questionnaire-submit"
                            : "composer-questionnaire-next"
                          : "composer-send"
                      }
                      initial={
                        reducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.8, rotate: 12 }
                      }
                      animate={{ opacity: 1, scale: 1, rotate: 0 }}
                      exit={
                        reducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.8, rotate: -12 }
                      }
                      transition={getChatTactileTransition(reducedMotion)}
                    >
                      {questionnaireActive && !isFinalQuestion ? (
                        <ChevronRight className="size-4.5" />
                      ) : (
                        <ArrowUp className="size-4.5" />
                      )}
                    </motion.span>
                  )}
                </AnimatePresence>
              </motion.span>
            </Button>
          </motion.div>
        </motion.div>
        <p className="mt-1 text-center text-[11px] leading-4 text-muted-foreground">{sendHint}</p>
        {error ? <p className="mt-0.5 text-center text-xs text-destructive">{error}</p> : null}
      </div>
    </div>
  );
}

export const ChatInput = React.memo(ChatInputInner);
ChatInput.displayName = "ChatInput";

type QuickMessageOption = {
  title: string;
  content: string;
};

function QuestionnairePanel({
  question,
  currentIndex,
  totalQuestions,
  selectedValue,
  onPrevious,
  onNext,
  onDismiss,
  onSelectOption,
}: {
  question: AskUserQuestion | null;
  currentIndex: number;
  totalQuestions: number;
  selectedValue: string | null;
  onPrevious: () => void;
  onNext: () => void;
  onDismiss: () => void;
  onSelectOption: (option: AskUserOption) => void;
}) {
  const { t } = useTranslation("input");

  if (!question) {
    return null;
  }

  return (
    <div className="space-y-3 p-3">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-1 text-muted-foreground text-xs">
          <button
            type="button"
            onClick={onPrevious}
            disabled={currentIndex === 0}
            className="rounded-full border border-border/70 bg-background/80 p-1 text-foreground transition hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
          >
            <ChevronLeft className="size-3.5" />
          </button>
          <span className="px-1">
            {t("chat.questionnaire_progress", { current: currentIndex + 1, total: totalQuestions })}
          </span>
          <button
            type="button"
            onClick={onNext}
            disabled={currentIndex >= totalQuestions - 1}
            className="rounded-full border border-border/70 bg-background/80 p-1 text-foreground transition hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
          >
            <ChevronRight className="size-3.5" />
          </button>
        </div>
        <button
          type="button"
          onClick={onDismiss}
          className="rounded-full border border-border/70 bg-background/80 p-1.5 text-muted-foreground transition hover:bg-accent hover:text-foreground"
        >
          <X className="size-3.5" />
        </button>
      </div>

      <div className="text-sm font-medium text-foreground">{question.question}</div>

      <div className="space-y-2">
        {question.options.map((option) => (
          <button
            key={option.label}
            type="button"
            onClick={() => {
              onSelectOption(option);
            }}
            className={cn(
              "w-full rounded-[var(--radius-card-inner)] border px-3 py-2 text-left transition active:scale-[0.97]",
              selectedValue === option.label
                ? "border-primary/30 bg-primary/10 text-primary"
                : "border-border/70 bg-background/70 text-foreground hover:bg-accent",
            )}
          >
            <div className="text-sm font-medium">{option.label}</div>
            {option.description ? (
              <div className="mt-1 text-muted-foreground text-xs">{option.description}</div>
            ) : null}
          </button>
        ))}
      </div>
    </div>
  );
}

interface QuickMessageButtonProps {
  quickMessages: QuickMessageOption[];
  disabled?: boolean;
  onSelect: (content: string) => void;
}

function QuickMessageButton({
  quickMessages,
  disabled = false,
  onSelect,
}: QuickMessageButtonProps) {
  if (quickMessages.length === 0) {
    return null;
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          disabled={disabled}
          className={cn("size-9", COMPOSER_CONTROL_BUTTON_CLASSNAME)}
        >
          <FlashOn className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-72" side="top" align="start">
        {quickMessages.map((quickMessage, index) => {
          const key = `${quickMessage.title}-${index}`;
          return (
            <DropdownMenuItem
              key={key}
              className="items-start"
              onClick={() => {
                onSelect(quickMessage.content);
              }}
            >
              <div className="min-w-0">
                <div className="truncate text-sm font-medium">{quickMessage.title}</div>
                <div className="text-muted-foreground mt-0.5 line-clamp-2 text-xs">
                  {quickMessage.content}
                </div>
              </div>
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
