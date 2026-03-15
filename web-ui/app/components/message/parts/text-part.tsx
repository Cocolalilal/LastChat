import Markdown from "~/components/markdown/markdown";
import type { DisplaySetting } from "~/types";

interface TextPartProps {
  text: string;
  displaySetting?: DisplaySetting | null;
  isAnimating?: boolean;
  onClickCitation?: (id: string) => void;
}

export function TextPart({ text, displaySetting, isAnimating, onClickCitation }: TextPartProps) {
  if (!text) return null;
  return (
    <div data-part="text">
      <Markdown
        content={text}
        displaySetting={displaySetting}
        isAnimating={isAnimating}
        onClickCitation={onClickCitation}
      />
    </div>
  );
}
