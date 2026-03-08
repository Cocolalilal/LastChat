import { appendWebAuthQuery } from "~/services/api";

/**
 * Convert file URL to the correct API endpoint
 * - data: URLs are returned as-is (base64 encoded files)
 * - http/https URLs are returned as-is (external files)
 * - file:// URLs are extracted to relative paths and converted to /api/files/path/{path}
 * - Relative paths are converted to /api/files/path/{path}
 */
export function resolveFileUrl(url: string): string {
  if (url.startsWith("data:")) {
    return url;
  }
  if (url.startsWith("http://") || url.startsWith("https://")) {
    return url;
  }
  if (url.startsWith("/api/")) {
    return appendWebAuthQuery(url);
  }

  if (
    url.startsWith("file://") ||
    url.startsWith("content://") ||
    url.startsWith("android.resource://")
  ) {
    return appendWebAuthQuery(`/api/files/content?uri=${encodeURIComponent(url)}`);
  }

  // Relative path - convert to API endpoint
  // Remove leading slash if present
  const path = url.startsWith("/") ? url.slice(1) : url;
  return appendWebAuthQuery(`/api/files/path/${path}`);
}
