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
import { Input } from "~/components/ui/input";
import {
  Popover,
  PopoverContent,
  PopoverHeader,
  PopoverTitle,
  PopoverDescription,
  PopoverTrigger,
} from "~/components/ui/popover";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "~/components/ui/dialog";
import { ScrollArea } from "~/components/ui/scroll-area";
import { PickerErrorAlert } from "./picker-error-alert";

export interface InjectionPickerButtonProps {
  disabled?: boolean;
  className?: string;
  assistantId?: string | null;
  conversationId?: string | null;
  conversationSkillIds?: string[] | null;
  onInvokeSkill: (skillName: string) => void;
}

function canInvoke(skill: ModeInjectionProfile, assistantId: string | undefined) {
  return Boolean(
    assistantId &&
    skill.enabled !== false &&
    skill.userInvocable !== false &&
    (skill.availableForAllAssistants || skill.availableAssistantIds?.includes(assistantId)),
  );
}

function SkillChoices({
  skills,
  onSelect,
  initialQuery = "",
}: {
  skills: ModeInjectionProfile[];
  onSelect: (name: string) => void;
  initialQuery?: string;
}) {
  const [query, setQuery] = React.useState(initialQuery);
  const matches = skills.filter((skill) =>
    `${skill.name} ${skill.description ?? ""}`.toLowerCase().includes(query.toLowerCase()),
  );
  return (
    <div className="space-y-2">
      <Input
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Search skills"
        aria-label="Search skills"
      />
      <ScrollArea className="max-h-80">
        {matches.length === 0 && (
          <p className="p-3 text-sm text-muted-foreground">
            No accessible skills match. Manage access in character settings.
          </p>
        )}
        {matches.map((skill) => (
          <button
            key={skill.id}
            type="button"
            onClick={() => onSelect(skill.name)}
            className="mb-1 w-full rounded-2xl bg-muted p-3 text-left hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring"
          >
            <div className="text-sm font-medium">/{skill.name}</div>
            <div className="mt-1 text-xs text-muted-foreground">{skill.description}</div>
          </button>
        ))}
      </ScrollArea>
    </div>
  );
}

export function SkillCommandDialog({
  value,
  assistantId,
  disabled,
  onValueChange,
}: {
  value: string;
  assistantId?: string | null;
  disabled: boolean;
  onValueChange: (text: string) => void;
}) {
  const { settings, currentAssistant } = useCurrentAssistant();
  const id = assistantId ?? currentAssistant?.id;
  const [dismissed, setDismissed] = React.useState<string | null>(null);
  const command = /^\/\S*$/.test(value) ? value : null;
  React.useEffect(() => {
    if (command === null) setDismissed(null);
  }, [command]);
  const skills = (settings?.modeInjections ?? []).filter((skill) => canInvoke(skill, id));
  return (
    <Dialog
      open={!disabled && command !== null && command !== dismissed}
      onOpenChange={(open) => {
        if (!open) setDismissed(command);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Invoke a skill</DialogTitle>
          <DialogDescription>Choose a skill for your next message.</DialogDescription>
        </DialogHeader>
        <SkillChoices
          key={command}
          skills={skills}
          initialQuery={command?.slice(1)}
          onSelect={(name) => onValueChange(`/${name} `)}
        />
      </DialogContent>
    </Dialog>
  );
}

export function InjectionPickerButton({
  disabled = false,
  className,
  assistantId,
  onInvokeSkill,
}: InjectionPickerButtonProps) {
  const { settings, currentAssistant } = useCurrentAssistant();
  const assistant =
    settings?.assistants.find((item) => item.id === assistantId) ?? currentAssistant;
  const { error, setError, popoverProps } = usePickerPopover(
    Boolean(settings && assistant && !disabled),
  );
  const [tab, setTab] = React.useState<"skills" | "lorebooks">("skills");
  const skills = (settings?.modeInjections ?? []).filter((skill) =>
    canInvoke(skill, assistant?.id),
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
          <PopoverDescription>Invoke a skill or choose lorebooks.</PopoverDescription>
        </PopoverHeader>
        <div className="my-3 flex gap-2">
          <Button
            size="sm"
            variant={tab === "skills" ? "default" : "ghost"}
            onClick={() => setTab("skills")}
          >
            Skills
          </Button>
          <Button
            size="sm"
            variant={tab === "lorebooks" ? "default" : "ghost"}
            onClick={() => setTab("lorebooks")}
          >
            Lorebooks
          </Button>
        </div>
        <PickerErrorAlert error={error} />
        {tab === "skills" ? (
          <SkillChoices
            skills={skills}
            onSelect={(name) => {
              onInvokeSkill(name);
              popoverProps.onOpenChange(false);
            }}
          />
        ) : (
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
        )}
      </PopoverContent>
    </Popover>
  );
}
