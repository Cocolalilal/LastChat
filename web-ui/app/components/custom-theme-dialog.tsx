import * as React from "react";
import { useTranslation } from "react-i18next";

import { Button } from "~/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "~/components/ui/dialog";
import { Textarea } from "~/components/ui/textarea";
import type { CustomThemeCss } from "~/components/theme-provider";

const CUSTOM_THEME_EDITOR_ROWS = 14;

function mergeThemeCss(light: string, dark: string): string {
  if (light && dark) {
    return `${light}\n\n${dark}`;
  }

  return light || dark || "";
}

type CustomThemeDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialCss: CustomThemeCss;
  onSave: (css: CustomThemeCss) => void;
};

export function CustomThemeDialog({
  open,
  onOpenChange,
  initialCss,
  onSave,
}: CustomThemeDialogProps) {
  const { t } = useTranslation();
  const [cssDraft, setCssDraft] = React.useState(() =>
    mergeThemeCss(initialCss.light, initialCss.dark),
  );

  React.useEffect(() => {
    if (!open) {
      return;
    }

    setCssDraft(mergeThemeCss(initialCss.light, initialCss.dark));
  }, [initialCss.light, initialCss.dark, open]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85svh] max-w-3xl overflow-hidden border-border bg-popover p-4">
        <DialogHeader className="px-1 pt-0 pb-1">
          <DialogTitle>{t("custom_theme_dialog.title")}</DialogTitle>
          <DialogDescription>{t("custom_theme_dialog.description")}</DialogDescription>
        </DialogHeader>

        <div className="min-h-0 flex-1 overflow-y-auto px-1 py-1">
          <div className="space-y-3">
            <div className="space-y-2 rounded-[var(--radius-card)] border border-border/70 bg-muted/35 p-3">
              <div className="text-sm font-medium">{t("custom_theme_dialog.theme_variables")}</div>
              <Textarea
                value={cssDraft}
                onChange={(event) => {
                  setCssDraft(event.target.value);
                }}
                placeholder={t("custom_theme_dialog.theme_placeholder")}
                rows={CUSTOM_THEME_EDITOR_ROWS}
                className="field-sizing-fixed h-56 max-h-56 overflow-y-auto rounded-[var(--radius-card-inner)] border-border/70 bg-background font-mono text-xs"
              />
            </div>

            <div className="rounded-[var(--radius-card)] border border-border/70 bg-muted/30 px-4 py-3 text-sm text-muted-foreground">
              {t("custom_theme_dialog.tip")}{" "}
              <a
                href="https://tweakcn.com/"
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary hover:underline"
              >
                https://tweakcn.com/
              </a>
            </div>
          </div>
        </div>

        <DialogFooter className="px-1 pt-2 pb-0">
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              onOpenChange(false);
            }}
          >
            {t("custom_theme_dialog.cancel")}
          </Button>
          <Button
            type="button"
            onClick={() => {
              let lightCss = cssDraft.match(/:root\s*\{[\s\S]*?\}/)?.[0]?.trim() ?? "";
              let darkCss =
                cssDraft.match(/(?:\.dark|:root\.dark)\s*\{[\s\S]*?\}/)?.[0]?.trim() ?? "";

              if (!lightCss && !darkCss && cssDraft.trim()) {
                lightCss = cssDraft.trim();
              }

              onSave({
                light: lightCss,
                dark: darkCss,
              });
              onOpenChange(false);
            }}
          >
            {t("custom_theme_dialog.save_and_apply")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
