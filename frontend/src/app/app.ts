import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, Injector, afterNextRender, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, distinctUntilChanged, interval, of, switchMap, tap } from 'rxjs';
import { lineColor, routeColor } from './colors';
import { ControlReport } from './control-report';
import { LineMap } from './line-map';
import { LineSelection } from './line-selection';
import { I18n, LANGUAGES, Lang, Translatable } from './i18n';
import { RefreshSettings } from './refresh';
import { ThemeSettings } from './theme';
import { LineMessage, LiveLineStops, LiveStop, StopRef, TicketControl, WaitingTime, directionKey } from './stib.models';
import { StibService } from './stib.service';
import { WaitingTimes } from './waiting-times';

function isRateLimited(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 429;
}

/** Directions of one line, as shown in a sidebar group. */
interface LineGroup {
  lineId: string;
  directions: LiveLineStops[];
  vehicleCount: number;
  messageCount: number;
}

@Component({
  selector: 'app-root',
  imports: [ControlReport, LineMap, WaitingTimes],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly stib = inject(StibService);
  protected readonly i18n = inject(I18n);
  protected readonly languages = LANGUAGES;
  protected readonly refresh = inject(RefreshSettings);
  protected readonly lines = inject(LineSelection);
  protected readonly theme = inject(ThemeSettings);
  protected readonly themeOptions = [
    { theme: 'system', icon: '◐', label: 'theme.system' },
    { theme: 'light', icon: '☀', label: 'theme.light' },
    { theme: 'dark', icon: '☾', label: 'theme.dark' },
  ] as const;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly directions = signal<LiveLineStops[]>([]);
  /** Lines the current `directions` were loaded for; re-frames the map when it changes. */
  protected readonly loadedKey = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<Translatable | null>(null);
  protected readonly lineInputError = signal<Translatable | null>(null);
  protected readonly lastUpdated = signal<Date | null>(null);

  protected readonly selectedStop = signal<StopRef | null>(null);
  protected readonly waitingTimes = signal<WaitingTime[] | null>(null);
  protected readonly waitingTimesError = signal<Translatable | null>(null);
  protected readonly now = signal(Date.now());
  protected readonly messages = signal<LineMessage[]>([]);
  protected readonly messagesExpanded = signal(false);
  /** Active ticket controls reported by travellers, most recent first. */
  protected readonly controls = signal<TicketControl[]>([]);
  /** Active controls per stop id. */
  protected readonly controlsByStop = computed(() => {
    const byStop = new Map<string, TicketControl[]>();
    for (const control of this.controls()) {
      byStop.set(control.stopId, [...(byStop.get(control.stopId) ?? []), control]);
    }
    return byStop;
  });
  /** Line groups the user expanded in the sidebar (only used when several lines are shown). */
  protected readonly expandedLines = signal<ReadonlySet<string>>(new Set());
  /** Per line, the only direction shown on the map; lines not in here show all their directions. */
  protected readonly soloDirections = signal<ReadonlyMap<string, string>>(new Map());
  /** Directions (`line/direction`) hidden from the map because another direction of their line is shown alone. */
  protected readonly hiddenDirections = computed(() => {
    const solo = this.soloDirections();
    return new Set(this.directions().map(directionKey)
      .filter(key => { const only = solo.get(key.split('/')[0]); return only !== undefined && only !== key; }));
  });
  /** Directions (`line/direction`) the user expanded in the sidebar; directions start collapsed. */
  protected readonly expandedDirections = signal<ReadonlySet<string>>(new Set());

  protected readonly singleLine = computed(() => this.lines.lineIds().length === 1);

  protected readonly vehicleCount = computed(() =>
    this.directions().reduce((n, d) => n + d.stops.filter(s => s.vehiclePresent).length, 0));

  /** Stop keys (`line:stop`) flagged by at least one message about that line. */
  protected readonly stopsWithMessages = computed(() => new Set(
    this.messages().flatMap(m => m.lineIds.flatMap(lineId => m.affectedStopIds.map(stopId => `${lineId}:${stopId}`)))));

  protected readonly groups = computed<LineGroup[]>(() => {
    const byLine = new Map<string, LiveLineStops[]>();
    for (const direction of this.directions()) {
      byLine.set(direction.lineId, [...(byLine.get(direction.lineId) ?? []), direction]);
    }
    return [...byLine].map(([lineId, directions]) => ({
      lineId,
      directions,
      vehicleCount: directions.reduce((n, d) => n + d.stops.filter(s => s.vehiclePresent).length, 0),
      messageCount: this.messages().filter(m => m.lineIds.includes(lineId)).length,
    }));
  });

  /**
   * The selected stop, with the direction it belongs to when it is on a shown line. A stop with a
   * reported control can also be selected without its line being shown; it then has no direction.
   */
  protected readonly selection = computed<{ stop: LiveStop; direction: LiveLineStops | null } | null>(() => {
    const ref = this.selectedStop();
    if (!ref) {
      return null;
    }
    for (const direction of this.directions()) {
      const stop = direction.lineId === ref.lineId ? direction.stops.find(s => s.id === ref.stopId) : undefined;
      if (stop) {
        return { stop, direction };
      }
    }
    const reported = this.controlsByStop().get(ref.stopId)?.[0]?.stop;
    return reported ? {
      stop: { ...reported, id: ref.stopId, order: 0, vehiclePresent: false },
      direction: null,
    } : null;
  });

  /** Messages about the selected stop on its line, plus that line's line-wide ones. */
  protected readonly selectedStopMessages = computed(() => {
    const ref = this.selectedStop();
    return ref === null ? [] : this.messages().filter(m => m.lineIds.includes(ref.lineId)
      && (m.affectedStopIds.length === 0 || m.affectedStopIds.includes(ref.stopId)));
  });

  constructor() {
    const lineIds$ = toObservable(this.lines.lineIds).pipe(
      distinctUntilChanged((a, b) => a.join(',') === b.join(',')));

    // Reload immediately when the lines change, then on every refresh tick for fresh vehicle positions.
    lineIds$
      .pipe(
        tap(() => {
          this.directions.set([]);
          this.error.set(null);
        }),
        switchMap(lineIds => lineIds.length === 0 ? EMPTY : this.refresh.ticks().pipe(
          tap(() => this.loading.set(true)),
          switchMap(() => this.stib.getLiveStops(lineIds).pipe(
            tap(directions => {
              this.directions.set(directions);
              this.loadedKey.set(lineIds.join(','));
              this.lastUpdated.set(new Date());
              this.error.set(directions.length ? null : { key: 'error.noData', params: { line: lineIds.join(', ') } });
            }),
            catchError(err => {
              this.error.set({ key: isRateLimited(err) ? 'error.rateLimit' : 'error.backend' });
              return of(null);
            }),
            tap(() => this.loading.set(false)),
          )),
        )),
        takeUntilDestroyed(),
      )
      .subscribe();

    // Load waiting times for the selected stop and keep them fresh while it stays selected.
    toObservable(this.selectedStop)
      .pipe(
        distinctUntilChanged((a, b) => a?.stopId === b?.stopId),
        tap(() => {
          this.waitingTimes.set(null);
          this.waitingTimesError.set(null);
        }),
        switchMap(ref => ref === null ? of(null) : this.refresh.ticks().pipe(
          switchMap(() => this.stib.getWaitingTimes(ref.stopId).pipe(
            tap(times => {
              this.waitingTimes.set(times);
              this.waitingTimesError.set(null);
            }),
            catchError(err => {
              this.waitingTimesError.set({ key: isRateLimited(err) ? 'error.rateLimit' : 'error.waitingTimes' });
              return of(null);
            }),
          )),
        )),
        takeUntilDestroyed(),
      )
      .subscribe();

    // Service messages for the lines (cached by the backend); on failure keep showing the previous ones.
    lineIds$
      .pipe(
        tap(() => this.messages.set([])),
        switchMap(lineIds => lineIds.length === 0 ? EMPTY : this.refresh.ticks().pipe(
          switchMap(() => this.stib.getLineMessages(lineIds).pipe(
            tap(messages => this.messages.set(messages)),
            catchError(() => of(null)),
          )),
        )),
        takeUntilDestroyed(),
      )
      .subscribe();

    // Ticket controls reported by travellers; on failure keep showing the previous ones.
    this.refresh.ticks()
      .pipe(
        switchMap(() => this.stib.getControls().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe(controls => {
        if (controls) {
          this.controls.set(controls);
        }
      });

    // Forget a selected stop whose line is no longer shown.
    lineIds$.pipe(takeUntilDestroyed()).subscribe(lineIds => {
      const ref = this.selectedStop();
      if (ref && !lineIds.includes(ref.lineId)) {
        this.selectedStop.set(null);
      }
    });

    // Keep "x min" countdowns current between refreshes.
    interval(10_000).pipe(takeUntilDestroyed()).subscribe(() => this.now.set(Date.now()));
  }

  protected addControl(control: TicketControl): void {
    this.controls.update(controls => [control, ...controls.filter(c => c.id !== control.id)]);
  }

  protected colorOf(lineId: string, directionIndex = 0): string {
    return routeColor(lineId, directionIndex, this.lines.lineIds());
  }

  protected lineColor(lineId: string): string {
    return lineColor(lineId, this.lines.lineIds());
  }

  protected toggleAllLines(): void {
    this.lineInputError.set(null);
    this.lines.toggleAll();
  }

  protected clearLines(): void {
    this.lineInputError.set(null);
    this.lines.clear();
  }

  /** Adds a line as soon as it is picked from the suggestions, without pressing "Add". */
  protected onLineInput(event: Event, input: HTMLInputElement): void {
    // Picking a datalist option fires a plain Event (Chrome) or an "insertReplacementText" InputEvent
    // (Firefox, Safari); typing fires ordinary InputEvents, so "1" isn't added while typing "12".
    const picked = !(event instanceof InputEvent) || event.inputType === 'insertReplacementText';
    if (picked && this.lines.allLineIds().includes(input.value.trim().toUpperCase())) {
      this.addLines(input);
    }
  }

  protected addLines(input: HTMLInputElement): void {
    const unknown = this.lines.add(input.value);
    this.lineInputError.set(unknown.length ? { key: 'lines.unknown', params: { lines: unknown.join(', ') } } : null);
    input.value = unknown.join(', ');
  }

  protected isExpanded(lineId: string): boolean {
    return this.singleLine() || this.expandedLines().has(lineId);
  }

  protected toggleLine(lineId: string): void {
    this.expandedLines.update(expanded => {
      const next = new Set(expanded);
      if (!next.delete(lineId)) {
        next.add(lineId);
      }
      return next;
    });
  }

  protected setAllExpanded(expanded: boolean): void {
    this.expandedLines.set(new Set(expanded ? this.groups().map(g => g.lineId) : []));
    this.expandedDirections.set(new Set(expanded ? this.directions().map(directionKey) : []));
  }

  protected isDirectionExpanded(direction: LiveLineStops): boolean {
    return this.expandedDirections().has(directionKey(direction));
  }

  protected isDirectionShown(direction: LiveLineStops): boolean {
    return !this.hiddenDirections().has(directionKey(direction));
  }

  /** Shows only this direction of its line on the map, or all of them again if it already was. */
  protected toggleSoloDirection(direction: LiveLineStops): void {
    const key = directionKey(direction);
    this.soloDirections.update(solo => {
      const next = new Map(solo);
      if (next.get(direction.lineId) === key) {
        next.delete(direction.lineId);
      } else {
        next.set(direction.lineId, key);
      }
      return next;
    });
  }

  protected toggleDirection(direction: LiveLineStops): void {
    const key = directionKey(direction);
    this.expandedDirections.update(expanded => {
      const next = new Set(expanded);
      if (!next.delete(key)) {
        next.add(key);
      }
      return next;
    });
  }

  protected setLanguage(lang: Lang): void {
    this.i18n.lang.set(lang);
  }

  protected isSelected(lineId: string, stopId: string): boolean {
    const ref = this.selectedStop();
    return ref?.lineId === lineId && ref.stopId === stopId;
  }

  protected selectStop(ref: StopRef | null): void {
    this.now.set(Date.now());
    this.selectedStop.set(ref);
    if (ref && this.lines.lineIds().includes(ref.lineId) && !this.isExpanded(ref.lineId)) {
      this.toggleLine(ref.lineId);
    }
    const direction = this.selection()?.direction;
    if (direction && !this.isDirectionExpanded(direction)) {
      this.toggleDirection(direction);
    }
    // Stops picked on the map may be out of view in the sidebar.
    afterNextRender(
      () => this.host.nativeElement.querySelector('.stops button.selected')?.scrollIntoView({ block: 'nearest' }),
      { injector: this.injector },
    );
  }
}
