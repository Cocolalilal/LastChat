import * as React from "react";
import { useMutation } from "@tanstack/react-query";
import { BookOpen, LoaderCircle } from "~/lib/material-icons";
import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { usePickerPopover } from "~/hooks/use-picker-popover";
import { safeStringArray } from "~/lib/type-guards";
import { extractErrorMessage } from "~/lib/error";
import { cn } from "~/lib/utils";
import api from "~/services/api";
import type { ModeInjectionProfile } from "~/types";
import { Button } from "~/components/ui/button";
import { Checkbox } from "~/components/ui/checkbox";
import { ComposerOption, ComposerPopup } from "./composer-popup";
import { AnimatePresence } from "motion/react";
import {
  Popover,
  PopoverContent,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from "~/components/ui/popover";
import { PickerErrorAlert } from "./picker-error-alert";

export interface InjectionPickerButtonProps {
  disabled?: boolean;
  className?: string;
  assistantId?: string | null;
  conversationId?: string | null;
  conversationSkillIds?: string[] | null;
  onOpenSkills: () => void;
}

function canInvoke(skill: ModeInjectionProfile, assistantId: string | undefined) {
  return Boolean(
    assistantId &&
    skill.name.trim() &&
    skill.enabled !== false &&
    skill.userInvocable !== false &&
    (skill.availableForAllAssistants || skill.availableAssistantIds?.includes(assistantId)),
  );
}

export function SkillCommandPopup({
  value,
  assistantId,
  disabled,
  requested,
  onClose,
  onSelect,
}: {
  value: string;
  assistantId?: string | null;
  disabled: boolean;
  requested: boolean;
  onClose: () => void;
  onSelect: (name: string) => void;
}) {
  const { settings, currentAssistant } = useCurrentAssistant();
  const id = assistantId ?? currentAssistant?.id;
  const command = /^\/\S*$/.test(value) ? value : null;
  const [dismissed, setDismissed] = React.useState<string | null>(null);
  React.useEffect(() => {
    if (command === null) setDismissed(null);
  }, [command]);
  const skills = (settings?.modeInjections ?? []).filter(
    (skill) =>
      canInvoke(skill, id) &&
      skill.name.toLowerCase().includes((command?.slice(1) ?? "").toLowerCase()),
  );
  const visible = !disabled && (requested || (command !== null && command !== dismissed));
  React.useEffect(() => {
    if (!visible) return;
    const dismiss = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setDismissed(command);
        onClose();
      }
    };
    document.addEventListener("keydown", dismiss);
    return () => document.removeEventListener("keydown", dismiss);
  }, [visible, command, onClose]);
  return (
    <AnimatePresence initial={false}>
      {visible && (
        <ComposerPopup key="skill-commands">
          <div className="max-h-60 overflow-y-auto py-1">
            {skills.map((skill) => (
              <ComposerOption
                key={skill.id}
                label={`/${skill.name}`}
                description={skill.description}
                outlined={false}
                onClick={() => onSelect(skill.name)}
              />
            ))}
            {skills.length === 0 && (
              <p className="px-3 py-2 text-sm text-muted-foreground">No matching skills</p>
            )}
          </div>
        </ComposerPopup>
      )}
    </AnimatePresence>
  );
}

export function InjectionPickerButton({
  disabled = false,
  className,
  assistantId,
  onOpenSkills,
}: InjectionPickerButtonProps) {
  const { settings, currentAssistant } = useCurrentAssistant();
  const assistant =
    settings?.assistants.find((item) => item.id === assistantId) ?? currentAssistant;
  const { error, setError, popoverProps } = usePickerPopover(
    Boolean(settings && assistant && !disabled),
  );
  const lorebooks = settings?.lorebooks ?? [];
  const selectedLorebooks = safeStringArray(assistant?.lorebookIds);
  const mutation = useMutation({
    mutationFn: (ids: string[]) =>
      api.post("settings/assistant/injections", {
        assistantId: assistant?.id,
        modeInjectionIds: safeStringArray(assistant?.modeInjectionIds),
        lorebookIds: ids,
      }),
    onError: (error) => setError(extractErrorMessage(error, "Could not update lorebooks")),
    onSuccess: () => setError(null),
  });
  return (
    <Popover {...popoverProps}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={disabled}
          aria-label="Skills and lorebooks"
          className={cn("h-9 rounded-full border border-border/70 bg-muted/70 px-2.5", className)}
        >
          <BookOpen className="size-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80">
        <PopoverHeader>
          <PopoverTitle>Add-ons</PopoverTitle>
        </PopoverHeader>
        <Button
          className="my-3 w-full"
          variant="secondary"
          onClick={() => {
            popoverProps.onOpenChange(false);
            onOpenSkills();
          }}
        >
          Skills
        </Button>
        <div className="mb-2 text-sm font-medium">Lorebooks</div>
        <PickerErrorAlert error={error} />
        <div className="space-y-2">
          {mutation.isPending && <LoaderCircle className="size-4 animate-spin" />}
          {lorebooks.map((item) => (
            <label key={item.id} className="flex items-center gap-3 rounded-2xl bg-muted p-3">
              <Checkbox
                disabled={mutation.isPending}
                checked={selectedLorebooks.includes(item.id)}
                onCheckedChange={(checked) =>
                  mutation.mutate(
                    checked
                      ? [...selectedLorebooks, item.id]
                      : selectedLorebooks.filter((id) => id !== item.id),
                  )
                }
              />
              <span>{item.name}</span>
            </label>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  );
}
