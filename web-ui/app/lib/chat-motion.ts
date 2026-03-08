import { useReducedMotion } from "motion/react";
import type { Transition, Variants } from "motion/react";

export const CHAT_MOTION_DURATION = {
  fast: 0.16,
  standard: 0.22,
  slow: 0.32,
  stagger: 0.045,
} as const;

export const CHAT_SPRING_TACTILE: Transition = {
  type: "spring",
  stiffness: 340,
  damping: 28,
  mass: 0.85,
};

export const CHAT_SPRING_LAYOUT: Transition = {
  type: "spring",
  stiffness: 260,
  damping: 30,
  mass: 0.95,
};

export const CHAT_SPRING_GENTLE: Transition = {
  type: "spring",
  stiffness: 220,
  damping: 26,
  mass: 1,
};

export function useChatReducedMotion() {
  return useReducedMotion() ?? false;
}

export function getChatFadeTransition(reducedMotion: boolean): Transition {
  return reducedMotion ? { duration: 0.01 } : { duration: CHAT_MOTION_DURATION.standard, ease: "easeOut" };
}

export function getChatLayoutTransition(reducedMotion: boolean): Transition {
  return reducedMotion ? { duration: 0.01 } : CHAT_SPRING_LAYOUT;
}

export function getChatTactileTransition(reducedMotion: boolean): Transition {
  return reducedMotion ? { duration: 0.01 } : CHAT_SPRING_TACTILE;
}

export function getChatLiftVariants(reducedMotion: boolean, distance = 10): Variants {
  if (reducedMotion) {
    return {
      initial: { opacity: 0 },
      animate: { opacity: 1, transition: { duration: 0.01 } },
      exit: { opacity: 0, transition: { duration: 0.01 } },
    };
  }

  return {
    initial: { opacity: 0, y: distance, scale: 0.985, filter: "blur(6px)" },
    animate: {
      opacity: 1,
      y: 0,
      scale: 1,
      filter: "blur(0px)",
      transition: {
        opacity: { duration: CHAT_MOTION_DURATION.standard, ease: "easeOut" },
        y: CHAT_SPRING_LAYOUT,
        scale: CHAT_SPRING_GENTLE,
        filter: { duration: CHAT_MOTION_DURATION.fast, ease: "easeOut" },
      },
    },
    exit: {
      opacity: 0,
      y: Math.max(4, Math.round(distance * 0.6)),
      scale: 0.99,
      transition: { duration: CHAT_MOTION_DURATION.fast, ease: "easeInOut" },
    },
  };
}
