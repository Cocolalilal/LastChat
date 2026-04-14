import * as React from "react";

import type { TFunction } from "i18next";
import { Check, ChevronDown, Heart, LoaderCircle, Search } from "~/lib/material-icons";
import { useTranslation } from "react-i18next";

import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { getModelDisplayName } from "~/lib/display";
import { cn } from "~/lib/utils";
import api from "~/services/api";
import type { ModelAbility, ProviderModel } from "~/types";
import { AIIcon } from "~/components/ui/ai-icon";
import { Badge } from "~/components/ui/badge";
import { Button } from "~/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from "~/components/ui/popover";
import { Input } from "~/components/ui/input";
import { ScrollArea } from "~/components/ui/scroll-area";

export interface ModelListProps {
  disabled?: boolean;
  className?: string;
  onChanged?: (model: ProviderModel) => void;
}

interface ModelSection {
  providerId: string;
  providerName: string;
  models: ProviderModel[];
}

const FAVORITE_SECTION_ID = "__favorites__";

function normalizeKeyword(value: string) {
  return value.trim().toLowerCase();
}

function formatModality(model: ProviderModel): string {
  const input = (model.inputModalities ?? []).join("+") || "TEXT";
  const output = (model.outputModalities ?? []).join("+") || "TEXT";
  return `${input} -> ${output}`;
}

function getAbilityLabel(ability: ModelAbility, t: TFunction): string {
  if (ability === "TOOL") {
    return t("model_list.ability_tool");
  }

  return t("model_list.ability_reasoning");
}

interface ModelOptionRowProps {
  model: ProviderModel;
  selected: boolean;
  updating: boolean;
  favorite: boolean;
  disabled: boolean;
  onSelect: (model: ProviderModel) => void | Promise<void>;
  onToggleFavorite: (model: ProviderModel) => void | Promise<void>;
  t: TFunction;
}

function ModelOptionRow({
  model,
  selected,
  updating,
  favorite,
  disabled,
  onSelect,
  onToggleFavorite,
  t,
}: ModelOptionRowProps) {
  const abilities = model.abilities ?? [];

  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled}
      className={cn(
        "flex w-full items-center gap-2 rounded-[var(--radius-card-inner)] border border-border/70 bg-background/90 px-2.5 py-1.5 text-left transition hover:bg-accent",
        disabled && "pointer-events-none opacity-60",
        selected && "border-primary/25 bg-primary/10",
      )}
      onClick={() => {
        if (disabled) {
          return;
        }

        void onSelect(model);
      }}
      onKeyDown={(event) => {
        if (disabled) {
          return;
        }

        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          void onSelect(model);
        }
      }}
    >
      <AIIcon 
        name={model.modelId} 
        size={24} 
        iconUrl={model.iconUrl}
        customIconUri={model.customIconUri}
        providerSlug={model.providerSlug}
      />

      <div className="min-w-0 flex-1">
        <div className="truncate text-xs font-medium leading-tight">
          {getModelDisplayName(model.displayName, model.modelId)}
        </div>
        <div className="text-muted-foreground truncate text-[11px] leading-tight">
          {model.modelId}
        </div>
        <div className="mt-0.5 flex flex-wrap gap-1">
          <Badge variant="outline" className="px-1 py-0 text-[9px]">
            {formatModality(model)}
          </Badge>
          {abilities.map((ability) => (
            <Badge key={ability} variant="secondary" className="px-1 py-0 text-[9px]">
              {getAbilityLabel(ability, t)}
            </Badge>
          ))}
        </div>
      </div>

      {updating ? (
        <LoaderCircle className="text-muted-foreground size-3.5 animate-spin" />
      ) : selected ? (
        <Check className="text-primary size-3.5" />
      ) : (
        <button
          type="button"
          className={favorite ? "text-primary" : "text-muted-foreground hover:text-primary"}
          onClick={(event) => {
            event.stopPropagation();
            void onToggleFavorite(model);
          }}
        >
          <Heart className={cn("size-3.5", favorite && "fill-current")} />
        </button>
      )}
    </div>
  );
}

export function ModelList({ disabled = false, className, onChanged }: ModelListProps) {
  const { t } = useTranslation("input");
  const { settings, currentAssistant } = useCurrentAssistant();

  const [open, setOpen] = React.useState(false);
  const [searchKeywords, setSearchKeywords] = React.useState("");
  const [selectedProviderId, setSelectedProviderId] = React.useState<string | null>(null);
  const [updatingModelId, setUpdatingModelId] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  const currentModelId = currentAssistant?.chatModelId ?? settings?.chatModelId ?? null;
  const favoriteModelIds = settings?.favoriteModels ?? [];
  const favoriteModelIdSet = React.useMemo(() => new Set(favoriteModelIds), [favoriteModelIds]);

  const allModels = React.useMemo(() => {
    if (!settings) {
      return [];
    }

    return settings.providers
      .filter((provider) => provider.enabled)
      .flatMap((provider) => provider.models)
      .filter((model) => model.type === "CHAT");
  }, [settings]);

  const sections = React.useMemo<ModelSection[]>(() => {
    if (!settings) {
      return [];
    }

    const keyword = normalizeKeyword(searchKeywords);

    return settings.providers
      .filter((provider) => provider.enabled)
      .map((provider) => {
        const models = provider.models.filter((model) => {
          if (model.type !== "CHAT") {
            return false;
          }

          if (keyword.length === 0) {
            return true;
          }

          const displayName = getModelDisplayName(model.displayName, model.modelId).toLowerCase();
          const modelId = model.modelId.toLowerCase();
          return displayName.includes(keyword) || modelId.includes(keyword);
        });

        return {
          providerId: provider.id,
          providerName: provider.name,
          models,
        };
      })
      .filter((section) => section.models.length > 0);
  }, [searchKeywords, settings]);

  const selectedSection = React.useMemo(() => {
    if (sections.length === 0) {
      return null;
    }

    return sections.find((section) => section.providerId === selectedProviderId) ?? sections[0];
  }, [sections, selectedProviderId]);
  const filteredModels = React.useMemo(() => sections.flatMap((section) => section.models), [sections]);

  const favoriteModels = React.useMemo(() => {
    return favoriteModelIds
      .map((id) => filteredModels.find((model) => model.id === id))
      .filter((model): model is ProviderModel => model !== undefined);
  }, [favoriteModelIds, filteredModels]);
  const isFavoriteSectionSelected = selectedProviderId === FAVORITE_SECTION_ID;
  const displayedModels = isFavoriteSectionSelected ? favoriteModels : (selectedSection?.models ?? []);

  const currentModel = React.useMemo(
    () => allModels.find((model) => model.id === currentModelId) ?? null,
    [allModels, currentModelId],
  );

  const currentModelLabel = currentModel
    ? getModelDisplayName(currentModel.displayName, currentModel.modelId)
    : t("model_list.select_model");

  React.useEffect(() => {
    if (!open) {
      setSearchKeywords("");
      setError(null);
    }
  }, [open]);

  React.useEffect(() => {
    if (!open) {
      return;
    }

    if (sections.length === 0 && favoriteModels.length === 0) {
      setSelectedProviderId(null);
      return;
    }

    if (selectedProviderId === FAVORITE_SECTION_ID && favoriteModels.length > 0) {
      return;
    }

    if (selectedProviderId && sections.some((section) => section.providerId === selectedProviderId)) {
      return;
    }

    const currentModelSection =
      currentModelId == null ? null : sections.find((section) => section.models.some((model) => model.id === currentModelId));
    setSelectedProviderId(currentModelSection?.providerId ?? (favoriteModels.length > 0 ? FAVORITE_SECTION_ID : sections[0]?.providerId ?? null));
  }, [currentModelId, favoriteModels.length, open, sections, selectedProviderId]);

  React.useEffect(() => {
    if (!disabled) {
      return;
    }

    setOpen(false);
  }, [disabled]);

  const handleSelectModel = React.useCallback(
    async (model: ProviderModel) => {
      if (disabled || !currentAssistant) {
        return;
      }

      if (model.id === currentModelId) {
        setOpen(false);
        return;
      }

      setUpdatingModelId(model.id);
      setError(null);

      try {
        await api.post<{ status: string }>("settings/assistant/model", {
          assistantId: currentAssistant.id,
          modelId: model.id,
        });
        onChanged?.(model);
        setOpen(false);
      } catch (changeError) {
        const message =
          changeError instanceof Error
            ? changeError.message
            : t("model_list.switch_model_failed");
        setError(message);
      } finally {
        setUpdatingModelId(null);
      }
    },
    [currentAssistant, currentModelId, disabled, onChanged, t],
  );

  const handleToggleFavorite = React.useCallback(
    async (model: ProviderModel) => {
      if (disabled || !settings) {
        return;
      }

      const isFavorite = favoriteModelIds.includes(model.id);
      const newFavoriteModels = isFavorite
        ? favoriteModelIds.filter((id) => id !== model.id)
        : [...favoriteModelIds, model.id];

      setUpdatingModelId(model.id);
      setError(null);

      try {
        await api.post<{ status: string }>("settings/favorite-models", {
          modelIds: newFavoriteModels,
        });
      } catch (changeError) {
        const message =
          changeError instanceof Error
            ? changeError.message
            : t("model_list.update_favorites_failed");
        setError(message);
      } finally {
        setUpdatingModelId(null);
      }
    },
    [disabled, favoriteModelIds, settings, t],
  );

  return (
    <Popover
      open={open}
      onOpenChange={(nextOpen) => {
        if (disabled || !currentAssistant) {
          setOpen(false);
          return;
        }

        setOpen(nextOpen);
      }}
    >
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className={cn(
            "h-9 rounded-full border border-border/70 bg-muted/70 px-2.5 text-foreground shadow-none hover:bg-accent hover:text-accent-foreground sm:max-w-64 sm:justify-start sm:gap-2",
            className,
          )}
          disabled={disabled || !currentAssistant}
        >
          <AIIcon
            name={currentModel?.modelId ?? "auto"}
            size={16}
            className="bg-transparent"
            imageClassName="h-full w-full"
            iconUrl={currentModel?.iconUrl}
            customIconUri={currentModel?.customIconUri}
            providerSlug={currentModel?.providerSlug}
          />
          <span className="hidden min-w-0 flex-1 truncate text-left sm:block">
            {currentModelLabel}
          </span>
          <ChevronDown className="hidden size-3.5 shrink-0 sm:block" />
        </Button>
      </PopoverTrigger>

      <PopoverContent align="end" className="w-[min(96vw,30rem)] gap-0 p-0">
        <PopoverHeader className="px-4 pt-4 pb-2">
          <PopoverTitle className="text-sm">{t("model_list.title")}</PopoverTitle>
          <PopoverDescription className="text-xs">
            {t("model_list.description")}
          </PopoverDescription>
        </PopoverHeader>

        <div className="space-y-3 px-3 py-3">
          <div className="relative">
            <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-2 size-3.5 -translate-y-1/2" />
            <Input
              value={searchKeywords}
              onChange={(event) => {
                setSearchKeywords(event.target.value);
              }}
              placeholder={t("model_list.search_placeholder")}
              className="h-8 border-border/70 bg-muted/45 pl-7 text-xs"
            />
          </div>

          {error ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/10 px-2.5 py-1.5 text-[11px] text-destructive">
              {error}
            </div>
          ) : null}

          <div className="h-[24rem]">
            {sections.length === 0 && favoriteModels.length === 0 ? (
              <div className="rounded-[var(--radius-card)] border border-dashed border-border px-3 py-8 text-center text-sm text-muted-foreground">
                {t("model_list.empty")}
              </div>
            ) : (
              <div className="flex h-full min-h-0 flex-col gap-3">
                <div className="overflow-hidden rounded-[var(--radius-card)] border border-border/70 bg-muted/45 p-2">
                  <ScrollArea className="max-h-20 w-full">
                    <div className="flex flex-wrap items-center gap-1.5 pb-1">
                      {favoriteModels.length > 0 && (
                        <button
                          type="button"
                          className={cn(
                            "inline-flex items-center gap-1 rounded-full border border-border/70 bg-background px-2.5 py-1 text-xs transition hover:bg-accent",
                            isFavoriteSectionSelected && "border-primary/25 bg-primary/10 text-primary",
                          )}
                          onClick={() => {
                            setSelectedProviderId(FAVORITE_SECTION_ID);
                          }}
                        >
                          <Heart className={cn("size-3", isFavoriteSectionSelected && "fill-current")} />
                          <span>{t("model_list.favorites")}</span>
                        </button>
                      )}

                      {sections.map((section) => {
                        const selected = section.providerId === selectedProviderId;
                        return (
                          <button
                            key={section.providerId}
                            type="button"
                            className={cn(
                              "inline-flex items-center gap-1 rounded-full border border-border/70 bg-background px-2.5 py-1 text-xs transition hover:bg-accent",
                              selected && "border-primary/25 bg-primary/10 text-primary",
                            )}
                            onClick={() => {
                              setSelectedProviderId(section.providerId);
                            }}
                          >
                            <AIIcon
                              name={section.providerName}
                              size={12}
                              className="bg-transparent"
                              imageClassName="h-full w-full"
                              providerSlug={section.providerName}
                            />
                            <span className="truncate">{section.providerName}</span>
                          </button>
                        );
                      })}
                    </div>
                  </ScrollArea>
                </div>

                <div className="min-h-0 flex-1 overflow-hidden rounded-[var(--radius-card)] border border-border/70 bg-muted/30 p-2">
                  <ScrollArea className="h-full min-h-0">
                    <div className="space-y-1">
                      {displayedModels.map((model) => (
                        <ModelOptionRow
                          key={model.id}
                          model={model}
                          selected={model.id === currentModelId}
                          updating={model.id === updatingModelId}
                          favorite={favoriteModelIdSet.has(model.id)}
                          disabled={disabled || updatingModelId !== null}
                          onSelect={handleSelectModel}
                          onToggleFavorite={handleToggleFavorite}
                          t={t}
                        />
                      ))}
                    </div>
                  </ScrollArea>
                </div>
              </div>
            )}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
