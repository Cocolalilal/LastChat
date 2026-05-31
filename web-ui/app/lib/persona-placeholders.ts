import type { AssistantProfile, DisplaySetting } from "~/types";

export function replacePersonaPlaceholders(
  text: string,
  assistant?: AssistantProfile | null,
  displaySetting?: DisplaySetting | null,
): string {
  const charName = assistant?.name?.trim() || "assistant";
  const userName = displaySetting?.userNickname?.trim() || "user";

  return text
    .replace(/\{\{char\}\}/gi, charName)
    .replace(/\{char\}/gi, charName)
    .replace(/\{\{user\}\}/gi, userName)
    .replace(/\{user\}/gi, userName);
}
