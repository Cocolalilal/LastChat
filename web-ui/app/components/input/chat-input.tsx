import * as React from "react";

import { fileTypeFromBuffer } from "file-type";
import {
  ArrowUp,
  File,
  FileDown,
  Image,
  LoaderCircle,
  Mic,
  Plus,
  Send,
  Square,
  Video,
  X,
  Zap,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { ModelList } from "~/components/input/model-list";
import { ReasoningPickerButton } from "~/components/input/reasoning-picker";
import { SearchPickerButton } from "~/components/input/search-picker";
import { InjectionPickerButton } from "~/components/input/injection-picker";
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
import { cn } from "~/lib/utils";
import api from "~/services/api";
import type { ModeInjectionProfile, UIMessagePart, UploadFilesResponseDto } from "~/types";

export interface ChatInputProps {
  value: string;
  attachments: UIMessagePart[];
  suggestions?: string[];
  ready?: boolean;
  disabled?: boolean;
  isGenerating?: boolean;
  isEditing?: boolean;
  onValueChange: (value: string) => void;
  onAddParts: (parts: UIMessagePart[]) => void;
  shouldDeleteFileOnRemove?: (part: UIMessagePart) => boolean;
  onRemovePart: (index: number, part: UIMessagePart) => Promise<void> | void;
  onSend: () => Promise<void> | void;
  onStop?: () => Promise<void> | void;
  onCancelEdit?: () => void;
  onSuggestionClick?: (suggestion: string) => void;
  onExportConversation?: (includeReasoning: boolean) => void;
  className?: string;
}

const IMAGE_UPLOAD_ACCEPT = "image/*";

interface SlashSkillOption {
  id: string;
  name: string;
  description: string;
  command: string;
}

function getSlashSkillCommand(skill: Pick<ModeInjectionProfile, "argumentHint" | "name">): string {
  if (typeof skill.argumentHint === "string") {
    const hinted = skill.argumentHint.trim();
    if (hinted.startsWith("/")) {
      return hinted;
    }
  }

  const normalizedName = skill.name.trim();
  return normalizedName ? `/${normalizedName}` : "/skill";
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
    detected.mime.startsWith("audio/")
  ) {
    return true;
  }

  // 其他可识别的二进制格式（exe、zip 等）→ 拒绝
  return false;
}

function toMessagePart(
  file: UploadFilesResponseDto["files"][number],
): UIMessagePart {
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
      return <Mic className="size-3.5" />;
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
  onValueChange,
  onAddParts,
  shouldDeleteFileOnRemove,
  onRemovePart,
  onSend,
  onStop,
  onCancelEdit,
  onSuggestionClick,
  onExportConversation,
  className,
}: ChatInputProps) {
  const { t } = useTranslation("input");
  const sendOnEnter = useSettingsStore(
    (state) => state.settings?.displaySetting.sendOnEnter ?? true,
  );
  const pasteLongTextAsFile = useSettingsStore(
    (state) => state.settings?.displaySetting.pasteLongTextAsFile ?? false,
  );
  const pasteLongTextThreshold = useSettingsStore(
    (state) => state.settings?.displaySetting.pasteLongTextThreshold ?? 1000,
  );
  const { settings, currentAssistant } = useCurrentAssistant();

  const quickMessages = React.useMemo(() => {
    const source = currentAssistant?.quickMessages;
    if (!Array.isArray(source)) {
      return [] as QuickMessageOption[];
    }

    return source
      .map((item) => {
        const title = typeof item?.title === "string" ? item.title.trim() : "";
        const content =
          typeof item?.content === "string" ? item.content.trim() : "";
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
  const slashSkills = React.useMemo(() => {
    const source = settings?.modeInjections;
    if (!Array.isArray(source)) {
      return [] as SlashSkillOption[];
    }

    return source
      .filter(
        (item): item is ModeInjectionProfile =>
          Boolean(item && typeof item === "object" && typeof item.id === "string") &&
          item.enabled !== false,
      )
      .map((item) => ({
        id: item.id,
        name: item.name?.trim() || t("injection.unnamed_mode"),
        description: item.description?.trim() || "",
        command: getSlashSkillCommand(item),
      }))
      .sort((left, right) => left.command.localeCompare(right.command));
  }, [settings?.modeInjections, t]);

  const imageInputRef = React.useRef<HTMLInputElement | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const [uploading, setUploading] = React.useState(false);
  const [uploadMenuOpen, setUploadMenuOpen] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [dragActive, setDragActive] = React.useState(false);
  const dragDepthRef = React.useRef(0);

  const isEmpty = value.trim().length === 0 && attachments.length === 0;
  const slashToken = React.useMemo(() => value.trimStart().split(/\s+/, 1)[0] ?? "", [value]);
  const isTypingSlashToken =
    value.startsWith("/") && !value.slice(1).includes(" ") && !value.includes("\n");
  const filteredSlashSkills = React.useMemo(() => {
    if (!slashToken.startsWith("/")) {
      return [] as SlashSkillOption[];
    }

    const query = slashToken.toLowerCase();
    return slashSkills.filter((skill) => skill.command.toLowerCase().startsWith(query));
  }, [slashSkills, slashToken]);

  const canStop = ready && Boolean(onStop) && isGenerating && !disabled;
  const canSend = ready && !isGenerating && !disabled && !isEmpty;
  const canUpload =
    ready && !disabled && !isGenerating && !uploading && !submitting;
  const canSwitchModel =
    ready && !disabled && !isGenerating && !uploading && !submitting;
  const canUseQuickMessage = ready && !disabled && !uploading && !submitting;
  const actionDisabled = submitting || uploading || (!canStop && !canSend);

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
        toast.warning(
          t("chat.unsupported_file_skipped", { count: skippedFiles.length }),
        );
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
        const response = await api.postMultipart<UploadFilesResponseDto>(
          "files/upload",
          formData,
        );
        const parts = response.files.map(toMessagePart);
        onAddParts(parts);
      } catch (uploadError) {
        const message =
          uploadError instanceof Error
            ? uploadError.message
            : t("chat.upload_failed");
        setError(message);
      } finally {
        setUploading(false);
      }
    },
    [onAddParts, ready, t],
  );

  const handlePrimaryAction = React.useCallback(async () => {
    if (actionDisabled) {
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      if (canStop) {
        await onStop?.();
        return;
      }

      if (canSend) {
        await onSend();
      }
    } catch (submitError) {
      const message =
        submitError instanceof Error
          ? submitError.message
          : t("chat.send_failed");
      setError(message);
    } finally {
      setSubmitting(false);
    }
  }, [actionDisabled, canSend, canStop, onSend, onStop, t]);

  const handleTextChange = React.useCallback(
    (event: React.ChangeEvent<HTMLTextAreaElement>) => {
      onValueChange(event.target.value);
      if (error) {
        setError(null);
      }
    },
    [error, onValueChange],
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

  const handleSlashSkillSelect = React.useCallback(
    (command: string) => {
      if (!canUseQuickMessage) {
        return;
      }

      onValueChange(`${command} `);
      if (error) {
        setError(null);
      }
      textareaRef.current?.focus();
    },
    [canUseQuickMessage, error, onValueChange],
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

  const handleDragLeave = React.useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      event.stopPropagation();
      dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
      if (dragDepthRef.current === 0) {
        setDragActive(false);
      }
    },
    [],
  );

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

  const sendHint = sendOnEnter
    ? t("chat.send_hint_enter")
    : t("chat.send_hint_newline");
  const placeholder = ready
    ? t("chat.placeholder_ready")
    : t("chat.placeholder_not_ready");

  return (
    <div
      className={cn(
        "bg-transparent",
        className,
      )}
    >
      <div className="mx-auto w-full max-w-4xl">
        <div
          className={cn(
            "relative flex flex-col gap-2.5 rounded-[1.3rem] border border-border/70 bg-card/90 px-3 py-3 shadow-xl backdrop-blur-xl transition-shadow focus-within:border-ring/50 focus-within:shadow-2xl focus-within:ring-1 focus-within:ring-ring/40 sm:px-4 sm:py-3.5",
            dragActive &&
              "border-primary/40 bg-primary/5 ring-2 ring-primary/20",
          )}
          onDragEnter={handleDragEnter}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={(event) => {
            void handleDrop(event);
          }}
        >
          {dragActive ? (
            <div className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center rounded-2xl border-2 border-dashed border-primary/50 bg-background/80 px-4 text-center text-sm font-medium text-primary">
              {t("chat.drop_to_upload")}
            </div>
          ) : null}
          {isEditing ? (
            <div className="flex items-center justify-between rounded-2xl border border-primary/20 bg-primary/5 px-3 py-2 text-xs">
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
            </div>
          ) : null}

          {isTypingSlashToken && filteredSlashSkills.length > 0 ? (
            <div className="overflow-hidden rounded-[1.05rem] border border-border/70 bg-background/90 shadow-sm">
              <div className="flex items-center justify-between border-b border-border/60 px-3 py-2 text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
                <span>{t("chat.slash_skills_title")}</span>
                <span className="normal-case tracking-normal">{t("chat.slash_skills_hint")}</span>
              </div>
              <div className="max-h-56 overflow-y-auto p-1.5">
                {filteredSlashSkills.map((skill) => (
                  <button
                    key={skill.id}
                    type="button"
                    disabled={!canUseQuickMessage}
                    className="flex w-full items-start gap-3 rounded-[0.95rem] px-3 py-2 text-left transition hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                    onClick={() => {
                      handleSlashSkillSelect(skill.command);
                    }}
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-foreground">
                        {skill.name}
                      </span>
                      {skill.description ? (
                        <span className="mt-0.5 block line-clamp-2 text-xs text-muted-foreground">
                          {skill.description}
                        </span>
                      ) : null}
                    </span>
                    <span className="shrink-0 rounded-full bg-muted px-2 py-1 text-[11px] text-muted-foreground">
                      {skill.command}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {suggestions.length > 0 && !isTypingSlashToken ? (
            <div className="flex gap-2 overflow-x-auto px-1 pb-1">
              {suggestions.map((suggestion, index) => (
                <button
                  key={`${suggestion}-${index}`}
                  type="button"
                  disabled={!canUseQuickMessage}
                  className={cn(
                    "shrink-0 rounded-full border border-border/70 bg-background/90 px-3 py-1.5 text-xs text-foreground/80 transition-colors hover:bg-muted hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50",
                  )}
                  onClick={() => {
                    handleSuggestionSelect(suggestion);
                  }}
                >
                  {suggestion}
                </button>
              ))}
            </div>
          ) : null}

          {attachments.length > 0 ? (
            <div className="flex flex-wrap gap-2 px-1 pt-1">
              {attachments.map((part, index) => {
                const key = `${part.type}-${index}`;
                return (
                  <div
                    key={key}
                    className="group inline-flex max-w-[220px] items-center gap-1.5 rounded-2xl border border-border/70 bg-background/75 px-2.5 py-1.5 text-xs shadow-sm"
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
                        if (!ready || disabled || isGenerating || submitting)
                          return;

                        const fileId = getPartFileId(part);
                        if (
                          fileId != null &&
                          (shouldDeleteFileOnRemove?.(part) ?? true)
                        ) {
                          try {
                            await api.delete<{ status: string }>(
                              `files/${fileId}`,
                            );
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
                  </div>
                );
              })}
            </div>
          ) : null}

          <Textarea
            ref={textareaRef}
            value={value}
            onChange={handleTextChange}
            onKeyDown={handleKeyDown}
            onPaste={(event) => {
                void handlePaste(event);
              }}
            placeholder={placeholder}
            disabled={!ready || disabled}
            className="min-h-[1.75rem] max-h-[240px] resize-none border-0 bg-transparent px-1 py-0.5 text-[15px] leading-7 shadow-none focus-visible:ring-0 dark:bg-transparent"
            rows={1}
          />
          <div className="flex items-end justify-between gap-3 pt-1">
            <div className="flex min-w-0 flex-wrap items-center gap-1.5">
              <DropdownMenu
                open={uploadMenuOpen}
                onOpenChange={setUploadMenuOpen}
              >
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
                    className="size-9 rounded-full border border-border/60 bg-background/65 text-muted-foreground shadow-sm hover:bg-muted hover:text-foreground"
                  >
                    <Plus
                      className={cn(
                        "size-4 transition-transform",
                        uploadMenuOpen && "rotate-45",
                      )}
                    />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  className="min-w-36"
                  side="top"
                  align="start"
                >
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
                    <File className="size-4" />
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
              <InjectionPickerButton disabled={!canSwitchModel} />
              <QuickMessageButton
                quickMessages={quickMessages}
                disabled={!canUseQuickMessage}
                onSelect={handleQuickMessageSelect}
              />
            </div>
            <Button
              onClick={() => {
                void handlePrimaryAction();
              }}
              disabled={actionDisabled}
              size="icon"
              className={cn(
                "size-11 rounded-full shadow-lg",
                isGenerating && !submitting
                  ? "bg-destructive text-destructive-foreground hover:bg-destructive/90"
                  : "bg-primary text-primary-foreground hover:bg-primary/90",
              )}
            >
              {submitting || uploading ? (
                <LoaderCircle className="size-4.5 animate-spin" />
              ) : isGenerating ? (
                <Square className="size-4.5" />
              ) : (
                <ArrowUp className="size-4.5" />
              )}
            </Button>
          </div>
        </div>
        <p className="mt-2 text-center text-[11px] text-muted-foreground">
          {sendHint}
        </p>
        {error ? (
          <p className="mt-1 text-center text-xs text-destructive">{error}</p>
        ) : null}
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
          className="size-9 rounded-full border border-border/60 bg-background/80 text-muted-foreground shadow-sm hover:bg-muted hover:text-foreground"
        >
          <Zap className="size-4" />
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
                <div className="truncate text-sm font-medium">
                  {quickMessage.title}
                </div>
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
