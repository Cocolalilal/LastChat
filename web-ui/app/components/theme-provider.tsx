import { createContext, useContext, useEffect, useMemo, useState } from "react";

export type ThemeMode = "dark" | "light" | "system";
export type Theme = ThemeMode;
export type ColorTheme = "default" | "claude" | "t3-chat" | "mono" | "caffeinated" | "custom";

export const COLOR_THEMES: ColorTheme[] = [
  "default",
  "claude",
  "t3-chat",
  "mono",
  "caffeinated",
  "custom",
];

const COLOR_THEME_STORAGE_SUFFIX = "-color";
const CUSTOM_THEME_LIGHT_STORAGE_SUFFIX = "-custom-light";
const CUSTOM_THEME_DARK_STORAGE_SUFFIX = "-custom-dark";
const CUSTOM_THEME_STYLE_ID = "rikkahub-custom-theme";

type ThemeProviderProps = {
  children: React.ReactNode;
  defaultTheme?: ThemeMode;
  defaultColorTheme?: ColorTheme;
  storageKey?: string;
};

export type CustomThemeCss = {
  light: string;
  dark: string;
};

type ThemeProviderState = {
  theme: ThemeMode;
  setTheme: (theme: ThemeMode) => void;
  colorTheme: ColorTheme;
  setColorTheme: (theme: ColorTheme) => void;
  resolvedMode: "light" | "dark";
  customThemeCss: CustomThemeCss;
  setCustomThemeCss: (theme: CustomThemeCss) => void;
};

const initialState: ThemeProviderState = {
  theme: "dark",
  colorTheme: "default",
  resolvedMode: "dark",
  customThemeCss: {
    light: "",
    dark: "",
  },
  setTheme: () => null,
  setColorTheme: () => null,
  setCustomThemeCss: () => null,
};

const ThemeProviderContext = createContext<ThemeProviderState>(initialState);

function isThemeMode(value: string | null): value is ThemeMode {
  return value === "light" || value === "dark" || value === "system";
}

function normalizeColorTheme(value: string | null): ColorTheme | null {
  if (value === "bubblegum") {
    return "caffeinated";
  }
  return value && COLOR_THEMES.includes(value as ColorTheme) ? (value as ColorTheme) : null;
}

function removeBlacklistedCss(value: string): string {
  return value
    .replace(/@theme\s+inline\s*\{[\s\S]*?\}/g, "")
    .replace(/(^|\n)\s*body\s*\{[\s\S]*?\}/g, "")
    .trim();
}

function scopeCustomThemeCss(value: string, mode: "light" | "dark"): string {
  const trimmed = value.trim();
  if (!trimmed) {
    return "";
  }

  const filtered = removeBlacklistedCss(trimmed);
  if (!filtered) {
    return "";
  }

  if (mode === "light") {
    const scoped = filtered.replace(
      /(^|\n)\s*:root(?!\.dark)(?!\[data-theme="custom"\])\s*\{/g,
      '$1:root[data-theme="custom"] {',
    );

    if (/:root\[data-theme="custom"\]\s*\{/.test(scoped)) {
      return scoped;
    }

    return `:root[data-theme="custom"] {\n${filtered}\n}`;
  }

  const scopedDarkRoot = filtered.replace(
    /(^|\n)\s*:root\.dark(?!\[data-theme="custom"\])\s*\{/g,
    '$1:root.dark[data-theme="custom"] {',
  );
  const scoped = scopedDarkRoot.replace(
    /(^|\n)\s*\.dark(?![a-zA-Z0-9_-])\s*\{/g,
    '$1:root.dark[data-theme="custom"] {',
  );

  if (/:root\.dark\[data-theme="custom"\]\s*\{/.test(scoped)) {
    return scoped;
  }

  return `:root.dark[data-theme="custom"] {\n${filtered}\n}`;
}

export function ThemeProvider({
  children,
  defaultTheme = "dark",
  defaultColorTheme = "default",
  storageKey = "vite-ui-theme",
  ...props
}: ThemeProviderProps) {
  const colorThemeStorageKey = `${storageKey}${COLOR_THEME_STORAGE_SUFFIX}`;
  const customThemeLightStorageKey = `${storageKey}${CUSTOM_THEME_LIGHT_STORAGE_SUFFIX}`;
  const customThemeDarkStorageKey = `${storageKey}${CUSTOM_THEME_DARK_STORAGE_SUFFIX}`;

  const [theme, setTheme] = useState<ThemeMode>(() => {
    const stored = localStorage.getItem(storageKey);
    return isThemeMode(stored) ? stored : defaultTheme;
  });

  const [colorTheme, setColorTheme] = useState<ColorTheme>(() => {
    const stored = normalizeColorTheme(localStorage.getItem(colorThemeStorageKey));
    return stored ?? defaultColorTheme;
  });

  const [customThemeCss, setCustomThemeCss] = useState<CustomThemeCss>(() => ({
    light: localStorage.getItem(customThemeLightStorageKey) ?? "",
    dark: localStorage.getItem(customThemeDarkStorageKey) ?? "",
  }));

  const [prefersDark, setPrefersDark] = useState(() =>
    typeof window !== "undefined"
      ? window.matchMedia("(prefers-color-scheme: dark)").matches
      : false,
  );

  useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const onSystemThemeChange = () => {
      setPrefersDark(mediaQuery.matches);
    };

    onSystemThemeChange();
    mediaQuery.addEventListener("change", onSystemThemeChange);
    return () => {
      mediaQuery.removeEventListener("change", onSystemThemeChange);
    };
  }, []);

  const resolvedMode = useMemo<"light" | "dark">(() => {
    if (theme === "system") {
      return prefersDark ? "dark" : "light";
    }
    return theme;
  }, [prefersDark, theme]);

  useEffect(() => {
    const root = window.document.documentElement;
    root.classList.remove("light", "dark");
    root.classList.add(resolvedMode);
  }, [resolvedMode]);

  useEffect(() => {
    const root = window.document.documentElement;
    root.dataset.theme = colorTheme;
  }, [colorTheme]);

  useEffect(() => {
    const lightCss = scopeCustomThemeCss(customThemeCss.light, "light");
    const darkCss = scopeCustomThemeCss(customThemeCss.dark, "dark");
    const cssBlocks = [lightCss, darkCss].filter(Boolean).join("\n\n");

    const existingStyle = document.getElementById(CUSTOM_THEME_STYLE_ID);

    if (!cssBlocks) {
      existingStyle?.remove();
      return;
    }

    const styleElement = existingStyle ?? document.createElement("style");
    styleElement.id = CUSTOM_THEME_STYLE_ID;
    styleElement.textContent = cssBlocks;

    if (!existingStyle) {
      document.head.appendChild(styleElement);
    }
  }, [customThemeCss.dark, customThemeCss.light]);

  const value = {
    theme,
    colorTheme,
    resolvedMode,
    customThemeCss,
    setTheme: (theme: ThemeMode) => {
      localStorage.setItem(storageKey, theme);
      setTheme(theme);
    },
    setColorTheme: (theme: ColorTheme) => {
      localStorage.setItem(colorThemeStorageKey, theme);
      setColorTheme(theme);
    },
    setCustomThemeCss: (themeCss: CustomThemeCss) => {
      localStorage.setItem(customThemeLightStorageKey, themeCss.light);
      localStorage.setItem(customThemeDarkStorageKey, themeCss.dark);
      setCustomThemeCss(themeCss);
    },
  };

  return (
    <ThemeProviderContext.Provider {...props} value={value}>
      {children}
    </ThemeProviderContext.Provider>
  );
}

export const useTheme = () => {
  const context = useContext(ThemeProviderContext);

  if (context === undefined) throw new Error("useTheme must be used within a ThemeProvider");

  return context;
};
