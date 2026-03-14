import { useTranslation } from "react-i18next";

import { UIAvatar } from "~/components/ui/ui-avatar";
import type { AssistantProfile, DisplaySetting, MessageDto } from "~/types";

export interface ChatMessageAvatarRowProps {
  message: MessageDto;
  hasMessageContent: boolean;
  assistant?: AssistantProfile | null;
  displaySetting?: DisplaySetting | null;
}

function formatMessageTimestamp(createdAt: string, locale?: string): string | null {
  const timestamp = Date.parse(createdAt);
  if (Number.isNaN(timestamp)) return null;

  return new Intl.DateTimeFormat(locale || undefined, {
    dateStyle: "medium",
    timeStyle: "medium",
  }).format(timestamp);
}

export function ChatMessageAvatarRow({
  message,
  hasMessageContent,
  assistant,
  displaySetting,
}: ChatMessageAvatarRowProps) {
  const { t, i18n } = useTranslation(["common", "page"]);

  if (!hasMessageContent) {
    return null;
  }

  const createdAtLabel = formatMessageTimestamp(message.createdAt, i18n.language);

  if (message.role === "USER") {
    return null;
  }

  if (message.role !== "ASSISTANT") {
    return null;
  }

  const showModelIcon = displaySetting?.showModelIcon !== false;
  const showModelName = displaySetting?.showModelName === true;
  if (!showModelIcon && !showModelName) {
    return null;
  }

  const defaultAssistantName = t("common:quick_jump.role_assistant", { defaultValue: "Assistant" });
  const assistantName = assistant?.name?.trim() || defaultAssistantName;
  const title = assistantName;
  const canRenderIcon = true;
  const canRenderName = true;
  if ((!showModelIcon || !canRenderIcon) && (!showModelName || !canRenderName)) {
    return null;
  }

  return (
    <div className="flex w-full justify-start">
      <div className="flex min-w-0 items-center gap-2 rounded-full bg-transparent">
        {showModelIcon && canRenderIcon ? (
          <UIAvatar name={assistantName} avatar={assistant?.avatar} className="size-9" />
        ) : null}
        {showModelName && canRenderName ? (
          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium text-foreground/85">{title}</div>
            {createdAtLabel ? (
              <div className="truncate text-[11px] text-muted-foreground">{createdAtLabel}</div>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}
