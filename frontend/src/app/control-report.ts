import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { outputFromObservable, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { I18n, TranslationKey } from './i18n';
import { ControlType, LiveLineStops, StopRef, TicketControl } from './stib.models';
import { StibService } from './stib.service';

/** Must match `controls.max-message-length` on the backend. */
const MAX_MESSAGE_LENGTH = 280;

/**
 * Floating "Report a ticket control" button over the map, opening a small form. Any line of the
 * network can be picked; the stops of a line that is not on the map are loaded on demand. Tapping
 * a stop on the map fills it in, and picking a stop of a shown line selects it on the map.
 */
@Component({
  selector: 'app-control-report',
  template: `
    @if (!open()) {
      <button type="button" class="fab" (click)="start()">
        <span aria-hidden="true">🚨</span> {{ i18n.t('control.report') }}
      </button>
      @if (sent()) {
        <p class="thanks" role="status">{{ i18n.t('control.thanks') }}</p>
      }
    } @else {
      <form class="panel" (submit)="$event.preventDefault(); submit()">
        <header>
          <h2>{{ i18n.t('control.report') }}</h2>
          <button type="button" class="close" [attr.aria-label]="i18n.t('control.cancel')" (click)="open.set(false)">×</button>
        </header>

        <label class="field">
          {{ i18n.t('control.line') }}
          <select (change)="setLine($any($event.target).value)">
            <option value="" [selected]="line() === null">{{ i18n.t('control.anyLine') }}</option>
            @for (lineId of lineChoices(); track lineId) {
              <option [value]="lineId" [selected]="lineId === line()">{{ lineId }}</option>
            }
          </select>
        </label>

        <label class="field">
          {{ i18n.t('control.stop') }}
          <select required [disabled]="stopGroups().length === 0" (change)="setStop($any($event.target).value)">
            <option value="" disabled [selected]="stopValue() === ''">{{ i18n.t('control.stopPlaceholder') }}</option>
            @for (group of stopGroups(); track group.key) {
              <optgroup [label]="group.label">
                @for (stop of group.stops; track stop.value) {
                  <option [value]="stop.value" [selected]="stop.value === stopValue()">{{ stop.name }}</option>
                }
              </optgroup>
            }
          </select>
          <small>
            @if (loadingStops()) {
              {{ i18n.t('control.loadingStops') }}
            } @else if (stopGroups().length === 0) {
              {{ i18n.t('control.chooseLineFirst') }}
            } @else {
              {{ i18n.t('control.stopHint') }}
            }
          </small>
        </label>

        <label class="checkbox">
          <input type="checkbox" [checked]="bothDirections()" (change)="bothDirections.set($any($event.target).checked)" />
          {{ i18n.t('control.bothDirections') }}
        </label>

        <fieldset>
          <legend>{{ i18n.t('control.question') }}</legend>
          <label>
            <input type="radio" name="control-type" value="POLICE" [checked]="type() === 'POLICE'"
                   (change)="type.set('POLICE')" />
            {{ i18n.t('control.police') }}
          </label>
          <label>
            <input type="radio" name="control-type" value="CONTROLLERS" [checked]="type() === 'CONTROLLERS'"
                   (change)="type.set('CONTROLLERS')" />
            {{ i18n.t('control.controllers') }}
          </label>
        </fieldset>

        <label class="field">
          {{ i18n.t('control.message') }}
          <textarea rows="2" [maxLength]="maxLength" [placeholder]="i18n.t('control.messagePlaceholder')"
                    [value]="message()" (input)="message.set($any($event.target).value)"></textarea>
        </label>

        @if (error(); as message) {
          <p class="error" role="alert">{{ i18n.t(message) }}</p>
        }
        <div class="actions">
          <button type="button" (click)="open.set(false)">{{ i18n.t('control.cancel') }}</button>
          <button type="submit" class="primary" [disabled]="!canSubmit()">
            {{ sending() ? i18n.t('control.sending') : i18n.t('control.submit') }}
          </button>
        </div>
      </form>
    }
  `,
  styles: `
    :host { display: block; }

    button, select, textarea {
      font: inherit;
      color: var(--text);
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 6px;
    }

    button { padding: 4px 10px; cursor: pointer; }

    .fab {
      padding: 8px 14px;
      font-weight: 600;
      color: #fff;
      background: #cf222e;
      border: 0;
      border-radius: 999px;
      box-shadow: 0 2px 8px var(--shadow);
    }

    .thanks {
      margin: 6px 0 0;
      padding: 4px 8px;
      font-size: 0.8rem;
      background: var(--surface);
      border-radius: 6px;
      box-shadow: 0 2px 8px var(--shadow);
    }

    .panel {
      width: 320px;
      max-width: 100%;
      display: flex;
      flex-direction: column;
      gap: 10px;
      max-height: calc(100vh - 160px);
      overflow-y: auto;
      padding: 12px 16px;
      font-size: 0.85rem;
      background: var(--surface);
      border-radius: 8px;
      box-shadow: 0 4px 16px var(--shadow);
    }

    header { display: flex; align-items: center; justify-content: space-between; }

    h2 { margin: 0; font-size: 1rem; }

    .close {
      padding: 0 4px;
      font-size: 1.4rem;
      line-height: 1;
      color: var(--text-muted);
      background: none;
      border: 0;
    }

    .field { display: flex; flex-direction: column; gap: 4px; font-weight: 600; }

    .field small { font-weight: normal; color: var(--text-muted); }

    select, textarea { padding: 4px 6px; font-weight: normal; }

    textarea { resize: vertical; }

    fieldset {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin: 0;
      padding: 0;
      border: 0;
    }

    legend { margin-bottom: 4px; font-weight: 600; }

    fieldset label, .checkbox { display: flex; align-items: center; gap: 6px; }

    .actions { display: flex; justify-content: flex-end; gap: 6px; }

    .primary {
      color: #fff;
      background: var(--accent);
      border-color: var(--accent);

      &:disabled { opacity: 0.5; cursor: default; }
    }

    .error { margin: 0; color: var(--danger-text); }

    /* Phones: a bottom sheet over the sidebar, so stops can still be tapped on the map above. */
    @media (max-width: 700px) {
      .fab { padding: 8px 12px; font-size: 0.85rem; }

      .panel {
        position: fixed;
        right: 0;
        bottom: 0;
        left: 0;
        width: auto;
        max-height: 55dvh;
        padding-bottom: max(12px, env(safe-area-inset-bottom));
        border-radius: 12px 12px 0 0;
      }

      /* 16px keeps iOS from zooming in when a field gets focus. */
      select, textarea { font-size: 16px; }

      button { padding: 8px 14px; }

      .close { padding: 4px 8px; }

      fieldset label, .checkbox { min-height: 32px; }
    }
  `,
})
export class ControlReport {
  /** Directions of the lines shown on the map. */
  readonly directions = input.required<LiveLineStops[]>();
  /** Lines shown on the map. */
  readonly lineIds = input.required<readonly string[]>();
  /** Every line of the network (empty until loaded). */
  readonly allLineIds = input<readonly string[]>([]);
  /** The map's selected stop. */
  readonly selectedStop = input<StopRef | null>(null);
  /** Whether directions are merged; then "both directions" is ticked by default. */
  readonly merged = input(false);
  readonly stopSelected = output<StopRef>();
  /** One report per platform. */
  readonly reported = output<TicketControl[]>();

  protected readonly i18n = inject(I18n);
  private readonly stib = inject(StibService);
  protected readonly maxLength = MAX_MESSAGE_LENGTH;

  protected readonly open = signal(false);
  /** Emits when the form opens or closes. */
  readonly openChange = outputFromObservable(toObservable(this.open));
  /** Line the control concerns, if the reporter says; also narrows the stop list. */
  protected readonly line = signal<string | null>(null);
  /** Stop the report is for; follows the map's selection, but can be a stop of a line not shown. */
  private readonly picked = signal<StopRef | null>(null);
  protected readonly type = signal<ControlType | null>(null);
  /** Also report the control at the stop's platform in the other direction. */
  protected readonly bothDirections = signal(false);
  protected readonly message = signal('');
  protected readonly sending = signal(false);
  protected readonly error = signal<TranslationKey | null>(null);
  protected readonly sent = signal(false);
  private sentTimer?: ReturnType<typeof setTimeout>;

  protected readonly lineChoices = computed(() => this.allLineIds().length ? this.allLineIds() : this.lineIds());

  /** Directions of the chosen line when it is not on the map; null while loading or when not needed. */
  private readonly fetched = toSignal(toObservable(computed(() => {
    const line = this.line();
    return line !== null && !this.lineIds().includes(line) ? line : null;
  })).pipe(
    switchMap(line => line === null ? of(null) : this.stib.getLiveStops([line]).pipe(
      map(directions => ({ line, directions })),
      catchError(() => of({ line, directions: [] as LiveLineStops[] })),
      startWith(null),
    )),
  ), { initialValue: null });

  protected readonly loadingStops = computed(() => {
    const line = this.line();
    return line !== null && !this.lineIds().includes(line) && this.fetched()?.line !== line;
  });

  /** Directions whose stops can be picked: the chosen line's, or all shown ones when no line is chosen. */
  private readonly available = computed(() => {
    const line = this.line();
    if (line === null) {
      return this.directions();
    }
    if (this.lineIds().includes(line)) {
      return this.directions().filter(d => d.lineId === line);
    }
    const fetched = this.fetched();
    return fetched?.line === line ? fetched.directions : [];
  });

  /** Stops to pick from, one group per direction ("5 → Stockel"). Option values are `line:stop`. */
  protected readonly stopGroups = computed(() =>
    this.available()
      .map(d => ({
        key: `${d.lineId}/${d.direction}`,
        label: `${d.lineId} → ${this.i18n.name(d.destination) ?? d.direction}`,
        stops: d.stops.map(s => ({ value: `${d.lineId}:${s.id}`, name: this.i18n.name(s.name) ?? s.id })),
      })));

  /** The picked stop as an option value, or '' when it is not in the list (e.g. on another line). */
  protected readonly stopValue = computed(() => {
    const ref = this.picked();
    if (!ref) {
      return '';
    }
    const line = this.line() ?? ref.lineId;
    const listed = this.available().some(d => d.lineId === line && d.stops.some(s => s.id === ref.stopId));
    return listed ? `${line}:${ref.stopId}` : '';
  });

  constructor() {
    // A stop tapped on the map becomes the form's stop.
    effect(() => {
      const ref = this.selectedStop();
      untracked(() => this.picked.set(ref));
    });
  }

  protected readonly canSubmit = computed(() => this.stopValue() !== '' && this.type() !== null && !this.sending());

  protected start(): void {
    this.line.set(null);
    this.type.set(null);
    this.bothDirections.set(this.merged());
    this.message.set('');
    this.error.set(null);
    this.sent.set(false);
    this.open.set(true);
  }

  protected setLine(value: string): void {
    this.line.set(value || null);
    // Keep the picked stop if the chosen line serves it too, so the map shows that line's direction.
    const ref = this.picked();
    if (value && ref && ref.lineId !== value && this.stopValue() !== '') {
      this.pick({ lineId: value, stopId: ref.stopId });
    }
  }

  protected setStop(value: string): void {
    const [lineId, stopId] = value.split(':');
    if (lineId && stopId) {
      this.pick({ lineId, stopId });
    }
  }

  /** Stops of lines on the map are also selected there; others only exist in the form. */
  private pick(ref: StopRef): void {
    this.picked.set(ref);
    if (this.lineIds().includes(ref.lineId)) {
      this.stopSelected.emit(ref);
    }
  }

  protected submit(): void {
    const type = this.type();
    const stopId = this.stopValue().split(':')[1];
    if (!this.canSubmit() || type === null || !stopId) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.stib.reportControl(stopId, this.line(), type, this.message().trim() || null, this.bothDirections()).subscribe({
      next: controls => {
        this.sending.set(false);
        this.open.set(false);
        this.sent.set(true);
        clearTimeout(this.sentTimer);
        this.sentTimer = setTimeout(() => this.sent.set(false), 5000);
        this.reported.emit(controls);
      },
      error: err => {
        this.sending.set(false);
        this.error.set(err instanceof HttpErrorResponse && err.status === 429 ? 'control.rateLimited' : 'control.error');
      },
    });
  }
}
