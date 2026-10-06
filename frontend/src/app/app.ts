import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, Injector, afterNextRender, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, catchError, combineLatest, distinctUntilChanged, forkJoin, interval, map, of, switchMap, tap } from 'rxjs';
import { lineColor, routeColor } from './colors';
import { ControlReport } from './control-report';
import { DirectionMerge } from './direction-merge';
import { LineMap } from './line-map';
import { LineSelection } from './line-selection';
import { I18n, LANGUAGES, Lang, Translatable } from './i18n';
import { RefreshSettings } from './refresh';
import { ThemeSettings } from './theme';
import {
  LineMessage, LiveLineStops, LiveStop, MergedLiveLine, MergedStop, StopRef, TicketControl, WaitingTime, directionKey,
} from './stib.models';
import { StibService } from './stib.service';
import { WaitingTimes } from './waiting-times';

function isRateLimited(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 429;
}

/** Sidebar key of a line's merged stop list, alongside the `line/direction` keys of its directions. */
function mergedKey(lineId: string): string {
  return `${lineId}/*`;
}

/** Directions of one line, as shown in a sidebar group. */
interface LineGroup {
  lineId: string;
  directions: LiveLineStops[];
  /** The line's stops with its directions merged, when directions are merged. */
  merged: MergedStop[] | null;
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
  protected readonly merge = inject(DirectionMerge);
  protected readonly theme = inject(ThemeSettings);
  protected readonly themeOptions = [
    { theme: 'system', icon: '◐', label: 'theme.system' },
    { theme: 'light', icon: '☀', label: 'theme.light' },
    { theme: 'dark', icon: '☾', label: 'theme.dark' },
  ] as const;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly directions = signal<LiveLineStops[]>([]);
  /** The same lines with their directions merged; null when directions are not merged. */
  protected readonly mergedLines = signal<MergedLiveLine[] | null>(null);
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
    if (this.mergedLines()) {
      return new Set<string>(); // the eye toggles are per direction, which a merged list does not show
    }
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
    const merged = new Map(this.mergedLines()?.map(line => [line.lineId, line.stops]));
    return [...byLine].map(([lineId, directions]) => ({
      lineId,
      directions,
      merged: merged.get(lineId) ?? null,
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

  /** Platforms of the selected stop: with merged directions, those of every direction; otherwise just the one. */
  protected readonly selectedPlatformIds = computed(() => {
    const ref = this.selectedStop();
    if (!ref) {
      return [];
    }
    const merged = this.mergedLines()?.find(line => line.lineId === ref.lineId)?.stops
      .find(stop => stop.platforms.some(p => p.stopId === ref.stopId));
    return merged ? this.platformIds(merged) : [ref.stopId];
  });

  /** Messages about the selected stop on its line, plus that line's line-wide ones. */
  protected readonly selectedStopMessages = computed(() => {
    const ref = this.selectedStop();
    const platforms = this.selectedPlatformIds();
    return ref === null ? [] : this.messages().filter(m => m.lineIds.includes(ref.lineId)
      && (m.affectedStopIds.length === 0 || m.affectedStopIds.some(id => platforms.includes(id))));
  });

  /** Controls reported at any platform of the selected stop, most recent first. */
  protected readonly selectedStopControls = computed(() => this.selectedPlatformIds()
    .flatMap(id => this.controlsByStop().get(id) ?? [])
    .sort((a, b) => Date.parse(b.reportedAt) - Date.parse(a.reportedAt)));

  constructor() {
    const lineIds$ = toObservable(this.lines.lineIds).pipe(
      distinctUntilChanged((a, b) => a.join(',') === b.join(',')));

    // Reload immediately when the lines (or the merge setting) change, then on every refresh tick
    // for fresh vehicle positions.
    combineLatest([lineIds$, toObservable(this.merge.merged)])
      .pipe(
        tap(() => {
          this.directions.set([]);
          this.mergedLines.set(null);
          this.error.set(null);
        }),
        switchMap(([lineIds, merged]) => lineIds.length === 0 ? EMPTY : this.refresh.ticks().pipe(
          tap(() => this.loading.set(true)),
          switchMap(() => this.loadLines(lineIds, merged).pipe(
            tap(({ directions, mergedLines }) => {
              this.directions.set(directions);
              this.mergedLines.set(mergedLines);
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

    // Load waiting times for the selected stop (all its platforms when directions are merged) and
    // keep them fresh while it stays selected.
    toObservable(this.selectedPlatformIds)
      .pipe(
        distinctUntilChanged((a, b) => a.join(',') === b.join(',')),
        tap(() => {
          this.waitingTimes.set(null);
          this.waitingTimesError.set(null);
        }),
        switchMap(stopIds => stopIds.length === 0 ? of(null) : this.refresh.ticks().pipe(
          switchMap(() => forkJoin(stopIds.map(id => this.stib.getWaitingTimes(id))).pipe(
            map(times => times.flat()),
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

    // Service messages for the lines, reloaded at the backend's cache pace (5 min by default) rather
    // than on every refresh; on failure keep showing the previous ones.
    lineIds$
      .pipe(
        tap(() => this.messages.set([])),
        switchMap(lineIds => lineIds.length === 0 ? EMPTY : this.refresh.slowTicks().pipe(
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

  protected addControls(added: TicketControl[]): void {
    const ids = new Set(added.map(c => c.id));
    this.controls.update(controls => [...added, ...controls.filter(c => !ids.has(c.id))]);
  }

  private loadLines(lineIds: string[], merged: boolean):
    Observable<{ directions: LiveLineStops[]; mergedLines: MergedLiveLine[] | null }> {
    return merged
      ? this.stib.getMergedLiveStops(lineIds).pipe(
        map(lines => ({ directions: lines.flatMap(line => line.directions), mergedLines: lines })))
      : this.stib.getLiveStops(lineIds).pipe(map(directions => ({ directions, mergedLines: null })));
  }

  protected platformIds(stop: MergedStop): string[] {
    return [...new Set(stop.platforms.map(p => p.stopId))];
  }

  /** The platform of a merged stop in one direction, if that direction serves it. */
  protected platformIn(stop: MergedStop, direction: LiveLineStops) {
    return stop.platforms.find(p => p.direction === direction.direction);
  }

  protected hasVehicle(stop: MergedStop): boolean {
    return stop.platforms.some(p => p.vehiclePresent);
  }

  protected hasMessage(lineId: string, stopIds: string[]): boolean {
    return stopIds.some(id => this.stopsWithMessages().has(`${lineId}:${id}`));
  }

  /** Most recent control at any of the given platforms. */
  protected latestControl(stopIds: string[]): TicketControl | undefined {
    return stopIds.flatMap(id => this.controlsByStop().get(id) ?? [])
      .sort((a, b) => Date.parse(b.reportedAt) - Date.parse(a.reportedAt))[0];
  }

  /** "Stockel ⇄ Gare de l'Ouest" */
  protected mergedTitle(group: LineGroup): string {
    return group.directions.map(d => this.i18n.name(d.destination) ?? d.direction).join(' ⇄ ');
  }

  protected isMergedSelected(lineId: string, stop: MergedStop): boolean {
    const ref = this.selectedStop();
    return ref?.lineId === lineId && stop.platforms.some(p => p.stopId === ref.stopId);
  }

  protected isMergedExpanded(lineId: string): boolean {
    return this.expandedDirections().has(mergedKey(lineId));
  }

  protected toggleMerged(lineId: string): void {
    this.toggleExpanded(mergedKey(lineId));
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
    this.expandedDirections.set(new Set(expanded
      ? [...this.directions().map(directionKey), ...this.groups().map(g => mergedKey(g.lineId))] : []));
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
    this.toggleExpanded(directionKey(direction));
  }

  private toggleExpanded(key: string): void {
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
    if (direction && this.mergedLines()) {
      if (!this.isMergedExpanded(direction.lineId)) {
        this.toggleMerged(direction.lineId);
      }
    } else if (direction && !this.isDirectionExpanded(direction)) {
      this.toggleDirection(direction);
    }
    // Stops picked on the map may be out of view in the sidebar.
    afterNextRender(
      () => this.host.nativeElement.querySelector('.stops button.selected')?.scrollIntoView({ block: 'nearest' }),
      { injector: this.injector },
    );
  }
}
