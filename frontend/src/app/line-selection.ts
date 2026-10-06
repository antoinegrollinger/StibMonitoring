import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { StibService } from './stib.service';

const STORAGE_KEY = 'stib-monitoring.lines';

interface StoredSelection {
  lines: string[];
  all: boolean;
}

/** Which lines the live view shows: a hand-picked list, or every line of the network. */
@Injectable({ providedIn: 'root' })
export class LineSelection {
  /** Every line of the network, in display order (empty until loaded). */
  readonly allLineIds = signal<string[]>([]);
  readonly picked = signal<string[]>(['1']);
  readonly showAll = signal(false);

  /** Lines actually displayed. */
  readonly lineIds = computed(() => this.showAll() ? this.allLineIds() : this.picked());

  constructor() {
    const stored = load();
    if (stored) {
      this.picked.set(stored.lines);
      this.showAll.set(stored.all);
    }
    effect(() => save({ lines: this.picked(), all: this.showAll() }));

    inject(StibService).getLineIds().subscribe({
      next: ids => {
        this.allLineIds.set(ids);
        // Drop lines that no longer exist (e.g. remembered from an older timetable).
        this.picked.update(picked => picked.filter(id => ids.includes(id)));
      },
      error: () => { /* the picker still works, just without validation */ },
    });
  }

  /**
   * Adds the lines typed by the user ("1, 5 92"). Returns the ones that are not part of the
   * network, which are not added.
   */
  add(input: string): string[] {
    const known = this.allLineIds();
    const requested = input.split(/[\s,;]+/).map(id => id.trim().toUpperCase()).filter(Boolean);
    const unknown = known.length ? requested.filter(id => !known.includes(id)) : [];
    const valid = requested.filter(id => !unknown.includes(id) && /^[A-Z0-9]+$/.test(id));
    if (valid.length) {
      this.showAll.set(false);
      this.picked.update(picked => [...picked, ...valid.filter(id => !picked.includes(id))]);
    }
    return unknown;
  }

  remove(lineId: string): void {
    this.picked.update(picked => picked.filter(id => id !== lineId));
  }

  /** Shows no lines at all. */
  clear(): void {
    this.showAll.set(false);
    this.picked.set([]);
  }

  toggleAll(): void {
    this.showAll.update(all => !all);
  }
}

function load(): StoredSelection | null {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    return value && Array.isArray(value.lines) && typeof value.all === 'boolean' ? value : null;
  } catch {
    return null;
  }
}

function save(selection: StoredSelection): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(selection));
  } catch {
    // Storage unavailable: the selection lasts for this session only.
  }
}
