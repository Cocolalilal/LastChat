import * as React from "react";

import { useTranslation } from "react-i18next";

import { cn } from "~/lib/utils";

type GreetingBucket =
  | "early_morning"
  | "morning"
  | "lunch"
  | "afternoon"
  | "evening"
  | "late_evening"
  | "night"
  | "early_hours";

const GREETING_DEFAULTS: Record<GreetingBucket, string> = {
  early_morning: "Rise and shine!",
  morning: "Good Morning!",
  lunch: "Lunch time!",
  afternoon: "Good Afternoon!",
  evening: "Good Evening!",
  late_evening: "Winding down?",
  night: "Good Night!",
  early_hours: "Still up? Get some rest",
};

function getGreetingBucket(hour: number): GreetingBucket {
  if (hour >= 5 && hour <= 7) return "early_morning";
  if (hour >= 8 && hour <= 11) return "morning";
  if (hour >= 12 && hour <= 13) return "lunch";
  if (hour >= 14 && hour <= 17) return "afternoon";
  if (hour >= 18 && hour <= 20) return "evening";
  if (hour >= 21 && hour <= 22) return "late_evening";
  if (hour === 23 || hour <= 1) return "night";
  return "early_hours";
}

export function ConversationGreeting({ className }: { className?: string }) {
  const { t } = useTranslation("page");
  const [hour, setHour] = React.useState(() => new Date().getHours());

  React.useEffect(() => {
    const updateHour = () => {
      setHour(new Date().getHours());
    };

    updateHour();
    const id = window.setInterval(updateHour, 60_000);
    return () => window.clearInterval(id);
  }, []);

  const bucket = getGreetingBucket(hour);

  return (
    <span className={cn("text-balance", className)}>
      {t(`conversations.greeting.${bucket}`, {
        defaultValue: GREETING_DEFAULTS[bucket],
      })}
    </span>
  );
}
