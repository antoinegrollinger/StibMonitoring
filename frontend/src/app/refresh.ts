import { Injectable, computed, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subject, interval, merge, of, switchMap } from 'rxjs';
import { StibService } from './stib.service';

/** Used until the backend's configured default arrives. */
const FALLBACK_INTERVAL_SECONDS = 15;
const PRESET_INTERVALS_SECONDS = [0, 5, 10, 15, 30, 60];
const STORAGE_KEY = 'stib-monitoring.refreshSeconds';

/**
 * Auto-refresh interval shared by everything the live view polls. The default comes from the
 * backend (`frontend.refresh-interval`); a choice made in the UI overrides it and is
 * remembered per browser. 0 means auto-refresh is off.
 */
@Injectable({ providedIn: 'root' })
export class RefreshSettings {
  private readonly userChoice = signal<number | null>(storedChoice());
  private readonly serverDefault = signal<number | null>(null);
  private readonly manual = new Subject<void>();

  readonly intervalSeconds = computed(() =>
    this.userChoice() ?? this.serverDefault() ?? FALLBACK_INTERVAL_SECONDS);

  /** Presets, plus the configured default if it is not one of them. */
  readonly options = computed(() => {
    const extra = [this.serverDefault(), this.intervalSeconds()].filter((s): s is number => s !== null);
    return [...new Set([...PRESET_INTERVALS_SECONDS, ...extra])].sort((a, b) => a - b);
  });

  private readonly interval$ = toObservable(this.intervalSeconds);

  constructor() {
    inject(StibService).getConfig().subscribe({
      next: config => this.serverDefault.set(Math.max(0, config.refreshIntervalSeconds)),
      error: () => { /* keep the fallback */ },
    });
  }

  set(seconds: number): void {
    this.userChoice.set(seconds);
    try {
      localStorage.setItem(STORAGE_KEY, String(seconds));
    } catch {
      // Storage unavailable: the choice lasts for this session only.
    }
  }

  refreshNow(): void {
    this.manual.next();
  }

  /**
   * Emits once immediately, then on every interval tick and every manual refresh. Changing the
   * interval restarts the timer without an extra immediate load.
   */
  ticks(): Observable<unknown> {
    return merge(
      of(null),
      this.interval$.pipe(switchMap(seconds => seconds > 0 ? interval(seconds * 1000) : EMPTY)),
      this.manual,
    );
  }
}

function storedChoice(): number | null {
  try {
    const value = Number(localStorage.getItem(STORAGE_KEY) ?? NaN);
    return Number.isInteger(value) && value >= 0 ? value : null;
  } catch {
    return null;
  }
}
