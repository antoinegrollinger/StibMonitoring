export interface LocalizedName {
  fr: string | null;
  nl: string | null;
}

export interface LiveStop {
  id: string;
  order: number;
  name: LocalizedName | null;
  latitude: number | null;
  longitude: number | null;
  vehiclePresent: boolean;
}

/** One direction of a line, as returned by GET /api/lines/{lineId}/stops/live. */
export interface LiveLineStops {
  lineId: string;
  direction: string;
  destination: LocalizedName;
  stops: LiveStop[];
}

/** Next passage at a stop, as returned by GET /api/stops/{stopId}/waiting-times. */
export interface WaitingTime {
  lineId: string;
  destination: LocalizedName | null;
  expectedArrivalTime: string;
  message: LocalizedText | null;
}

export interface LocalizedText {
  en: string | null;
  fr: string | null;
  nl: string | null;
}

/** Service message, as returned by GET /api/lines/messages?ids=... */
export interface LineMessage {
  id: string;
  /** Lower is more important. */
  priority: number;
  text: LocalizedText;
  /** The requested lines the message concerns. */
  lineIds: string[];
  /** Stops of those lines the message mentions; empty when it is about the lines as a whole. */
  affectedStopIds: string[];
}

/** A stop as seen from one line (a platform can be shared by several lines). */
export interface StopRef {
  lineId: string;
  stopId: string;
}

export type ControlType = 'POLICE' | 'CONTROLLERS';

/** Ticket control reported by a traveller, as returned by GET /api/controls. */
export interface TicketControl {
  id: string;
  stopId: string;
  /** Name and position of the stop; null if STIB's stop data was unavailable when it was reported. */
  stop: { id: string; name: LocalizedName | null; latitude: number | null; longitude: number | null } | null;
  /** Line the reporter said the control concerns; null when not specified. */
  lineId: string | null;
  /** POLICE: police officers present; CONTROLLERS: STIB ticket controllers only. */
  type: ControlType;
  message: string | null;
  reportedAt: string;
  expiresAt: string;
}

/** One direction's platform of a merged stop. */
export interface MergedPlatform {
  direction: string;
  stopId: string;
  vehiclePresent: boolean;
}

/** A stop of a line with its directions merged: one platform per direction serving it. */
export interface MergedStop {
  /** Id of the first platform, usable as a key. */
  id: string;
  name: LocalizedName | null;
  latitude: number | null;
  longitude: number | null;
  platforms: MergedPlatform[];
}

/** A line with its directions merged, as returned by GET /api/lines/live/merged?ids=... */
export interface MergedLiveLine {
  lineId: string;
  /** The per-direction view, as returned by GET /api/lines/live. */
  directions: LiveLineStops[];
  /** Stops in the order of the first direction. */
  stops: MergedStop[];
}

/** Frontend settings from GET /api/config. */
export interface FrontendConfig {
  refreshIntervalSeconds: number;
  /** Whether directions are merged by default. */
  mergeDirections: boolean;
  /** How often to reload traveller messages; the backend caches them this long. */
  messagesRefreshSeconds: number;
}

/** Identifies one direction of one line, e.g. `1/V`. */
export function directionKey(direction: LiveLineStops): string {
  return `${direction.lineId}/${direction.direction}`;
}
