import * as React from "react";
import type { TFunction } from "i18next";
import { AudioLines,
  BookHeart,
  BookX,
  Check,
  Clipboard,
  ClipboardPaste,
  Clock3,
  Globe,
  Image as ImageIcon,
  MessageCircleQuestion,
  Video,
  Wrench,
  X, } from "~/lib/material-icons";

import Markdown from "~/components/markdown/markdown";
import { DocumentPart } from "~/components/message/parts/document-part";
import { Button } from "~/components/ui/button";
import { resolveFileUrl } from "~/lib/files";
import type { DisplaySetting, TextPart as UITextPart, ToolPart as UIToolPart } from "~/types";

export const TOOL_NAMES = {
  MEMORY: "memory_tool",
  SEARCH_WEB: "search_web",
  SCRAPE_WEB: "scrape_web",
  GET_TIME_INFO: "get_time_info",
  CLIPBOARD: "clipboard_tool",
  ASK_USER: "ask_user",
} as const;

export const MEMORY_ACTIONS = {
  CREATE: "create",
  EDIT: "edit",
  DELETE: "delete",
} as const;

export const CLIPBOARD_ACTIONS = {
  READ: "read",
  WRITE: "write",
} as const;

function tryParseJson(input: string): unknown | undefined {
  try {
    return JSON.parse(input);
  } catch {
    return undefined;
  }
}

function extractJsonCodeFence(input: string): string | null {
  const match = input.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  return match?.[1] ?? null;
}

function extractBalancedJsonSlice(input: string): string | null {
  if (!input) return null;

  const objectStart = input.indexOf("{");
  const arrayStart = input.indexOf("[");
  const startIndex =
    objectStart === -1
      ? arrayStart
      : arrayStart === -1
        ? objectStart
        : Math.min(objectStart, arrayStart);

  if (startIndex < 0) return null;

  const stack: string[] = [];
  let inString = false;
  let escaping = false;

  for (let index = startIndex; index < input.length; index += 1) {
    const char = input[index];

    if (escaping) {
      escaping = false;
      continue;
    }

    if (char === "\\") {
      if (inString) {
        escaping = true;
      }
      continue;
    }

    if (char === "\"") {
      inString = !inString;
      continue;
    }

    if (inString) {
      continue;
    }

    if (char === "{") {
      stack.push("}");
      continue;
    }

    if (char === "[") {
      stack.push("]");
      continue;
    }

    if (char === "}" || char === "]") {
      if (stack[stack.length - 1] !== char) {
        return null;
      }

      stack.pop();
      if (stack.length === 0) {
        return input.slice(startIndex, index + 1);
      }
    }
  }

  return null;
}

export function safeJsonParse(input: string): unknown {
  const trimmed = input.trim();
  if (!trimmed) return {};

  const fenced = extractJsonCodeFence(trimmed);
  const candidates = [
    trimmed,
    fenced,
    extractBalancedJsonSlice(trimmed),
    fenced ? extractBalancedJsonSlice(fenced) : null,
  ].filter((candidate, index, values): candidate is string => {
    return typeof candidate === "string" && candidate.length > 0 && values.indexOf(candidate) === index;
  });

  for (const candidate of candidates) {
    const parsed = tryParseJson(candidate);
    if (parsed !== undefined) {
      return parsed;
    }
  }

  return {};
}

export function toJsonString(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2);
}

export function getStringField(data: unknown, key: string): string | undefined {
  if (!data || typeof data !== "object" || Array.isArray(data)) return undefined;
  const value = (data as Record<string, unknown>)[key];
  return typeof value === "string" ? value : undefined;
}

export function getArrayField(data: unknown, key: string): unknown[] {
  if (!data || typeof data !== "object" || Array.isArray(data)) return [];
  const value = (data as Record<string, unknown>)[key];
  return Array.isArray(value) ? value : [];
}

function parseOutputContent(tool: UIToolPart): unknown {
  const outputText = tool.output
    .filter((part): part is UITextPart => part.type === "text")
    .map((part) => part.text)
    .join("\n");
  return safeJsonParse(outputText);
}

export function getToolIcon(toolName: string, action?: string) {
  if (toolName === TOOL_NAMES.MEMORY) {
    if (action === MEMORY_ACTIONS.CREATE || action === MEMORY_ACTIONS.EDIT) return BookHeart;
    if (action === MEMORY_ACTIONS.DELETE) return BookX;
    return Wrench;
  }
  if (toolName === TOOL_NAMES.SEARCH_WEB) return Globe;
  if (toolName === TOOL_NAMES.SCRAPE_WEB) return Globe;
  if (toolName === TOOL_NAMES.GET_TIME_INFO) return Clock3;
  if (toolName === TOOL_NAMES.CLIPBOARD) {
    if (action === CLIPBOARD_ACTIONS.WRITE) return ClipboardPaste;
    return Clipboard;
  }
  if (toolName === TOOL_NAMES.ASK_USER) return MessageCircleQuestion;
  return Wrench;
}

export function getToolTitle(toolName: string, args: unknown, t: TFunction): string {
  const action = getStringField(args, "action");
  if (toolName === TOOL_NAMES.MEMORY) {
    if (action === MEMORY_ACTIONS.CREATE) return t("tool_part.memory_create");
    if (action === MEMORY_ACTIONS.EDIT) return t("tool_part.memory_edit");
    if (action === MEMORY_ACTIONS.DELETE) return t("tool_part.memory_delete");
  }
  if (toolName === TOOL_NAMES.SEARCH_WEB) {
    const query = getStringField(args, "query") ?? "";
    return query ? t("tool_part.search_web_with_query", { query }) : t("tool_part.search_web");
  }
  if (toolName === TOOL_NAMES.SCRAPE_WEB) return t("tool_part.scrape_web");
  if (toolName === TOOL_NAMES.GET_TIME_INFO) return t("tool_part.get_time_info");
  if (toolName === TOOL_NAMES.CLIPBOARD) {
    if (action === CLIPBOARD_ACTIONS.READ) return t("tool_part.clipboard_read");
    if (action === CLIPBOARD_ACTIONS.WRITE) return t("tool_part.clipboard_write");
  }
  if (toolName === TOOL_NAMES.ASK_USER) return t("tool_part.ask_user_title");
  return t("tool_part.tool_call_with_name", { toolName });
}

export function toolHasMediaOutput(tool: UIToolPart): boolean {
  return tool.output.some((part) => part.type === "image" || part.type === "video" || part.type === "audio");
}

export function getToolPreviewText(tool: UIToolPart, t: TFunction): string | null {
  const args = safeJsonParse(tool.input);
  const outputContent = parseOutputContent(tool);
  const memoryAction = getStringField(args, "action");

  if (
    tool.toolName === TOOL_NAMES.MEMORY &&
    (memoryAction === MEMORY_ACTIONS.CREATE || memoryAction === MEMORY_ACTIONS.EDIT)
  ) {
    return getStringField(outputContent, "content") ?? null;
  }
  if (tool.toolName === TOOL_NAMES.SEARCH_WEB) {
    return getStringField(outputContent, "answer") ?? null;
  }
  if (tool.toolName === TOOL_NAMES.SCRAPE_WEB) {
    return getStringField(args, "url") ?? null;
  }
  if (tool.approvalState.type === "denied") {
    return tool.approvalState.reason
      ? t("tool_part.denied_with_reason", { reason: tool.approvalState.reason })
      : t("tool_part.denied");
  }
  if (tool.toolName === TOOL_NAMES.ASK_USER) {
    const questions = parseAskUserQuestions(args);
    if (questions.length === 0) return null;
    return questions.length === 1
      ? questions[0].question
      : t("tool_part.ask_user_questions_count", { count: questions.length });
  }
  return null;
}

export function getToolOutputContent(tool: UIToolPart): unknown {
  return parseOutputContent(tool);
}

function JsonBlock({ value }: { value: unknown }) {
  return (
    <pre className="max-h-64 overflow-auto rounded-[var(--radius-card-inner)] border border-border bg-background/80 p-3 text-xs">
      {toJsonString(value)}
    </pre>
  );
}

function SearchWebPreview({
  args,
  content,
  t,
  displaySetting,
}: {
  args: unknown;
  content: unknown;
  t: TFunction;
  displaySetting?: DisplaySetting | null;
}) {
  const query = getStringField(args, "query") ?? "";
  const answer = getStringField(content, "answer");
  const items = getArrayField(content, "items");

  return (
    <div className="space-y-3">
      <div className="text-sm">
        {t("tool_part.search_query_label", { query: query || t("tool_part.empty") })}
      </div>
      {answer ? (
        <div className="rounded-[var(--radius-card-inner)] border border-border bg-background/80 p-3">
          <Markdown content={answer} className="text-sm" displaySetting={displaySetting} />
        </div>
      ) : null}
      {items.length > 0 ? (
        <div className="space-y-2">
          {items.map((item, index) => {
            if (!item || typeof item !== "object" || Array.isArray(item)) return null;
            const record = item as Record<string, unknown>;
            const url = typeof record.url === "string" ? record.url : "";
            const title = typeof record.title === "string" ? record.title : "";
            const text = typeof record.text === "string" ? record.text : "";
            if (!url) return null;

            return (
              <a
                key={`${url}-${index}`}
                className="block rounded-[var(--radius-card-inner)] border border-border bg-card p-3 transition-colors hover:bg-accent"
                href={url}
                rel="noreferrer"
                target="_blank"
              >
                <div className="line-clamp-1 font-medium text-sm">{title || url}</div>
                {text ? (
                  <div className="mt-1 line-clamp-3 text-muted-foreground text-xs">{text}</div>
                ) : null}
                <div className="mt-2 line-clamp-1 text-primary text-xs">{url}</div>
              </a>
            );
          })}
        </div>
      ) : (
        <JsonBlock value={content} />
      )}
    </div>
  );
}

function ScrapeWebPreview({
  content,
  displaySetting,
}: {
  content: unknown;
  displaySetting?: DisplaySetting | null;
}) {
  const urls = getArrayField(content, "urls");
  if (urls.length === 0) return <JsonBlock value={content} />;

  return (
    <div className="space-y-3">
      {urls.map((item, index) => {
        if (!item || typeof item !== "object" || Array.isArray(item)) return null;
        const record = item as Record<string, unknown>;
        const url = typeof record.url === "string" ? record.url : "";
        const text = typeof record.content === "string" ? record.content : "";
        return (
          <div
            key={`${url}-${index}`}
            className="space-y-2 rounded-[var(--radius-card-inner)] border border-border bg-background/60 p-3"
          >
            <div className="line-clamp-1 text-muted-foreground text-xs">{url}</div>
            <div className="rounded-[calc(var(--radius-card-inner)-0.25rem)] border border-border bg-card p-2.5">
              <Markdown content={text} className="text-sm" displaySetting={displaySetting} />
            </div>
          </div>
        );
      })}
    </div>
  );
}

export interface AskUserOption {
  label: string;
  description?: string;
}

export interface AskUserQuestion {
  id: string;
  question: string;
  options: AskUserOption[];
}

export interface AskUserAnswer {
  id: string;
  status: "answered" | "skipped";
  source?: "option" | "custom";
  value?: string;
}

export interface AskUserAnswerPayload {
  answers: AskUserAnswer[];
  dismissed: boolean;
}

export function parseAskUserQuestions(args: unknown): AskUserQuestion[] {
  try {
    return getArrayField(args, "questions")
      .map((question) => {
        if (!question || typeof question !== "object" || Array.isArray(question)) return null;
        const record = question as Record<string, unknown>;
        const id = typeof record.id === "string" ? record.id : "";
        const prompt = typeof record.question === "string" ? record.question : "";
        if (!id || !prompt) return null;
        const options = Array.isArray(record.options)
          ? record.options
              .map((option) => {
                if (typeof option === "string") {
                  const label = option.trim();
                  return label ? { label } : null;
                }
                if (!option || typeof option !== "object" || Array.isArray(option)) {
                  return null;
                }
                const optionRecord = option as Record<string, unknown>;
                const label = typeof optionRecord.label === "string" ? optionRecord.label.trim() : "";
                if (!label) return null;
                const description =
                  typeof optionRecord.description === "string"
                    ? optionRecord.description.trim() || undefined
                    : undefined;
                return { label, description };
              })
              .filter((option): option is AskUserOption => option !== null)
              .slice(0, 3)
          : [];
        return { id, question: prompt, options };
      })
      .filter((question): question is AskUserQuestion => question !== null)
      .slice(0, 5);
  } catch {
    return [];
  }
}

export function parseAskUserAnswerPayload(raw: string | null | undefined): AskUserAnswerPayload | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const answersSource = Array.isArray(parsed.answers) ? parsed.answers : [];
    const answers = answersSource
      .map((answer) => {
        if (!answer || typeof answer !== "object" || Array.isArray(answer)) return null;
        const record = answer as Record<string, unknown>;
        const id = typeof record.id === "string" ? record.id : "";
        const status: AskUserAnswer["status"] | null =
          record.status === "answered" || record.status === "skipped"
            ? record.status
            : null;
        if (!id || !status) return null;
        const source: AskUserAnswer["source"] =
          record.source === "option" || record.source === "custom"
            ? record.source
            : undefined;
        const value: AskUserAnswer["value"] =
          typeof record.value === "string" ? record.value : undefined;
        const normalized: AskUserAnswer = { id, status };
        if (source) normalized.source = source;
        if (value !== undefined) normalized.value = value;
        return normalized;
      })
      .filter((answer): answer is AskUserAnswer => answer !== null);

    return {
      answers,
      dismissed: parsed.dismissed === true,
    };
  } catch {
    return null;
  }
}

function AskUserToolContent({
  tool,
  t,
}: {
  tool: UIToolPart;
  t: TFunction;
}) {
  const args = React.useMemo(() => safeJsonParse(tool.input), [tool.input]);
  const questions = React.useMemo(() => parseAskUserQuestions(args), [args]);
  const answerPayload = React.useMemo(() => {
    if (tool.approvalState.type === "answered") {
      return parseAskUserAnswerPayload(tool.approvalState.answer);
    }
    const outputText = tool.output
      .filter((part): part is UITextPart => part.type === "text")
      .map((part) => part.text)
      .join("\n");
    return parseAskUserAnswerPayload(outputText);
  }, [tool.approvalState, tool.output]);
  const answersById = React.useMemo(() => {
    return new Map(answerPayload?.answers.map((answer) => [answer.id, answer]) ?? []);
  }, [answerPayload]);

  return (
    <div className="space-y-3">
      {questions.map((question) => (
        <div key={question.id} className="space-y-2">
          <div className="text-sm text-foreground">{question.question}</div>
          {question.options.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {question.options.map((option) => {
                const answer = answersById.get(question.id);
                const selected = answer?.value === option.label;
                return (
                  <span
                    key={option.label}
                    className={
                      selected
                        ? "rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-xs text-primary"
                        : "rounded-full border border-border bg-background px-3 py-1 text-xs text-muted-foreground"
                    }
                  >
                    {option.label}
                  </span>
                );
              })}
            </div>
          ) : null}
          {(() => {
            const answer = answersById.get(question.id);
            if (answer?.status === "answered") {
              return (
                <div className="text-primary text-sm">
                  {answer.value ?? t("tool_part.ask_user_answered")}
                </div>
              );
            }
            if (answer?.status === "skipped") {
              return (
                <div className="text-muted-foreground text-sm">
                  {t("tool_part.ask_user_skipped")}
                </div>
              );
            }
            if (tool.approvalState.type === "pending") {
              return (
                <div className="text-muted-foreground text-sm">
                  {t("tool_part.ask_user_waiting")}
                </div>
              );
            }
            return null;
          })()}
        </div>
      ))}
      {answerPayload?.dismissed ? (
        <div className="text-muted-foreground text-xs">{t("tool_part.ask_user_dismissed")}</div>
      ) : null}
    </div>
  );
}

export function ToolDetailContent({
  tool,
  t,
  displaySetting,
  onToolApproval,
}: {
  tool: UIToolPart;
  t: TFunction;
  displaySetting?: DisplaySetting | null;
  onToolApproval?: (toolCallId: string, approved: boolean, reason: string, answer?: string) => void | Promise<void>;
}) {
  const args = React.useMemo(() => safeJsonParse(tool.input), [tool.input]);
  const outputContent = React.useMemo(() => parseOutputContent(tool), [tool]);
  const isExecuted = tool.output.length > 0;

  if (tool.toolName === TOOL_NAMES.ASK_USER) {
    return <AskUserToolContent tool={tool} t={t} />;
  }

  if (tool.toolName === TOOL_NAMES.SEARCH_WEB && isExecuted) {
    return (
      <SearchWebPreview
        args={args}
        content={outputContent}
        t={t}
        displaySetting={displaySetting}
      />
    );
  }

  if (tool.toolName === TOOL_NAMES.SCRAPE_WEB && isExecuted) {
    return <ScrapeWebPreview content={outputContent} displaySetting={displaySetting} />;
  }

  return (
    <div className="space-y-3">
      <div>
        <div className="mb-1 text-muted-foreground text-xs">{t("tool_part.parameters")}</div>
        <JsonBlock value={args} />
      </div>

      {isExecuted ? (
        <div className="space-y-2">
          <div className="mb-1 text-muted-foreground text-xs">{t("tool_part.result")}</div>
          {tool.output.map((part, index) => {
            if (part.type === "text") {
              let parsed: unknown;
              try {
                parsed = JSON.parse(part.text);
              } catch {
                parsed = part.text;
              }
              return <JsonBlock key={index} value={parsed} />;
            }
            if (part.type === "image") {
              return (
                <img
                  key={index}
                  alt=""
                  className="max-h-72 rounded-md border object-contain"
                  src={resolveFileUrl(part.url)}
                />
              );
            }
            if (part.type === "video") {
              return (
                <video
                  key={index}
                  controls
                  className="max-h-72 w-full rounded-md border"
                  src={resolveFileUrl(part.url)}
                />
              );
            }
            if (part.type === "audio") {
              return <audio key={index} controls className="w-full" src={resolveFileUrl(part.url)} />;
            }
            if (part.type === "document") {
              return (
                <DocumentPart
                  key={index}
                  url={part.url}
                  fileName={part.fileName}
                  mime={part.mime}
                />
              );
            }
            return null;
          })}
        </div>
      ) : (
        <div className="text-muted-foreground text-sm">{t("tool_part.not_executed")}</div>
      )}
    </div>
  );
}

export function ToolPreviewContent({ tool, t }: { tool: UIToolPart; t: TFunction }) {
  const args = React.useMemo(() => safeJsonParse(tool.input), [tool.input]);
  const outputContent = React.useMemo(() => parseOutputContent(tool), [tool]);
  const memoryAction = getStringField(args, "action");
  const deniedReason =
    tool.approvalState.type === "denied" ? (tool.approvalState.reason ?? "") : "";
  const hasMediaOutput = toolHasMediaOutput(tool);
  const askUserAnswers =
    tool.toolName === TOOL_NAMES.ASK_USER && tool.approvalState.type === "answered"
      ? parseAskUserAnswerPayload(tool.approvalState.answer)
      : null;

  const hasExtraContent =
    tool.toolName === TOOL_NAMES.ASK_USER ||
    (tool.toolName === TOOL_NAMES.MEMORY &&
      (memoryAction === MEMORY_ACTIONS.CREATE || memoryAction === MEMORY_ACTIONS.EDIT) &&
      Boolean(getStringField(outputContent, "content"))) ||
    (tool.toolName === TOOL_NAMES.SEARCH_WEB &&
      (Boolean(getStringField(outputContent, "answer")) || getArrayField(outputContent, "items").length > 0)) ||
    (tool.toolName === TOOL_NAMES.SCRAPE_WEB && Boolean(getStringField(args, "url"))) ||
    tool.approvalState.type === "denied" ||
    hasMediaOutput;

  if (!hasExtraContent) return null;

  return (
    <div className="space-y-1">
      {tool.toolName === TOOL_NAMES.ASK_USER ? (
        <div className="text-muted-foreground text-xs">
          {tool.approvalState.type === "pending"
            ? t("tool_part.ask_user_waiting")
            : tool.approvalState.type === "answered"
              ? t("tool_part.ask_user_answer_summary", {
                  answered: askUserAnswers?.answers.filter((answer) => answer.status === "answered").length ?? 0,
                  skipped: askUserAnswers?.answers.filter((answer) => answer.status === "skipped").length ?? 0,
                })
              : getToolPreviewText(tool, t)}
        </div>
      ) : null}

      {tool.toolName === TOOL_NAMES.MEMORY &&
      (memoryAction === MEMORY_ACTIONS.CREATE || memoryAction === MEMORY_ACTIONS.EDIT) ? (
        <div className="line-clamp-3 text-muted-foreground text-xs">
          {getStringField(outputContent, "content")}
        </div>
      ) : null}

      {tool.toolName === TOOL_NAMES.SEARCH_WEB && getStringField(outputContent, "answer") ? (
        <div className="line-clamp-3 text-muted-foreground text-xs">
          {getStringField(outputContent, "answer")}
        </div>
      ) : null}

      {tool.toolName === TOOL_NAMES.SEARCH_WEB && getArrayField(outputContent, "items").length > 0 ? (
        <div className="text-muted-foreground text-xs">
          {t("tool_part.search_results_count", { count: getArrayField(outputContent, "items").length })}
        </div>
      ) : null}

      {tool.toolName === TOOL_NAMES.SCRAPE_WEB && getStringField(args, "url") ? (
        <div className="line-clamp-2 text-muted-foreground text-xs">{getStringField(args, "url")}</div>
      ) : null}

      {tool.approvalState.type === "denied" ? (
        <div className="text-destructive text-xs">
          {deniedReason ? t("tool_part.denied_with_reason", { reason: deniedReason }) : t("tool_part.denied")}
        </div>
      ) : null}

      {hasMediaOutput ? (
        <div className="flex flex-wrap gap-1">
          {tool.output.map((part, index) => {
            if (part.type === "image") {
              return (
                <span
                  key={index}
                  className="inline-flex items-center gap-1 rounded-[var(--radius-card-inner)] border border-border bg-background/80 px-2 py-1 text-xs text-muted-foreground"
                >
                  <ImageIcon className="h-3 w-3" />
                  image
                </span>
              );
            }
            if (part.type === "video") {
              return (
                <span
                  key={index}
                  className="inline-flex items-center gap-1 rounded-[var(--radius-card-inner)] border border-border bg-background/80 px-2 py-1 text-xs text-muted-foreground"
                >
                  <Video className="h-3 w-3" />
                  video
                </span>
              );
            }
            if (part.type === "audio") {
              return (
                <span
                  key={index}
                  className="inline-flex items-center gap-1 rounded-[var(--radius-card-inner)] border border-border bg-background/80 px-2 py-1 text-xs text-muted-foreground"
                >
                  <AudioLines className="h-3 w-3" />
                  audio
                </span>
              );
            }
            return null;
          })}
        </div>
      ) : null}
    </div>
  );
}

export function ToolApprovalActions({
  tool,
  onToolApproval,
  t,
}: {
  tool: UIToolPart;
  onToolApproval?: (toolCallId: string, approved: boolean, reason: string, answer?: string) => void | Promise<void>;
  t: TFunction;
}) {
  if (tool.approvalState.type !== "pending" || !onToolApproval || tool.toolName === TOOL_NAMES.ASK_USER) {
    return null;
  }

  const handleApprove = async (event: React.MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    await onToolApproval(tool.toolCallId, true, "");
  };

  const handleDeny = async (event: React.MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    const reason = window.prompt(t("tool_part.deny_reason_prompt"), "");
    if (reason === null) return;
    await onToolApproval(tool.toolCallId, false, reason);
  };

  return (
    <div className="flex items-center gap-1">
      <Button onClick={handleDeny} size="icon-xs" type="button" variant="secondary">
        <X className="h-3.5 w-3.5" />
      </Button>
      <Button onClick={handleApprove} size="icon-xs" type="button" variant="secondary">
        <Check className="h-3.5 w-3.5" />
      </Button>
    </div>
  );
}
