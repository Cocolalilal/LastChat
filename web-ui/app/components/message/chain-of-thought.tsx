import * as React from "react";
import {
  AnimatePresence,
  motion,
} from "motion/react";
import { ChevronDown, ChevronRight, ChevronUp } from "~/lib/material-icons";

import { Card } from "~/components/ui/card";
import { getChatLayoutTransition, useChatReducedMotion } from "~/lib/chat-motion";
import { cn } from "~/lib/utils";

interface ChainOfThoughtProps<T> extends React.ComponentProps<typeof Card> {
  steps: T[];
  collapsedVisibleCount?: number;
  renderStep: (
    step: T,
    index: number,
    info: { isFirst: boolean; isLast: boolean },
  ) => React.ReactNode;
  collapseLabel?: React.ReactNode;
  showMoreLabel?: (hiddenCount: number) => React.ReactNode;
}

interface ChainOfThoughtStepBaseProps {
  icon?: React.ReactNode;
  label: React.ReactNode;
  extra?: React.ReactNode;
  onClick?: () => void;
  children?: React.ReactNode;
  contentVisible?: boolean;
  className?: string;
  isFirst?: boolean;
  isLast?: boolean;
}

interface ChainOfThoughtStepProps extends ChainOfThoughtStepBaseProps {
  defaultExpanded?: boolean;
}

interface ControlledChainOfThoughtStepProps extends ChainOfThoughtStepBaseProps {
  expanded: boolean;
  onExpandedChange: (expanded: boolean) => void;
}

function ChainOfThought<T>({
  steps,
  collapsedVisibleCount = 2,
  renderStep,
  collapseLabel = "Collapse",
  showMoreLabel,
  className,
  ...props
}: ChainOfThoughtProps<T>) {
  const [expanded, setExpanded] = React.useState(false);
  const reducedMotion = useChatReducedMotion();
  const canCollapse = steps.length > collapsedVisibleCount;
  const visibleSteps = expanded || !canCollapse ? steps : steps.slice(-collapsedVisibleCount);
  const hiddenCount = Math.max(steps.length - collapsedVisibleCount, 0);

  return (
    <Card
      className={cn(
        "gap-0 rounded-[var(--radius-card)] border-border bg-card px-2 py-2 shadow-none",
        className,
      )}
      {...props}
    >
      {canCollapse && (
        <motion.button
          type="button"
          className="mb-1 flex w-full items-center gap-2 rounded-[var(--radius-card-inner)] px-2 py-1.5 text-left text-sm text-primary outline-none hover:bg-accent focus-visible:ring-[3px] focus-visible:ring-ring/50"
          onClick={() => setExpanded((prev) => !prev)}
          whileHover={reducedMotion ? undefined : { x: 1 }}
          whileTap={reducedMotion ? undefined : { scale: 0.99 }}
        >
          <span className="flex w-6 items-center justify-center">
            <motion.span
              animate={{ rotate: expanded ? 180 : 0 }}
              transition={getChatLayoutTransition(reducedMotion)}
            >
              <ChevronDown className="size-4" />
            </motion.span>
          </span>
          <span>
            {expanded
              ? collapseLabel
              : (showMoreLabel?.(hiddenCount) ?? `Show ${hiddenCount} more steps`)}
          </span>
        </motion.button>
      )}

      <div>
        {visibleSteps.map((step, index) => (
          <React.Fragment key={index}>
            {renderStep(step, index, {
              isFirst: index === 0,
              isLast: index === visibleSteps.length - 1,
            })}
          </React.Fragment>
        ))}
      </div>
    </Card>
  );
}

function ChainOfThoughtStep({
  defaultExpanded = false,
  contentVisible,
  ...props
}: ChainOfThoughtStepProps) {
  const [expanded, setExpanded] = React.useState(defaultExpanded);
  return (
    <ChainOfThoughtStepContent
      {...props}
      expanded={expanded}
      onExpandedChange={setExpanded}
      contentVisible={contentVisible ?? expanded}
    />
  );
}

function ControlledChainOfThoughtStep({
  expanded,
  onExpandedChange,
  contentVisible,
  ...props
}: ControlledChainOfThoughtStepProps) {
  return (
    <ChainOfThoughtStepContent
      {...props}
      expanded={expanded}
      onExpandedChange={onExpandedChange}
      contentVisible={contentVisible ?? expanded}
    />
  );
}

interface ChainOfThoughtStepContentProps extends ChainOfThoughtStepBaseProps {
  expanded: boolean;
  onExpandedChange: (expanded: boolean) => void;
  contentVisible: boolean;
}

function ChainOfThoughtStepContent({
  icon,
  label,
  extra,
  onClick,
  children,
  expanded,
  onExpandedChange,
  contentVisible,
  className,
  isFirst,
  isLast,
}: ChainOfThoughtStepContentProps) {
  const reducedMotion = useChatReducedMotion();
  const hasContent = Boolean(children);
  const clickable = Boolean(onClick || hasContent);

  const handleActivate = () => {
    if (onClick) {
      onClick();
      return;
    }
    if (hasContent) {
      onExpandedChange(!expanded);
    }
  };

  const rowClassName = cn(
    "flex w-full items-center gap-2 px-1 py-2 text-left",
    clickable && "cursor-pointer outline-none",
    className,
  );

  const stepClassName = cn(
    "flex w-full gap-2 rounded-[var(--radius-card-inner)]",
    clickable && "hover:bg-accent focus-within:ring-[3px] focus-within:ring-ring/50",
  );

  const iconContent = icon ? (
    <div className="size-3.5">{icon}</div>
  ) : (
    <div className="bg-muted-foreground size-2 rounded-full" />
  );

  const indicator = onClick ? (
    <ChevronRight className="text-muted-foreground size-4" />
  ) : hasContent ? (
    expanded ? (
      <ChevronUp className="text-muted-foreground size-4" />
    ) : (
      <ChevronDown className="text-muted-foreground size-4" />
    )
  ) : null;

  return (
    <div className={stepClassName}>
      {/* Icon rail with per-step line segments */}
      <div
        className={cn("flex w-6 shrink-0 flex-col items-center", clickable && "cursor-pointer")}
        onClick={clickable ? handleActivate : undefined}
      >
        <div className={cn("h-2 w-px shrink-0", isFirst === false && "bg-border/80")} />
        <div className="flex h-5 shrink-0 items-center justify-center">{iconContent}</div>
        <div className={cn("w-px flex-1", isLast === false && "bg-border/80")} />
      </div>

      {/* Content */}
      <div className="min-w-0 flex-1">
        {clickable ? (
          <button type="button" className={rowClassName} onClick={handleActivate}>
            <span className="min-w-0 flex-1">{label}</span>
            {extra}
            {indicator}
          </button>
        ) : (
          <div className={rowClassName}>
            <span className="min-w-0 flex-1">{label}</span>
            {extra}
            {indicator}
          </div>
        )}

        <AnimatePresence initial={false}>
          {hasContent && contentVisible ? (
            <motion.div
              initial={reducedMotion ? { opacity: 0 } : { opacity: 0, height: 0, y: -4 }}
              animate={{
                opacity: 1,
                height: "auto",
                y: 0,
                transition: reducedMotion
                  ? { duration: 0.01 }
                  : {
                      opacity: { duration: 0.16, ease: "easeOut" },
                      height: getChatLayoutTransition(false),
                      y: getChatLayoutTransition(false),
                    },
              }}
              exit={reducedMotion ? { opacity: 0 } : { opacity: 0, height: 0, y: -4, transition: { duration: 0.12 } }}
              className="overflow-hidden"
            >
              <div className="px-1 pb-2 pt-1">{children}</div>
            </motion.div>
          ) : null}
        </AnimatePresence>
      </div>
    </div>
  );
}

export { ChainOfThought, ChainOfThoughtStep, ControlledChainOfThoughtStep };

export type { ChainOfThoughtProps, ChainOfThoughtStepProps, ControlledChainOfThoughtStepProps };
