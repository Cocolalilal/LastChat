import { useTranslation } from "react-i18next";

import { AIIcon } from "~/components/ui/ai-icon";
import { UIAvatar } from "~/components/ui/ui-avatar";
import type { AssistantProfile, DisplaySetting, MessageDto, ProviderModel } from "~/types";

export interface ChatMessageAvatarRowProps {
  message: MessageDto;
  hasMessageContent: boolean;
  loading: boolean;
  assistant?: AssistantProfile | null;
  displaySetting?: DisplaySetting | null;
  model?: ProviderModel | null;
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
  loading,
  assistant,
  displaySetting,
  model,
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

  const useAssistantAvatar = assistant?.useAssistantAvatar === true;
  const defaultAssistantName = t("common:quick_jump.role_assistant", { defaultValue: "Assistant" });
  const assistantName = assistant?.name?.trim() || defaultAssistantName;
  const modelName =
    model?.displayName.trim() || model?.modelId.trim() || defaultAssistantName;
  const title = useAssistantAvatar ? assistantName : modelName;
  const canRenderIcon = useAssistantAvatar ? Boolean(assistant) : Boolean(model);
  const canRenderName = Boolean(title);
  if ((!showModelIcon || !canRenderIcon) && (!showModelName || !canRenderName)) {
    return null;
  }

  return (
    <div className="flex w-full justify-start">
      <div className="flex min-w-0 items-center gap-2 rounded-full bg-transparent">
        {showModelIcon && canRenderIcon ? (
          useAssistantAvatar ? (
            <UIAvatar name={assistantName} avatar={assistant?.avatar} className="size-9" />
          ) : (
            <AIIcon
              name={model?.modelId ?? modelName}
              size={34}
              loading={loading}
              className="bg-secondary/90"
              imageClassName="h-[72%] w-[72%]"
            />
          )
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
