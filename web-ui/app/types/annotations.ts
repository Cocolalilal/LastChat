export interface UrlCitationAnnotation {
  type: "url_citation";
  title: string;
  url: string;
}

export interface OcrActivityAnnotation {
  type: "ocr_activity";
  source: "image" | "pdf";
  fileName?: string | null;
  pageNumbers: number[];
}

/**
 * Union type for message annotations
 * @see ai/src/main/java/me/rerere/ai/ui/Message.kt - UIMessageAnnotation
 */
export type UIMessageAnnotation = UrlCitationAnnotation | OcrActivityAnnotation;
