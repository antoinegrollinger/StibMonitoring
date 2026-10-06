import { Injectable, computed, inject, signal } from '@angular/core';
import { StibService } from './stib.service';

const STORAGE_KEY = 'stib-monitoring.mergeDirections';

/**
 * Whether the sidebar shows each line's directions merged into one list of stops. The default
 * comes from the backend (`frontend.merge-directions`); a choice made in the UI overrides it and
 * is remembered per browser.
 */
@Injectable({ providedIn: 'root' })
export class DirectionMerge {
  private readonly userChoice = signal<boolean | null>(storedChoice());
  private readonly serverDefault = signal<boolean | null>(null);

  readonly merged = computed(() => this.userChoice() ?? this.serverDefault() ?? false);

  constructor() {
    inject(StibService).getConfig().subscribe({
      next: config => this.serverDefault.set(config.mergeDirections),
      error: () => { /* keep the fallback */ },
    });
  }

  toggle(): void {
    const merged = !this.merged();
    this.userChoice.set(merged);
    try {
      localStorage.setItem(STORAGE_KEY, String(merged));
    } catch {
      // Storage unavailable: the choice lasts for this session only.
    }
  }
}

function storedChoice(): boolean | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === 'true' ? true : value === 'false' ? false : null;
  } catch {
    return null;
  }
}
