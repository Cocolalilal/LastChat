import { serverNow } from "~/lib/utils";
import type {
  MessageDto,
  MessageNodeDto,
  ToolPart,
  UIMessageAnnotation,
  UIMessagePart,
} from "~/types";

export interface SelectedNodeMessage {
  node: MessageNodeDto;
  message: MessageDto;
}

export type ActivityType =
  | "reasoning"
  | "ocr"
  | "search"
  | "memory_recall"
  | "python"
  | "workspace"
  | "skill"
  | "mcp"
  | "loading_model"
  | "tool_other";

export type ActivityState =
  | { type: "hidden" }
  | { type: "waiting" }
  | { type: "replying" }
  | { type: "ocr" }
  | { type: "reasoning"; startTimeMs: number }
  | { type: "tool_use"; toolName: string; displayName: string; startTimeMs: number }
  | {
      type: "completed_single";
      activityType: ActivityType;
      durationMs?: number | null;
      count?: number;
    }
  | {
      type: "completed_multiple";
      reasoningDurationMs?: number | null;
      activityTypes: ActivityType[];
      totalActivities: number;
    };

export type TimelineEntry =
  | {
      id: string;
      type: "reasoning";
      reasoning: Extract<UIMessagePart, { type: "reasoning" }>;
      durationMs: number | null;
    }
  | {
      id: string;
      type: "ocr";
      source: "image" | "pdf";
      fileName?: string | null;
      pageNumbers: number[];
      isLoading: boolean;
    }
  | {
      id: string;
      type: "tool";
      tool: ToolPart;
      activityType: ActivityType;
      displayName: string;
      isLoading: boolean;
    };

export interface RawTurn {
  kind: "raw";
  id: string;
  anchorMessageId: string;
  node: MessageNodeDto;
  message: MessageDto;
}

export interface AssistantTurnGroup {
  kind: "assistant";
  id: string;
  anchorMessageId: string;
  items: SelectedNodeMessage[];
  displayMessage: MessageDto;
  actionTarget: SelectedNodeMessage;
  allParts: UIMessagePart[];
  contentParts: UIMessagePart[];
  annotations: UIMessageAnnotation[];
}

export type ConversationTurn = RawTurn | AssistantTurnGroup;

export function getCurrentMessage(node: MessageNodeDto): MessageDto {
  return node.messages[node.selectIndex] ?? node.messages[0];
}

function getLogicalRole(role: string): "USER" | "ASSISTANT" | "SYSTEM" {
  if (role === "TOOL") return "ASSISTANT";
  if (role === "USER" || role === "ASSISTANT") return role;
  return "SYSTEM";
}

export function groupSelectedNodesIntoTurns(
  selectedNodeMessages: SelectedNodeMessage[],
): ConversationTurn[] {
  const turns: ConversationTurn[] = [];
  let currentAssistantItems: SelectedNodeMessage[] = [];

  const flushAssistantItems = () => {
    if (currentAssistantItems.length === 0) return;
    turns.push(buildAssistantTurn(currentAssistantItems));
    currentAssistantItems = [];
  };

  selectedNodeMessages.forEach((item) => {
    const logicalRole = getLogicalRole(item.message.role);
    if (logicalRole === "ASSISTANT") {
      currentAssistantItems.push(item);
      return;
    }

    flushAssistantItems();
    turns.push({
      kind: "raw",
      id: item.message.id,
      anchorMessageId: item.message.id,
      node: item.node,
      message: item.message,
    });
  });

  flushAssistantItems();
  return turns;
}

function buildAssistantTurn(items: SelectedNodeMessage[]): AssistantTurnGroup {
  const allParts = items.flatMap((item) => item.message.parts);
  const contentParts = allParts.filter((part) => part.type !== "reasoning" && part.type !== "tool");
  const actionTarget =
    [...items]
      .reverse()
      .find((item) =>
        item.message.parts.some(
          (part) =>
            part.type === "text" ||
            part.type === "image" ||
            part.type === "video" ||
            part.type === "audio" ||
            part.type === "document",
        ),
      ) ?? items[items.length - 1];
  const displayMessage =
    actionTarget.message.role === "ASSISTANT"
      ? actionTarget.message
      : { ...actionTarget.message, role: "ASSISTANT" };

  return {
    kind: "assistant",
    id: items.map((item) => item.message.id).join(":"),
    anchorMessageId: items[0]?.message.id ?? actionTarget.message.id,
    items,
    displayMessage,
    actionTarget,
    allParts,
    contentParts,
    annotations: items.flatMap((item) => item.message.annotations ?? []),
  };
}

export function categorizeToolName(toolName: string, args = ""): ActivityType {
  const normalized = resolveActivityToolName(toolName, args);
  if (normalized === "search_web" || normalized === "scrape_web") return "search";
  if (normalized === "search_memory") return "memory_recall";
  if (
    normalized === "eval_python" ||
    normalized === "pip_install" ||
    normalized === "write_sandbox_file" ||
    normalized === "read_sandbox_file" ||
    normalized === "list_sandbox_files" ||
    normalized === "delete_sandbox_file"
  ) {
    return "python";
  }
  if (
    normalized === "workspace_read_file" ||
    normalized === "workspace_write_file" ||
    normalized === "workspace_edit_file" ||
    normalized === "workspace_shell"
  ) {
    return "workspace";
  }
  if (normalized === "manage_skills") return "skill";
  if (normalized.startsWith("mcp_")) return "mcp";
  return "tool_other";
}

export function getToolDisplayName(toolName: string): string {
  const normalized = resolveActivityToolName(toolName, "");
  switch (normalized) {
    case "search_web":
      return "Searching web";
    case "scrape_web":
      return "Reading page";
    case "search_memory":
      return "Searching memory";
    case "eval_python":
      return "Running Python";
    case "pip_install":
      return "Installing packages";
    case "write_sandbox_file":
      return "Writing file";
    case "read_sandbox_file":
      return "Reading file";
    case "list_sandbox_files":
      return "Listing files";
    case "delete_sandbox_file":
      return "Deleting file";
    case "workspace_read_file":
      return "Reading workspace file";
    case "workspace_write_file":
      return "Writing workspace file";
    case "workspace_edit_file":
      return "Editing workspace file";
    case "workspace_shell":
      return "Running workspace command";
    case "manage_skills":
      return "Managing skills";
    default:
      return toolName.replaceAll("_", " ").replace(/^\w/, (char) => char.toUpperCase());
  }
}

const pythonToolNames = new Set([
  "eval_python",
  "pip_install",
  "write_sandbox_file",
  "read_sandbox_file",
  "list_sandbox_files",
  "delete_sandbox_file",
]);

const workspaceToolNames = new Set([
  "workspace_read_file",
  "workspace_write_file",
  "workspace_edit_file",
  "workspace_shell",
]);

export function resolveActivityToolName(toolName: string, args: string): string {
  const normalized = toolName.trim();
  if (pythonToolNames.has(normalized) || workspaceToolNames.has(normalized)) {
    return normalized;
  }
  const workspaceMatch = [...workspaceToolNames].find((name) => name.startsWith(normalized));
  if (normalized.length >= 10 && workspaceMatch) {
    return workspaceMatch;
  }
  const pythonMatch = [...pythonToolNames].find((name) => name.startsWith(normalized));
  if (normalized.length >= 3 && pythonMatch) {
    return "eval_python";
  }
  if (
    normalized.length === 0 &&
    (args.includes('"code"') || args.includes("'code'") || args.includes('\\"code\\"'))
  ) {
    return "eval_python";
  }
  return normalized;
}

function getOcrAnnotations(annotations: UIMessageAnnotation[]) {
  return annotations.filter(
    (annotation): annotation is Extract<UIMessageAnnotation, { type: "ocr_activity" }> =>
      annotation.type === "ocr_activity",
  );
}

export function buildTimelineEntries(
  parts: UIMessagePart[],
  annotations: UIMessageAnnotation[] = [],
  loading = false,
): TimelineEntry[] {
  const entries: TimelineEntry[] = [];
  const toolEntryIndexById = new Map<string, number>();

  getOcrAnnotations(annotations).forEach((annotation, index) => {
    entries.push({
      id: `ocr-${index}`,
      type: "ocr",
      source: annotation.source,
      fileName: annotation.fileName ?? null,
      pageNumbers: annotation.pageNumbers,
      isLoading: loading,
    });
  });

  parts.forEach((part, index) => {
    if (part.type === "reasoning") {
      entries.push({
        id: part.createdAt ?? `reasoning-${index}`,
        type: "reasoning",
        reasoning: part,
        durationMs: getReasoningDurationMs(part),
      });
      return;
    }

    if (part.type === "tool") {
      const entry: TimelineEntry = {
        id: part.toolCallId || `tool-${index}`,
        type: "tool",
        tool: part,
        activityType: categorizeToolName(part.toolName, part.input),
        displayName: getToolDisplayName(part.toolName),
        isLoading: part.output.length === 0,
      };

      const toolCallId = part.toolCallId?.trim();
      if (toolCallId) {
        const existingIndex = toolEntryIndexById.get(toolCallId);
        if (existingIndex != null) {
          entries[existingIndex] = entry;
          return;
        }
        toolEntryIndexById.set(toolCallId, entries.length);
      }

      entries.push(entry);
    }
  });

  return entries;
}

export function deriveActivityState(
  parts: UIMessagePart[],
  annotations: UIMessageAnnotation[] = [],
  loading = false,
): ActivityState {
  const reasoningParts = parts.filter(
    (part): part is Extract<UIMessagePart, { type: "reasoning" }> => part.type === "reasoning",
  );
  const toolParts = parts.filter(
    (part): part is Extract<UIMessagePart, { type: "tool" }> => part.type === "tool",
  );
  const ocrAnnotations = getOcrAnnotations(annotations);
  let lastActivityIndex = -1;
  for (let index = parts.length - 1; index >= 0; index -= 1) {
    const part = parts[index];
    if (part.type === "reasoning" || part.type === "tool") {
      lastActivityIndex = index;
      break;
    }
  }
  const hasRecentText = parts
    .slice(lastActivityIndex + 1)
    .some((part) => part.type === "text" && part.text.trim().length > 0);

  if (!loading) {
    const totalReasoningDuration = reasoningParts.reduce((total, part) => {
      const duration = getReasoningDurationMs(part);
      return total + (duration ?? 0);
    }, 0);
    const toolTypes = [
      ...new Set(toolParts.map((part) => categorizeToolName(part.toolName, part.input))),
    ];
    const hasReasoning = totalReasoningDuration > 0;
    const hasOcr = ocrAnnotations.length > 0;
    const activityCount = (hasReasoning ? 1 : 0) + (hasOcr ? 1 : 0) + toolTypes.length;

    if (activityCount === 0) return { type: "hidden" };
    if (activityCount === 1 && hasReasoning) {
      return {
        type: "completed_single",
        activityType: "reasoning",
        durationMs: totalReasoningDuration,
        count: reasoningParts.length,
      };
    }
    if (activityCount === 1 && hasOcr) {
      return {
        type: "completed_single",
        activityType: "ocr",
        count: ocrAnnotations.length,
      };
    }
    if (activityCount === 1 && toolTypes.length === 1) {
      return {
        type: "completed_single",
        activityType: toolTypes[0],
        count: toolParts.length,
      };
    }

    return {
      type: "completed_multiple",
      reasoningDurationMs: hasReasoning ? totalReasoningDuration : null,
      activityTypes: [...(hasOcr ? ["ocr" as const] : []), ...toolTypes],
      totalActivities: activityCount,
    };
  }

  const activeReasoning = [...reasoningParts].reverse().find((part) => part.finishedAt == null);
  if (activeReasoning?.createdAt) {
    const startTimeMs = Date.parse(activeReasoning.createdAt);
    if (!Number.isNaN(startTimeMs)) {
      return { type: "reasoning", startTimeMs };
    }
  }

  const activeTool = [...toolParts].reverse().find((part) => part.output.length === 0);
  if (activeTool) {
    return {
      type: "tool_use",
      toolName: activeTool.toolName,
      displayName: getToolDisplayName(activeTool.toolName),
      startTimeMs: serverNow(),
    };
  }

  if (hasRecentText) {
    return { type: "replying" };
  }

  if (ocrAnnotations.length > 0) {
    return { type: "ocr" };
  }

  return { type: "waiting" };
}

export function getReasoningDurationMs(
  part: Extract<UIMessagePart, { type: "reasoning" }>,
): number | null {
  if (!part.createdAt) return null;
  const start = Date.parse(part.createdAt);
  if (Number.isNaN(start)) return null;

  const end = part.finishedAt ? Date.parse(part.finishedAt) : serverNow();
  if (Number.isNaN(end) || end <= start) return null;
  return end - start;
}
