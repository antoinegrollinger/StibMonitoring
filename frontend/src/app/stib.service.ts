import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { ControlType, FrontendConfig, LineMessage, LiveLineStops, MergedLiveLine, TicketControl, WaitingTime } from './stib.models';

@Injectable({ providedIn: 'root' })
export class StibService {
  private readonly http = inject(HttpClient);

  /** Fetched once and shared by the settings that need it. */
  private readonly config$ = this.http.get<FrontendConfig>('/api/config').pipe(shareReplay(1));

  getConfig(): Observable<FrontendConfig> {
    return this.config$;
  }

  getLineIds(): Observable<string[]> {
    return this.http.get<string[]>('/api/lines');
  }

  getLiveStops(lineIds: readonly string[]): Observable<LiveLineStops[]> {
    return this.http.get<LiveLineStops[]>('/api/lines/live', { params: { ids: lineIds.join(',') } });
  }

  getMergedLiveStops(lineIds: readonly string[]): Observable<MergedLiveLine[]> {
    return this.http.get<MergedLiveLine[]>('/api/lines/live/merged', { params: { ids: lineIds.join(',') } });
  }

  getLineMessages(lineIds: readonly string[]): Observable<LineMessage[]> {
    return this.http.get<LineMessage[]>('/api/lines/messages', { params: { ids: lineIds.join(',') } });
  }

  getWaitingTimes(stopId: string): Observable<WaitingTime[]> {
    return this.http.get<WaitingTime[]>(`/api/stops/${encodeURIComponent(stopId)}/waiting-times`);
  }

  getControls(): Observable<TicketControl[]> {
    return this.http.get<TicketControl[]>('/api/controls');
  }

  /** One report per platform: with `bothDirections`, the stop's platform(s) in the other direction too. */
  reportControl(stopId: string, lineId: string | null, type: ControlType, message: string | null,
                bothDirections: boolean): Observable<TicketControl[]> {
    return this.http.post<TicketControl[]>(`/api/stops/${encodeURIComponent(stopId)}/controls`,
      { type, lineId, message, bothDirections });
  }
}
