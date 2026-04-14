import * as React from "react";

import { useMutation } from "@tanstack/react-query";
import { BookOpen, LoaderCircle, Code2, Image as ImageIcon, Palette, Wrench, Terminal, Search, Memory } from "~/lib/material-icons";
import { useTranslation } from "react-i18next";

import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { usePickerPopover } from "~/hooks/use-picker-popover";
import { getDisplayName } from "~/lib/display";
import { extractErrorMessage } from "~/lib/error";
import { safeStringArray } from "~/lib/type-guards";
import { cn } from "~/lib/utils";
import api from "~/services/api";
import type { LorebookProfile, ModeInjectionProfile } from "~/types";
import { Button } from "~/components/ui/button";
import { Checkbox } from "~/components/ui/checkbox";
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from "~/components/ui/popover";
import { ScrollArea } from "~/components/ui/scroll-area";

import { PickerErrorAlert } from "./picker-error-alert";

export interface InjectionPickerButtonProps {
  disabled?: boolean;
  className?: string;
  assistantId?: string | null;
  conversationId?: string | null;
  conversationSkillIds?: string[] | null;
}

function getSkillIcon(iconName: string | null | undefined) {
  if (!iconName) return null;
  switch (iconName.toLowerCase()) {
    case "code": return Code2;
    case "image": return ImageIcon;
    case "palette": return Palette;
    case "terminal": return Terminal;
    case "build":
    case "wrench": return Wrench;
    case "search": return Search;
    case "memory": return Memory;
    default: return BookOpen;
  }
}

function getModeInjections(source: unknown): ModeInjectionProfile[] {
  if (!Array.isArray(source)) {
    return [];
  }

  return source.filter((item): item is ModeInjectionProfile =>
    Boolean(item && typeof item === "object" && typeof item.id === "string"),
  );
}

function getLorebooks(source: unknown): LorebookProfile[] {
  if (!Array.isArray(source)) {
    return [];
  }

  return source.filter((item): item is LorebookProfile =>
    Boolean(item && typeof item === "object" && typeof item.id === "string"),
  );
}

export function InjectionPickerButton({
  disabled = false,
  className,
  assistantId = null,
  conversationId = null,
  conversationSkillIds = null,
}: InjectionPickerButtonProps) {
  const { t } = useTranslation("input");
  const { settings, currentAssistant } = useCurrentAssistant();

  const assistant = React.useMemo(() => {
    if (!settings || !assistantId) {
      return currentAssistant;
    }

    return settings.assistants.find((item) => item.id === assistantId) ?? currentAssistant;
  }, [assistantId, currentAssistant, settings]);

  const [activeTab, setActiveTab] = React.useState<"mode" | "lorebook">("mode");

  const canUse = Boolean(settings && assistant && !disabled);
  const { error, setError, popoverProps } = usePickerPopover(canUse);

  const modeInjections = React.useMemo(
    () => getModeInjections(settings?.modeInjections),
    [settings?.modeInjections],
  );
  const lorebooks = React.useMemo(() => getLorebooks(settings?.lorebooks), [settings?.lorebooks]);

  const modeInjectionIdSet = React.useMemo(
    () => new Set(modeInjections.map((item) => item.id)),
    [modeInjections],
  );
  const lorebookIdSet = React.useMemo(() => new Set(lorebooks.map((item) => item.id)), [lorebooks]);

  const assistantModeInjectionIds = React.useMemo(
    () => safeStringArray(assistant?.modeInjectionIds),
    [assistant?.modeInjectionIds],
  );
  const selectedModeInjectionIds = React.useMemo(() => {
    const currentConversationSkillIds = safeStringArray(conversationSkillIds);
    if (conversationId && currentConversationSkillIds.length > 0) {
      return currentConversationSkillIds;
    }

    return assistantModeInjectionIds;
  }, [assistantModeInjectionIds, conversationId, conversationSkillIds]);
  const selectedLorebookIds = React.useMemo(
    () => safeStringArray(assistant?.lorebookIds),
    [assistant?.lorebookIds],
  );

  const selectedCount = selectedModeInjectionIds.length + selectedLorebookIds.length;
  const hasData = modeInjections.length > 0 || lorebooks.length > 0;

  React.useEffect(() => {
    if (!canUse || !hasData) {
      popoverProps.onOpenChange(false);
    }
  }, [canUse, hasData, popoverProps]);

  React.useEffect(() => {
    if (modeInjections.length === 0 && lorebooks.length > 0) {
      setActiveTab("lorebook");
      return;
    }

    if (lorebooks.length === 0 && modeInjections.length > 0) {
      setActiveTab("mode");
    }
  }, [lorebooks.length, modeInjections.length]);

  const updateModeInjectionsMutation = useMutation({
    mutationFn: ({
      assistantId,
      modeInjectionIds,
      lorebookIds,
      conversationId,
    }: {
      assistantId: string;
      modeInjectionIds: string[];
      lorebookIds: string[];
      conversationId?: string | null;
      key: string;
    }) => {
      if (conversationId) {
        return api.post<{ status: string }>(`conversations/${conversationId}/skills`, {
          skillIds: modeInjectionIds,
        });
      }

      return api.post<{ status: string }>("settings/assistant/injections", {
        assistantId,
        modeInjectionIds,
        lorebookIds,
      });
    },
    onError: (updateError) => {
      setError(extractErrorMessage(updateError, t("injection.update_failed")));
    },
    onSuccess: () => setError(null),
  });

  const updateLorebooksMutation = useMutation({
    mutationFn: ({
      assistantId,
      modeInjectionIds,
      lorebookIds,
    }: {
      assistantId: string;
      modeInjectionIds: string[];
      lorebookIds: string[];
      key: string;
    }) =>
      api.post<{ status: string }>("settings/assistant/injections", {
        assistantId,
        modeInjectionIds,
        lorebookIds,
      }),
    onError: (updateError) => {
      setError(extractErrorMessage(updateError, t("injection.update_failed")));
    },
    onSuccess: () => setError(null),
  });

  const handleToggleModeInjection = React.useCallback(
    (id: string, checked: boolean) => {
      if (!canUse || !assistant) return;

      const nextModeIds = new Set(
        selectedModeInjectionIds.filter((item) => modeInjectionIdSet.has(item)),
      );
      const nextLorebookIds = selectedLorebookIds.filter((item) => lorebookIdSet.has(item));

      if (checked) {
        nextModeIds.add(id);
      } else {
        nextModeIds.delete(id);
      }

      updateModeInjectionsMutation.mutate({
        assistantId: assistant.id,
        modeInjectionIds: Array.from(nextModeIds),
        lorebookIds: nextLorebookIds,
        conversationId,
        key: `mode:${id}`,
      });
    },
    [
      assistant,
      canUse,
      conversationId,
      lorebookIdSet,
      modeInjectionIdSet,
      selectedLorebookIds,
      selectedModeInjectionIds,
      updateModeInjectionsMutation,
    ],
  );

  const handleToggleLorebook = React.useCallback(
    (id: string, checked: boolean) => {
      if (!canUse || !assistant) return;

      const nextLorebookIds = new Set(
        selectedLorebookIds.filter((item) => lorebookIdSet.has(item)),
      );

      if (checked) {
        nextLorebookIds.add(id);
      } else {
        nextLorebookIds.delete(id);
      }

      updateLorebooksMutation.mutate({
        assistantId: assistant.id,
        modeInjectionIds: assistantModeInjectionIds.filter((item) => modeInjectionIdSet.has(item)),
        lorebookIds: Array.from(nextLorebookIds),
        key: `lorebook:${id}`,
      });
    },
    [
      assistant,
      assistantModeInjectionIds,
      canUse,
      lorebookIdSet,
      modeInjectionIdSet,
      selectedLorebookIds,
      updateLorebooksMutation,
    ],
  );

  if (!hasData) {
    return null;
  }

  return (
    <Popover {...popoverProps}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={!canUse || updateModeInjectionsMutation.isPending || updateLorebooksMutation.isPending}
          className={cn(
            "h-9 rounded-full border border-border/70 bg-muted/70 px-2.5 text-foreground shadow-none hover:bg-accent hover:text-accent-foreground",
            selectedCount > 0 && "border-primary/20 bg-primary/10 text-primary hover:bg-primary/18",
            className,
          )}
        >
          {updateModeInjectionsMutation.isPending || updateLorebooksMutation.isPending ? (
            <LoaderCircle className="size-4 animate-spin" />
          ) : (
            <BookOpen className="size-4" />
          )}
          {selectedCount > 0 ? (
            <span className="rounded-full bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary">
              {selectedCount}
            </span>
          ) : null}
        </Button>
      </PopoverTrigger>

      <PopoverContent align="end" className="w-[min(92vw,26rem)] gap-0 p-0">
        <PopoverHeader className="px-6 pt-4 pb-2">
          <PopoverTitle>{t("injection.title")}</PopoverTitle>
          <PopoverDescription>{t("injection.description")}</PopoverDescription>
        </PopoverHeader>

        <div className="space-y-4 px-4 py-4">
          <PickerErrorAlert error={error} />

          <div className="inline-flex rounded-full border border-border/70 bg-muted/45 p-1">
            <button
              type="button"
              className={cn(
                "rounded-full px-3 py-1 text-xs transition",
                activeTab === "mode"
                  ? "bg-background text-foreground shadow-sm"
                  : "text-muted-foreground hover:bg-accent",
              )}
              onClick={() => {
                setActiveTab("mode");
              }}
              disabled={modeInjections.length === 0}
            >
              {t("injection.tab_mode")}
            </button>
            <button
              type="button"
              className={cn(
                "rounded-full px-3 py-1 text-xs transition",
                activeTab === "lorebook"
                  ? "bg-background text-foreground shadow-sm"
                  : "text-muted-foreground hover:bg-accent",
              )}
              onClick={() => {
                setActiveTab("lorebook");
              }}
              disabled={lorebooks.length === 0}
            >
              {t("injection.tab_lorebook")}
            </button>
          </div>

          <div className="overflow-hidden rounded-[var(--radius-card)] border border-border/70 bg-muted/30 p-2">
            <ScrollArea className="h-[16rem] pr-3">
              {activeTab === "mode" ? (
                modeInjections.length > 0 ? (
                  <div className="space-y-2">
                    {modeInjections.map((item) => {
                      const checked = selectedModeInjectionIds.includes(item.id);
                      const switching =
                        updateModeInjectionsMutation.isPending &&
                        updateModeInjectionsMutation.variables?.key === `mode:${item.id}`;

                      return (
                        <label
                          key={item.id}
                          className={cn(
                            "flex cursor-pointer items-center gap-3 rounded-[var(--radius-card-inner)] border border-border/70 bg-background px-3 py-3 transition hover:bg-accent",
                            (checked || item.alwaysEnabled) && "border-primary/25 bg-primary/10",
                            (item.enabled === false || item.alwaysEnabled) && "opacity-80"
                          )}
                        >
                          {switching ? (
                            <LoaderCircle className="size-4 animate-spin" />
                          ) : (
                            <Checkbox
                              checked={checked || item.alwaysEnabled}
                              disabled={disabled || updateModeInjectionsMutation.isPending || item.enabled === false || item.alwaysEnabled}
                              onCheckedChange={(nextChecked) => {
                                handleToggleModeInjection(item.id, Boolean(nextChecked));
                              }}
                            />
                          )}
                        
                        {(() => {
                           const SkillIcon = getSkillIcon(item.icon);
                           return SkillIcon ? <SkillIcon className="size-5 shrink-0 text-muted-foreground" /> : null;
                        })()}

                        <div className="min-w-0 flex-1">
                          <div className="truncate text-sm font-medium">
                            {getDisplayName(item.name, t("injection.unnamed_mode"))}
                          </div>
                          {typeof item.description === "string" && item.description.trim().length > 0 ? (
                            <div className="text-muted-foreground mt-0.5 line-clamp-2 text-xs">
                              {item.description}
                            </div>
                          ) : null}
                          {item.enabled === false ? (
                            <div className="text-muted-foreground mt-0.5 text-xs text-destructive">
                              {t("injection.disabled")}
                            </div>
                          ) : item.alwaysEnabled ? (
                            <div className="text-muted-foreground mt-0.5 text-xs text-primary">
                              {t("injection.always_enabled", "Always Enabled")}
                            </div>
                          ) : null}
                          </div>
                        </label>
                      );
                    })}
                  </div>
                ) : (
                  <div className="rounded-md border border-dashed px-3 py-8 text-center text-sm text-muted-foreground">
                    {t("injection.empty_mode")}
                  </div>
                )
              ) : lorebooks.length > 0 ? (
                <div className="space-y-2">
                  {lorebooks.map((item) => {
                    const checked = selectedLorebookIds.includes(item.id);
                    const switching =
                      updateLorebooksMutation.isPending &&
                      updateLorebooksMutation.variables?.key === `lorebook:${item.id}`;

                    return (
                      <label
                        key={item.id}
                        className={cn(
                          "flex cursor-pointer items-center gap-3 rounded-[var(--radius-card-inner)] border border-border/70 bg-background px-3 py-3 transition hover:bg-accent",
                          checked && "border-primary/25 bg-primary/10",
                          item.enabled === false && "opacity-80"
                        )}
                      >
                        {switching ? (
                          <LoaderCircle className="size-4 animate-spin" />
                        ) : (
                          <Checkbox
                            checked={checked}
                            disabled={disabled || updateLorebooksMutation.isPending || item.enabled === false}
                            onCheckedChange={(nextChecked) => {
                              handleToggleLorebook(item.id, Boolean(nextChecked));
                            }}
                          />
                        )}

                        <div className="min-w-0">
                          <div className="truncate text-sm font-medium">
                            {getDisplayName(item.name, t("injection.unnamed_lorebook"))}
                          </div>
                          {typeof item.description === "string" && item.description.trim().length > 0 ? (
                            <div className="text-muted-foreground mt-0.5 line-clamp-2 text-xs">
                              {item.description}
                            </div>
                          ) : null}
                          {item.enabled === false ? (
                            <div className="text-muted-foreground mt-0.5 text-xs text-destructive">
                              {t("injection.disabled")}
                            </div>
                          ) : null}
                        </div>
                      </label>
                    );
                  })}
                </div>
              ) : (
                <div className="rounded-md border border-dashed px-3 py-8 text-center text-sm text-muted-foreground">
                  {t("injection.empty_lorebook")}
                </div>
              )}
            </ScrollArea>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
