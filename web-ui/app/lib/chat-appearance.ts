import type {
  AssistantProfile,
  DisplaySetting,
} from "~/types";

export function resolveEffectiveDisplaySetting(
  displaySetting: DisplaySetting | null | undefined,
  assistant: AssistantProfile | null | undefined,
): DisplaySetting | null {
  if (!displaySetting) {
    return null;
  }

  const ui = assistant?.uiSettings;
  return {
    ...displaySetting,
    showUserAvatar: ui?.showUserAvatar ?? displaySetting.showUserAvatar,
    showModelIcon: ui?.showAssistantAvatar ?? displaySetting.showModelIcon,
    showAssistantBubbles: ui?.showAssistantBubbles ?? displaySetting.showAssistantBubbles,
    showTokenUsage: ui?.showTokenUsage ?? displaySetting.showTokenUsage,
    autoCloseThinking: ui?.autoCloseThinking ?? displaySetting.autoCloseThinking,
    showMessageJumper: ui?.showMessageJumper ?? displaySetting.showMessageJumper,
    messageJumperOnLeft: ui?.messageJumperOnLeft ?? displaySetting.messageJumperOnLeft,
    fontSizeRatio: ui?.fontSizeRatio ?? displaySetting.fontSizeRatio,
    codeBlockAutoWrap: ui?.codeBlockAutoWrap ?? displaySetting.codeBlockAutoWrap,
    codeBlockAutoCollapse: ui?.codeBlockAutoCollapse ?? displaySetting.codeBlockAutoCollapse,
    showContextStacks: ui?.showContextStacks ?? displaySetting.showContextStacks,
    newChatHeaderStyle: ui?.newChatHeaderStyle ?? displaySetting.newChatHeaderStyle,
    newChatContentStyle: ui?.newChatContentStyle ?? displaySetting.newChatContentStyle,
    newChatShowAvatar: ui?.newChatShowAvatar ?? displaySetting.newChatShowAvatar,
  };
}
