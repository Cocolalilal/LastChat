import * as React from "react";
import { Loader2 } from "~/lib/material-icons";
import { useTranslation } from "react-i18next";

import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from "~/components/ui/drawer";
import { useIsMobile } from "~/hooks/use-mobile";
import {
  getStringField,
  getToolIcon,
  getToolTitle,
  safeJsonParse,
  ToolApprovalActions,
  ToolDetailContent,
  ToolPreviewContent,
} from "~/lib/tool-activity";
import type { DisplaySetting, ToolPart as UIToolPart } from "~/types";

import { ControlledChainOfThoughtStep } from "../chain-of-thought";

interface ToolPartProps {
  tool: UIToolPart;
  displaySetting?: DisplaySetting | null;
  loading?: boolean;
  onToolApproval?: (toolCallId: string, approved: boolean, reason: string, answer?: string) => void | Promise<void>;
  isFirst?: boolean;
  isLast?: boolean;
}

export function ToolPart({
  tool,
  displaySetting,
  loading = false,
  onToolApproval,
  isFirst,
  isLast,
}: ToolPartProps) {
  const { t } = useTranslation("message");
  const isMobile = useIsMobile();
  const [expanded, setExpanded] = React.useState(true);
  const [drawerOpen, setDrawerOpen] = React.useState(false);

  const args = React.useMemo(() => safeJsonParse(tool.input), [tool.input]);
  const title = getToolTitle(tool.toolName, args, t);
  const Icon = getToolIcon(tool.toolName, getStringField(args, "action"));
  const canOpenDrawer = tool.approvalState.type === "pending" || tool.output.length > 0;
  const previewContent = <ToolPreviewContent tool={tool} t={t} />;

  return (
    <>
      <ControlledChainOfThoughtStep
        expanded={expanded}
        onExpandedChange={setExpanded}
        isFirst={isFirst}
        isLast={isLast}
        icon={
          loading ? (
            <Loader2 className="h-4 w-4 animate-spin text-primary" />
          ) : (
            <Icon className="h-4 w-4 text-primary" />
          )
        }
        label={<span className="text-foreground line-clamp-2 text-sm font-medium">{title}</span>}
        extra={<ToolApprovalActions tool={tool} onToolApproval={onToolApproval} t={t} />}
        onClick={canOpenDrawer ? () => setDrawerOpen(true) : undefined}
      >
        {previewContent}
      </ControlledChainOfThoughtStep>

      <Drawer direction={isMobile ? "bottom" : "right"} open={drawerOpen} onOpenChange={setDrawerOpen}>
        <DrawerContent>
          <DrawerHeader>
            <DrawerTitle>{title}</DrawerTitle>
            <DrawerDescription>
              {t("tool_part.tool_name_label", { toolName: tool.toolName })}
            </DrawerDescription>
          </DrawerHeader>

          <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-6">
            <ToolDetailContent
              tool={tool}
              t={t}
              displaySetting={displaySetting}
              onToolApproval={onToolApproval}
            />
          </div>
        </DrawerContent>
      </Drawer>
    </>
  );
}
