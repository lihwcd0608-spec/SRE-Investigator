export type ThemeMode = "dark" | "light";

export const THEME_STORAGE_KEY = "sre-investigator.theme";

export function parseTheme(value: string | null | undefined): ThemeMode {
  return value === "light" ? "light" : "dark";
}

export function readStoredTheme(): ThemeMode {
  try {
    return parseTheme(localStorage.getItem(THEME_STORAGE_KEY));
  } catch {
    return "dark";
  }
}

export function applyTheme(mode: ThemeMode): void {
  document.documentElement.setAttribute("data-theme", mode);
  document.documentElement.style.colorScheme = mode;
}

export function persistTheme(mode: ThemeMode): void {
  applyTheme(mode);
  try {
    localStorage.setItem(THEME_STORAGE_KEY, mode);
  } catch {
    // ignore quota / private mode
  }
}

export function toggleTheme(current: ThemeMode): ThemeMode {
  const next: ThemeMode = current === "dark" ? "light" : "dark";
  persistTheme(next);
  return next;
}
