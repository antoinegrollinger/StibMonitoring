import { Component, computed, inject, input, output } from '@angular/core';
import { I18n, Translatable } from './i18n';
import { LineMessage, LiveLineStops, LiveStop, WaitingTime } from './stib.models';

@Component({
  selector: 'app-waiting-times',
  template: `
    <header>
      <div>
        <h2>{{ i18n.name(stop().name) ?? stop().id }}</h2>
        @if (otherName(); as other) {
          <p class="other-name">{{ other }}</p>
        }
      </div>
      <button type="button" class="close" [attr.aria-label]="i18n.t('stop.close')" (click)="closed.emit()">×</button>
    </header>

    @for (message of messages(); track message.id) {
      <p class="message"><span aria-hidden="true">⚠</span> {{ i18n.text(message.text) }}</p>
    }

    @if (times() === null && error(); as message) {
      <p class="muted">{{ i18n.translate(message) }}</p>
    } @else if (times() === null) {
      <p class="muted">{{ i18n.t('stop.loading') }}</p>
    } @else if (rows().length === 0) {
      <p class="muted">{{ i18n.t('stop.noPassages') }}</p>
    } @else {
      <ul>
        @for (row of rows(); track row.key) {
          <li [class.current]="row.lineId === direction().lineId">
            <span class="line">{{ row.lineId }}</span>
            <span class="destination">
              {{ row.destination }}
              @if (row.message) {
                <small>{{ row.message }}</small>
              }
            </span>
            <span class="eta">{{ row.eta }}</span>
          </li>
        }
      </ul>
    }
  `,
  styleUrl: './waiting-times.scss',
})
export class WaitingTimes {
  readonly stop = input.required<LiveStop>();
  readonly direction = input.required<LiveLineStops>();
  /** null while loading. */
  readonly times = input.required<WaitingTime[] | null>();
  /** Message for the last failed refresh; earlier times stay visible when a refresh fails. */
  readonly error = input<Translatable | null>(null);
  /** Current time, ticked by the parent so the countdowns stay fresh. */
  readonly now = input.required<number>();
  /** Service messages concerning this stop. */
  readonly messages = input<LineMessage[]>([]);
  readonly closed = output<void>();

  protected readonly i18n = inject(I18n);

  /** The stop's name in the other official language, when it differs. */
  protected readonly otherName = computed(() => {
    const name = this.stop().name;
    const shown = this.i18n.name(name);
    const other = this.i18n.lang() === 'nl' ? name?.fr : name?.nl;
    return other && other !== shown ? other : null;
  });

  /** Passages of the selected line first, then the other lines serving this stop, each soonest first. */
  protected readonly rows = computed(() => {
    const now = this.now();
    const lineId = this.direction().lineId;
    return (this.times() ?? [])
      .map((t, i) => ({
        key: `${t.lineId}-${t.expectedArrivalTime}-${i}`,
        lineId: t.lineId,
        destination: this.i18n.name(t.destination) ?? '',
        message: this.i18n.text(t.message),
        at: Date.parse(t.expectedArrivalTime),
      }))
      .filter(r => r.at >= now - 60_000)
      .sort((a, b) => Number(b.lineId === lineId) - Number(a.lineId === lineId) || a.at - b.at)
      .map(r => ({ ...r, eta: this.formatEta(r.at - now) }));
  });

  private formatEta(ms: number): string {
    const minutes = Math.floor(ms / 60_000);
    return minutes <= 0 ? this.i18n.t('eta.now') : this.i18n.t('eta.minutes', { minutes });
  }
}
