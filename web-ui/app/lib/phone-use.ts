export function phoneUseLabel(action?: string): string {
  const labels: Record<string, string> = {
    observe: "Reading screen",
    screenshot: "Looking at screen",
    tap: "Tapping",
    long_press: "Holding",
    swipe: "Scrolling",
    scroll: "Scrolling",
    type: "Typing",
    press: "Navigating",
    open_app: "Opening app",
    list_apps: "Finding apps",
    wait: "Waiting",
  };
  return labels[action ?? ""] ?? "Using phone";
}
