import type { MessageDto } from "./dto";
/**
 * Display settings
 * @see app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt - DisplaySetting
 */
export interface DisplaySetting {
  userNickname: string;
  userAvatar?: AssistantAvatar;
  showUserAvatar: boolean;
  showModelIcon?: boolean;
  showModelName: boolean;
  showAssistantBubbles?: boolean;
  showTokenUsage: boolean;
  showThinkingContent: boolean;
  autoCloseThinking: boolean;
  codeBlockAutoWrap: boolean;
  codeBlockAutoCollapse: boolean;
  showLineNumbers: boolean;
  sendOnEnter: boolean;
  enableAutoScroll: boolean;
  fontSizeRatio: number;
  pasteLongTextAsFile: boolean;
  pasteLongTextThreshold: number;
  showMessageJumper?: boolean;
  messageJumperOnLeft?: boolean;
  showContextStacks?: boolean;
  newChatHeaderStyle?: string;
  newChatContentStyle?: string;
  newChatShowAvatar?: boolean;
  [key: string]: unknown;
}

export interface AssistantTag {
  id: string;
  name: string;
}

export interface AssistantAvatar {
  type?: string;
  content?: string;
  url?: string;
  [key: string]: unknown;
}

export interface AssistantQuickMessage {
  title: string;
  content: string;
}

export interface AssistantUISettings {
  showUserAvatar?: boolean | null;
  showAssistantAvatar?: boolean | null;
  showAssistantBubbles?: boolean | null;
  showTokenUsage?: boolean | null;
  autoCloseThinking?: boolean | null;
  showMessageJumper?: boolean | null;
  messageJumperOnLeft?: boolean | null;
  fontSizeRatio?: number | null;
  codeBlockAutoWrap?: boolean | null;
  codeBlockAutoCollapse?: boolean | null;
  showContextStacks?: boolean | null;
  newChatHeaderStyle?: string | null;
  newChatContentStyle?: string | null;
  newChatShowAvatar?: boolean | null;
  [key: string]: unknown;
}

export interface ModeInjectionProfile {
  id: string;
  name: string;
  description?: string;
  enabled?: boolean;
  alwaysEnabled?: boolean;
  icon?: string | null;
  [key: string]: unknown;
}

export interface LorebookProfile {
  id: string;
  name: string;
  description?: string;
  enabled?: boolean;
  [key: string]: unknown;
}

export interface AssistantProfile {
  id: string;
  chatModelId?: string | null;
  thinkingBudget?: number | null;
  mcpServers?: string[];
  modeInjectionIds?: string[];
  lorebookIds?: string[];
  name: string;
  avatar?: AssistantAvatar;
  useAssistantAvatar?: boolean;
  uiSettings?: AssistantUISettings;
  tags: string[];
  quickMessages?: AssistantQuickMessage[];
  presetMessages?: MessageDto[];
  enableMemory?: boolean;
  enableMemoryConsolidation?: boolean;
  enableHistorySummarization?: boolean;
  autoRegenerateSummary?: boolean;
  maxHistoryMessages?: number | null;
  [key: string]: unknown;
}

export interface McpToolOption {
  enable: boolean;
  name: string;
  description?: string | null;
  needsApproval?: boolean;
  [key: string]: unknown;
}

export interface McpCommonOptions {
  enable: boolean;
  name: string;
  tools: McpToolOption[];
  [key: string]: unknown;
}

export interface McpServerConfig {
  id: string;
  type?: string;
  commonOptions: McpCommonOptions;
  [key: string]: unknown;
}

export type ModelType = "CHAT" | "IMAGE" | "EMBEDDING";
export type ModelModality = "TEXT" | "IMAGE";
export type ModelAbility = "TOOL" | "REASONING";

export interface BuiltInTool {
  type?: string;
  [key: string]: unknown;
}

export interface ProviderModel {
  id: string;
  modelId: string;
  displayName: string;
  type: ModelType;
  inputModalities?: ModelModality[];
  outputModalities?: ModelModality[];
  abilities?: ModelAbility[];
  tools?: BuiltInTool[];
  iconUrl?: string | null;
  customIconUri?: string | null;
  providerSlug?: string | null;
  [key: string]: unknown;
}

export interface ProviderProfile {
  id: string;
  enabled: boolean;
  name: string;
  models: ProviderModel[];
  [key: string]: unknown;
}

export interface SearchServiceOption {
  id: string;
  type?: string;
  [key: string]: unknown;
}

/**
 * App settings (streamed via SSE)
 * @see app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt - Settings
 */
export interface Settings {
  dynamicColor: boolean;
  themeId: string;
  developerMode: boolean;
  displaySetting: DisplaySetting;
  enableWebSearch: boolean;
  favoriteModels: string[];
  chatModelId: string;
  assistantId: string;
  providers: ProviderProfile[];
  assistants: AssistantProfile[];
  assistantTags: AssistantTag[];
  modeInjections?: ModeInjectionProfile[];
  lorebooks?: LorebookProfile[];
  mcpServers: McpServerConfig[];
  searchServices: SearchServiceOption[];
  searchServiceSelected: number;
  [key: string]: unknown;
}
