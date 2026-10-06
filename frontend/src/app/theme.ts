import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';

export const THEMES = ['system', 'light', 'dark'] as const;
export type Theme = (typeof THEMES)[number];

/** Keep in sync with the inline script in index.html, which applies the theme before the app boots. */
const STORAGE_KEY = 'stib-monitoring.theme';

/**
 * Light / dark appearance. "system" follows the OS setting; an explicit choice is remembered
 * per browser. The resolved theme is set as `data-theme` on <html>, which styles.scss keys on.
 */
@Injectable({ providedIn: 'root' })
export class ThemeSettings {
  readonly theme = signal<Theme>(storedTheme());

  private readonly systemDark = signal(false);

  readonly dark = computed(() => this.theme() === 'dark' || (this.theme() === 'system' && this.systemDark()));

  constructor() {
    const query = window.matchMedia?.('(prefers-color-scheme: dark)');
    if (query) {
      this.systemDark.set(query.matches);
      const listener = (e: MediaQueryListEvent) => this.systemDark.set(e.matches);
      query.addEventListener('change', listener);
      inject(DestroyRef).onDestroy(() => query.removeEventListener('change', listener));
    }

    effect(() => {
      document.documentElement.dataset['theme'] = this.dark() ? 'dark' : 'light';
    });
    effect(() => {
      const theme = this.theme();
      try {
        localStorage.setItem(STORAGE_KEY, theme);
      } catch {
        // Storage unavailable (private mode…): the choice just isn't remembered.
      }
    });
  }
}

function storedTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (THEMES.includes(stored as Theme)) {
      return stored as Theme;
    }
  } catch {
    // ignore
  }
  return 'system';
}
