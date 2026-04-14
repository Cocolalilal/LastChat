import * as React from "react";
import { useTranslation } from "react-i18next";
import { Streamdown, useIsCodeFenceIncomplete } from "streamdown";
import { cjk } from "@streamdown/cjk";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import rehypeRaw from "rehype-raw";
import { cn } from "~/lib/utils";
import { resolveFileUrl } from "~/lib/files";
import { getCodePreviewLanguage } from "~/components/workbench/code-preview-language";
import { useOptionalWorkbench } from "~/components/workbench/workbench-context";
import type { DisplaySetting } from "~/types";
import { CodeBlock } from "./code-block";
import remarkRp from "./remark-rp";
import "katex/dist/katex.min.css";
import "./markdown.css";
import "streamdown/styles.css";

// Regex patterns for preprocessing
const INLINE_LATEX_REGEX = /\\\((.+?)\\\)/g;
const BLOCK_LATEX_REGEX = /\\\[(.+?)\\\]/gs;
const CODE_BLOCK_REGEX = /```[\s\S]*?```|`[^`\n]*`/g;

type IncompleteFence = {
  contentBeforeFence: string;
  code: string;
  language: string;
};

// Preprocess markdown content
function preProcess(content: string): string {
  // Find all code block positions
  const codeBlocks: { start: number; end: number }[] = [];
  let match;
  const codeBlockRegex = new RegExp(CODE_BLOCK_REGEX.source, "g");
  while ((match = codeBlockRegex.exec(content)) !== null) {
    codeBlocks.push({ start: match.index, end: match.index + match[0].length });
  }

  // Check if position is inside a code block
  const isInCodeBlock = (position: number): boolean => {
    return codeBlocks.some((range) => position >= range.start && position < range.end);
  };

  // Replace inline formulas \( ... \) to $ ... $, skip code blocks
  let result = content.replace(
    new RegExp(INLINE_LATEX_REGEX.source, "g"),
    (match, group1, offset) => {
      if (isInCodeBlock(offset)) {
        return match;
      }
      return `$${group1}$`;
    },
  );

  // Replace block formulas \[ ... \] to $$ ... $$, skip code blocks
  result = result.replace(new RegExp(BLOCK_LATEX_REGEX.source, "gs"), (match, group1, offset) => {
    if (isInCodeBlock(offset)) {
      return match;
    }
    return `$$${group1}$$`;
  });

  return result;
}

function extractTrailingIncompleteFence(content: string): IncompleteFence | null {
  const fenceMatches = [...content.matchAll(/```/g)];
  if (fenceMatches.length === 0 || fenceMatches.length % 2 === 0) {
    return null;
  }

  const lastFence = fenceMatches.at(-1);
  const lastFenceIndex = lastFence?.index;
  if (lastFenceIndex == null) {
    return null;
  }

  const trailingFence = content.slice(lastFenceIndex);
  const headerMatch = /^```([^\n`]*)\n?/.exec(trailingFence);
  if (!headerMatch) {
    return null;
  }

  const codeStart = lastFenceIndex + headerMatch[0].length;
  return {
    contentBeforeFence: content.slice(0, lastFenceIndex),
    code: content.slice(codeStart),
    language: headerMatch[1]?.trim() ?? "",
  };
}

type MarkdownProps = {
  content: string;
  className?: string;
  onClickCitation?: (id: string) => void;
  allowCodePreview?: boolean;
  isAnimating?: boolean;
  displaySetting?: DisplaySetting | null;
};

function getNodeText(node: React.ReactNode): string {
  if (node == null || typeof node === "boolean") return "";
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(getNodeText).join("");
  if (React.isValidElement<{ children?: React.ReactNode }>(node)) {
    return getNodeText(node.props.children);
  }
  return "";
}

export default function Markdown({
  content,
  className,
  onClickCitation,
  allowCodePreview = true,
  isAnimating = false,
  displaySetting,
}: MarkdownProps) {
  const { t } = useTranslation("markdown");
  const workbench = useOptionalWorkbench();
  const processedContent = React.useMemo(() => preProcess(content), [content]);
  const trailingIncompleteFence = React.useMemo(
    () => (isAnimating ? extractTrailingIncompleteFence(processedContent) : null),
    [isAnimating, processedContent],
  );
  const streamdownContent = trailingIncompleteFence?.contentBeforeFence ?? processedContent;
  const handlePreviewCode = React.useCallback(
    (language: string, code: string) => {
      if (!allowCodePreview || !workbench) return;

      const previewLanguage = getCodePreviewLanguage(language);
      if (!previewLanguage) return;

      workbench.openPanel({
        type: "code-preview",
        title: t("markdown.code_preview_title", {
          language: previewLanguage.toUpperCase(),
        }),
        preferredDesktopSize: "40%",
        payload: {
          language: previewLanguage,
          code,
        },
      });
    },
    [allowCodePreview, t, workbench],
  );

  const getRuleColor = React.useCallback(
    (pattern: string) => {
      const rule = displaySetting?.rpStyleRules?.find(
        (r) => r.pattern === pattern && r.enabled
      );
      return rule ? rule.colorHex : undefined;
    },
    [displaySetting?.rpStyleRules]
  );

  function MarkdownCode(componentProps: Record<string, unknown> & { children?: React.ReactNode }) {
    const { className, children, ...props } = componentProps;
    const isIncomplete = useIsCodeFenceIncomplete();
    const codeClassName = typeof className === "string" ? className : "";
    const match = /language-([A-Za-z0-9_-]+)/.exec(codeClassName);
    const code = String(children).replace(/\n$/, "");
    const isBlock = code.includes("\n");

    if (match || isBlock || isIncomplete) {
      const language = match?.[1] || "";
      return (
        <CodeBlock
          language={language}
          code={code}
          autoCollapse={displaySetting?.codeBlockAutoCollapse ?? true}
          isIncomplete={isIncomplete}
          showLineNumbers={displaySetting?.showLineNumbers ?? false}
          wrapLines={displaySetting?.codeBlockAutoWrap ?? false}
          onPreview={
            allowCodePreview && workbench
              ? () => {
                  handlePreviewCode(language, code);
                }
              : undefined
          }
        />
      );
    }

    return (
      <code className="inline-code" {...props}>
        {children}
      </code>
    );
  }

  return (
    <div className={cn("markdown", className)}>
      {streamdownContent.length > 0 ? (
        <Streamdown
          mode={isAnimating ? "streaming" : "static"}
          parseIncompleteMarkdown={isAnimating}
          remarkPlugins={[remarkGfm, remarkMath, [remarkRp, displaySetting?.rpStyleRules]]}
          rehypePlugins={[rehypeKatex, rehypeRaw]}
          plugins={{ cjk: cjk }}
          isAnimating={isAnimating}
          controls={{ code: false, mermaid: false }}
          components={{
            pre: ({ children }) => <>{children}</>,
            h1: ({ children, style, ...props }: any) => {
              const color = getRuleColor("#");
              return <h1 style={color ? { ...style, color } : style} {...props}>{children}</h1>;
            },
            h2: ({ children, style, ...props }: any) => {
              const color = getRuleColor("##");
              return <h2 style={color ? { ...style, color } : style} {...props}>{children}</h2>;
            },
            h3: ({ children, style, ...props }: any) => {
              const color = getRuleColor("###");
              return <h3 style={color ? { ...style, color } : style} {...props}>{children}</h3>;
            },
            h4: ({ children, style, ...props }: any) => {
              const color = getRuleColor("####");
              return <h4 style={color ? { ...style, color } : style} {...props}>{children}</h4>;
            },
            h5: ({ children, style, ...props }: any) => {
              const color = getRuleColor("#####");
              return <h5 style={color ? { ...style, color } : style} {...props}>{children}</h5>;
            },
            h6: ({ children, style, ...props }: any) => {
              const color = getRuleColor("######");
              return <h6 style={color ? { ...style, color } : style} {...props}>{children}</h6>;
            },
            blockquote: ({ children, style, ...props }: any) => {
              const color = getRuleColor(">");
              return <blockquote style={color ? { ...style, color, fontStyle: "italic" } : { ...style, fontStyle: "italic" }} {...props}>{children}</blockquote>;
            },
            em: ({ children, style, ...props }: any) => {
              const color = getRuleColor("*");
              return <em style={color ? { ...style, color } : style} {...props}>{children}</em>;
            },
            strong: ({ children, style, ...props }: any) => {
              const color = getRuleColor("**");
              return <strong style={color ? { ...style, color } : style} {...props}>{children}</strong>;
            },
            del: ({ children, style, ...props }: any) => {
              const color = getRuleColor("~~");
              return <del style={color ? { ...style, color } : style} {...props}>{children}</del>;
            },
            code: MarkdownCode as never,
            a: ({ href, children, ...props }) => {
              const childText = getNodeText(children).trim();
              const resolvedHref = href ? resolveFileUrl(href) : href;

              // Citation format: [citation,domain](id)
              if (childText.startsWith("citation,")) {
                const domain = childText.substring("citation,".length);
                const id = (href || "").trim();

                if (id.length === 6) {
                  return (
                    <span
                      className="citation-badge"
                      onClick={() => onClickCitation?.(id)}
                      title={domain}
                    >
                      {domain}
                    </span>
                  );
                }

                if (resolvedHref) {
                  return (
                    <a
                      className="citation-badge"
                      href={resolvedHref}
                      target="_blank"
                      rel="noopener noreferrer"
                      title={domain}
                      {...props}
                    >
                      {domain}
                    </a>
                  );
                }
              }

              return (
                <a href={resolvedHref} target="_blank" rel="noopener noreferrer" {...props}>
                  {children}
                </a>
              );
            },
            img: ({ src, alt, ...props }) => (
              <img
                src={typeof src === "string" ? resolveFileUrl(src) : src}
                alt={alt ?? ""}
                {...props}
              />
            ),
          }}
        >
          {streamdownContent}
        </Streamdown>
      ) : null}
      {trailingIncompleteFence ? (
        <CodeBlock
          language={trailingIncompleteFence.language}
          code={trailingIncompleteFence.code}
          autoCollapse={displaySetting?.codeBlockAutoCollapse ?? true}
          isIncomplete
          showLineNumbers={displaySetting?.showLineNumbers ?? false}
          wrapLines={displaySetting?.codeBlockAutoWrap ?? false}
          onPreview={
            allowCodePreview && workbench
              ? () => {
                  handlePreviewCode(trailingIncompleteFence.language, trailingIncompleteFence.code);
                }
              : undefined
          }
        />
      ) : null}
    </div>
  );
}
