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

/** Frontend settings from GET /api/config. */
export interface FrontendConfig {
  refreshIntervalSeconds: number;
}
