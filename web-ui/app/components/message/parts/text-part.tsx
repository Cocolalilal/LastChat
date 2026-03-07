import Markdown from "~/components/markdown/markdown";

interface TextPartProps {
  text: string;
  isAnimating?: boolean;
  onClickCitation?: (id: string) => void;
}

export function TextPart({ text, isAnimating, onClickCitation }: TextPartProps) {
  if (!text) return null;
  return (
    <div data-part="text">
      <Markdown content={text} isAnimating={isAnimating} onClickCitation={onClickCitation} />
    </div>
  );
}
