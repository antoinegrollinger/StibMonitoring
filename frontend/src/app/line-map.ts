import { AfterViewInit, Component, ElementRef, OnDestroy, effect, inject, input, output, viewChild } from '@angular/core';
import * as L from 'leaflet';
import { routeColor } from './colors';
import { I18n } from './i18n';
import { ThemeSettings } from './theme';
import { LiveLineStops, LiveStop, StopRef, TicketControl, directionKey } from './stib.models';

const BRUSSELS: L.LatLngTuple = [50.8466, 4.3528];

type LocatedStop = LiveStop & { latitude: number; longitude: number };

/** Above this many lines, stops and vehicles are drawn smaller to keep the map readable. */
const COMPACT_THRESHOLD = 3;

/** Zoom level when centring on the user's location: enough to see the nearby stops. */
const LOCATION_ZOOM = 16;

const CROSSHAIR_ICON = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" '
  + 'stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2.5" fill="currentColor"/>'
  + '<path d="M12 1v4M12 19v4M1 12h4M19 12h4"/></svg>';

type LocationState = 'off' | 'locating' | 'on' | 'denied' | 'unavailable';

@Component({
  selector: 'app-line-map',
  template: '<div #map class="map"></div>',
  styles: `
    :host { display: block; }
    .map { width: 100%; height: 100%; }
  `,
})
export class LineMap implements AfterViewInit, OnDestroy {
  readonly directions = input.required<LiveLineStops[]>();
  /** Directions (`line/direction`) not drawn; they still count for colours and framing. */
  readonly hiddenDirections = input<ReadonlySet<string>>(new Set());
  /** Active ticket controls per stop id, most recent first. */
  readonly controls = input<ReadonlyMap<string, TicketControl[]>>(new Map());
  /** Changes whenever the map should re-frame the lines (i.e. a different set of lines was loaded). */
  readonly fitKey = input<string | null>(null);
  /** Lines shown, in display order; decides each line's colour. */
  readonly lineIds = input<readonly string[]>([]);
  readonly selectedStop = input<StopRef | null>(null);
  readonly stopSelected = output<StopRef>();

  private readonly i18n = inject(I18n);
  private readonly theme = inject(ThemeSettings);
  private readonly container = viewChild.required<ElementRef<HTMLElement>>('map');
  private map?: L.Map;
  /** Routes and stops: only rebuilt when the lines, the selection or the language change. */
  private readonly routeLayer = L.layerGroup();
  /** Vehicles: rebuilt on every refresh. */
  private readonly vehicleLayer = L.layerGroup();
  /** Reported ticket controls: rebuilt on every refresh. */
  private readonly controlLayer = L.layerGroup();
  /** The user's position and its accuracy circle, when they let the site use their location. */
  private readonly locationLayer = L.layerGroup();
  private locateButton?: HTMLAnchorElement;
  private locationState: LocationState = 'off';
  private position: L.LatLng | null = null;
  private watchId: number | null = null;
  /** Centre the map on the next position fix (set when the user presses the button). */
  private centreOnNextFix = false;
  private routeKey: string | null = null;
  private fittedKey: string | null = null;
  private revealedKey: string | null = null;

  constructor() {
    effect(() => this.render());
    // Keep the location button's label in the current language.
    effect(() => {
      this.i18n.lang();
      this.updateLocateButton();
    });
  }

  ngAfterViewInit(): void {
    // Canvas rendering copes with thousands of stop markers when all lines are shown.
    this.map = L.map(this.container().nativeElement, { zoomControl: true, preferCanvas: true }).setView(BRUSSELS, 12);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(this.map);
    this.routeLayer.addTo(this.map);
    this.vehicleLayer.addTo(this.map);
    this.controlLayer.addTo(this.map);
    this.locationLayer.addTo(this.map);
    this.addLocateControl(this.map);
    this.render();
    this.locateIfAlreadyAllowed();
  }

  ngOnDestroy(): void {
    if (this.watchId !== null) {
      navigator.geolocation.clearWatch(this.watchId);
    }
    this.map?.remove();
  }

  /** A button under the zoom buttons; pressing it asks for the location (the browser prompts once). */
  private addLocateControl(map: L.Map): void {
    const control = new L.Control({ position: 'topleft' });
    control.onAdd = () => {
      const bar = L.DomUtil.create('div', 'leaflet-bar locate-control');
      const button = L.DomUtil.create('a', 'locate-button', bar) as HTMLAnchorElement;
      button.href = '#';
      button.setAttribute('role', 'button');
      button.innerHTML = CROSSHAIR_ICON;
      L.DomEvent.disableClickPropagation(bar);
      L.DomEvent.on(button, 'click', e => {
        L.DomEvent.preventDefault(e);
        this.locate();
      });
      this.locateButton = button;
      this.updateLocateButton();
      return bar;
    };
    control.addTo(map);
  }

  /** Shows the location without prompting when the user already allowed it on an earlier visit. */
  private locateIfAlreadyAllowed(): void {
    navigator.permissions?.query({ name: 'geolocation' })
      .then(status => {
        if (status.state === 'granted') {
          this.startWatching();
        }
      })
      .catch(() => { /* Permissions API unsupported: wait for the button */ });
  }

  private locate(): void {
    if (this.position && this.map) {
      this.map.setView(this.position, Math.max(this.map.getZoom(), LOCATION_ZOOM));
      return;
    }
    this.centreOnNextFix = true;
    this.startWatching();
  }

  private startWatching(): void {
    if (!('geolocation' in navigator)) {
      this.setLocationState('unavailable');
      return;
    }
    if (this.watchId !== null) {
      return;
    }
    this.setLocationState('locating');
    this.watchId = navigator.geolocation.watchPosition(
      position => this.showPosition(position),
      error => {
        if (error.code === error.PERMISSION_DENIED) {
          navigator.geolocation.clearWatch(this.watchId!);
          this.watchId = null;
          this.locationLayer.clearLayers();
          this.position = null;
          this.setLocationState('denied');
        } else if (!this.position) {
          // Timeout or no fix yet: keep watching, a later fix may still come.
          this.setLocationState('unavailable');
        }
      },
      { enableHighAccuracy: true, maximumAge: 10_000 },
    );
  }

  private showPosition(position: GeolocationPosition): void {
    const { latitude, longitude, accuracy } = position.coords;
    this.position = L.latLng(latitude, longitude);
    this.locationLayer.clearLayers();
    L.circle(this.position, { radius: accuracy, color: '#1f6feb', weight: 1, opacity: 0.4, fillOpacity: 0.1, interactive: false })
      .addTo(this.locationLayer);
    L.circleMarker(this.position, { radius: 7, color: '#fff', weight: 2, fillColor: '#1f6feb', fillOpacity: 1 })
      .bindTooltip(escapeHtml(this.i18n.t('map.you')))
      .addTo(this.locationLayer);
    this.setLocationState('on');
    if (this.centreOnNextFix && this.map) {
      this.centreOnNextFix = false;
      this.map.setView(this.position, Math.max(this.map.getZoom(), LOCATION_ZOOM));
    }
  }

  private setLocationState(state: LocationState): void {
    this.locationState = state;
    this.updateLocateButton();
  }

  private updateLocateButton(): void {
    const button = this.locateButton;
    if (!button) {
      return;
    }
    const label = this.i18n.t(this.locationState === 'denied' ? 'map.locationDenied'
      : this.locationState === 'unavailable' ? 'map.locationUnavailable'
      : this.locationState === 'locating' ? 'map.locating'
      : 'map.locate');
    button.title = label;
    button.setAttribute('aria-label', label);
    button.dataset['state'] = this.locationState;
  }

  private render(): void {
    // Read every input up front so the effect tracks them even before the map exists.
    const directions = this.directions();
    const hidden = this.hiddenDirections();
    const controls = this.controls();
    const fitKey = this.fitKey();
    const lineIds = this.lineIds();
    const selected = this.selectedStop();
    const lang = this.i18n.lang();
    const dark = this.theme.dark();
    if (!this.map) {
      return;
    }

    const lineCount = new Set(directions.map(d => d.lineId)).size;
    const compact = lineCount > COMPACT_THRESHOLD;
    const colorOf = (direction: LiveLineStops, index: number) => routeColor(direction.lineId, index, lineIds);
    const directionIndex = indexWithinLine(directions);

    const routeKey = [
      directions.map(d => `${d.lineId}/${d.direction}/${d.stops.length}`).join(','),
      lineIds.join(','), selected?.lineId, selected?.stopId, lang, dark, [...hidden].join(','),
    ].join('|');
    let selectedPosition: L.LatLngTuple | null = null;

    if (routeKey !== this.routeKey) {
      this.routeKey = routeKey;
      this.routeLayer.clearLayers();
      directions.forEach((direction, i) => {
        if (hidden.has(directionKey(direction))) {
          return;
        }
        const color = colorOf(direction, directionIndex[i]);
        const stops = direction.stops.filter(hasLocation);
        L.polyline(stops.map(s => [s.latitude, s.longitude] as L.LatLngTuple), {
          color, weight: compact ? 3 : 4, opacity: 0.55, interactive: false,
        }).addTo(this.routeLayer);

        for (const stop of stops) {
          const isSelected = selected?.lineId === direction.lineId && selected.stopId === stop.id;
          const marker = L.circleMarker([stop.latitude, stop.longitude], {
            radius: isSelected ? 9 : compact ? 3 : 5,
            color,
            weight: isSelected ? 4 : compact ? 1.5 : 2,
            fillColor: isSelected ? color : dark ? '#161b22' : '#fff',
            fillOpacity: 1,
          })
            .bindTooltip(this.stopLabel(stop, direction))
            .on('click', () => this.stopSelected.emit({ lineId: direction.lineId, stopId: stop.id }))
            .addTo(this.routeLayer);
          if (isSelected) {
            marker.bringToFront();
          }
        }
      });
    }

    this.vehicleLayer.clearLayers();
    const bounds = L.latLngBounds([]);
    const size = compact ? 16 : 22;
    directions.forEach((direction, i) => {
      const color = colorOf(direction, directionIndex[i]);
      const stops = direction.stops.filter(hasLocation);
      const isHidden = hidden.has(directionKey(direction));
      stops.forEach((stop, s) => {
        bounds.extend([stop.latitude, stop.longitude]);
        if (selected?.lineId === direction.lineId && selected.stopId === stop.id) {
          selectedPosition = [stop.latitude, stop.longitude];
        }
        if (!stop.vehiclePresent || isHidden) {
          return;
        }
        // Point the arrow along the route: towards the next stop, or away from the previous one at the terminus.
        const heading = s + 1 < stops.length ? bearing(stop, stops[s + 1])
          : s > 0 ? bearing(stops[s - 1], stop) : 0;
        const label = lineCount > 1 ? `<b>${escapeHtml(direction.lineId)}</b>` : '';
        L.marker([stop.latitude, stop.longitude], {
          icon: L.divIcon({
            className: '',
            html: `<div class="vehicle-marker" style="width:${size}px;height:${size}px;background:${color};`
              + `transform:rotate(${heading}deg)">`
              + `<svg viewBox="0 0 10 10" width="${size / 2.2}" height="${size / 2.2}"><path d="M5 0 L10 10 L5 7 L0 10 Z" fill="#fff"/></svg>`
              + '</div>',
            iconSize: [size, size],
            iconAnchor: [size / 2, size / 2],
          }),
          zIndexOffset: 1000,
        })
          .bindTooltip(`${label} ${escapeHtml(this.i18n.t('map.vehicle'))} → ${this.destinationLabel(direction)}`
            + `<br>${this.stopLabel(stop, direction)}`)
          .on('click', () => this.stopSelected.emit({ lineId: direction.lineId, stopId: stop.id }))
          .addTo(this.vehicleLayer);
      });
    });

    // One pin per stop with a reported control, above the stop so the stop itself stays clickable.
    // Stops that are not on a shown direction get a stop marker of their own.
    this.controlLayer.clearLayers();
    const shownStops = new Map<string, { stop: LocatedStop; direction: LiveLineStops }>();
    for (const direction of directions) {
      if (!hidden.has(directionKey(direction))) {
        for (const stop of direction.stops.filter(hasLocation)) {
          if (!shownStops.has(stop.id)) {
            shownStops.set(stop.id, { stop, direction });
          }
        }
      }
    }
    for (const [stopId, [latest]] of controls) {
      const shown = shownStops.get(stopId);
      const position: L.LatLngTuple | null = shown ? [shown.stop.latitude, shown.stop.longitude]
        : latest.stop?.latitude != null && latest.stop.longitude != null ? [latest.stop.latitude, latest.stop.longitude]
        : null;
      if (!position) {
        continue;
      }
      const ref: StopRef = { lineId: shown?.direction.lineId ?? latest.lineId ?? '', stopId };
      const select = () => this.stopSelected.emit(ref);
      const stopLabel = shown ? this.stopLabel(shown.stop, shown.direction)
        : `<strong>${escapeHtml(this.i18n.name(latest.stop?.name) ?? stopId)}</strong>`;

      if (!shown) {
        const isSelected = selected?.stopId === stopId;
        if (isSelected) {
          selectedPosition = position;
        }
        L.circleMarker(position, {
          radius: isSelected ? 9 : 5,
          color: dark ? '#9198a1' : '#59636e',
          weight: isSelected ? 4 : 2,
          fillColor: dark ? '#161b22' : '#fff',
          fillOpacity: 1,
        })
          .bindTooltip(stopLabel)
          .on('click', select)
          .addTo(this.controlLayer);
      }

      const police = latest.type === 'POLICE';
      const what = this.i18n.t(police ? 'control.police' : 'control.controllers')
        + (latest.lineId ? ` · ${this.i18n.t('map.line', { line: latest.lineId })}` : '');
      L.marker(position, {
        icon: L.divIcon({
          className: '',
          html: `<div class="control-marker${police ? ' police' : ''}"><span>${police ? '🚓' : '🎫'}</span></div>`,
          iconSize: [24, 24],
          iconAnchor: [12, 34],
        }),
        zIndexOffset: 2000,
        title: `${this.i18n.t('control.mapLabel')}: ${what}`,
      })
        .bindTooltip(`<strong>${escapeHtml(what)}</strong>`
          + (latest.message ? `<br>${escapeHtml(latest.message)}` : '')
          + `<br>${stopLabel}`, { direction: 'top', offset: [0, -24] })
        .on('click', select)
        .addTo(this.controlLayer);
    }

    if (fitKey !== this.fittedKey && bounds.isValid()) {
      this.map.fitBounds(bounds, { padding: [32, 32] });
      this.fittedKey = fitKey;
    }

    // Bring a newly selected stop into view (e.g. when picked from the sidebar).
    const selectedKey = selected ? `${selected.lineId}:${selected.stopId}` : null;
    if (selectedKey !== this.revealedKey) {
      const position = selectedPosition as L.LatLngTuple | null;
      if (position && !this.map.getBounds().pad(-0.1).contains(position)) {
        this.map.panTo(position);
      }
      this.revealedKey = selectedKey;
    }
  }

  private destinationLabel(direction: LiveLineStops): string {
    return escapeHtml(this.i18n.name(direction.destination) ?? direction.direction);
  }

  private stopLabel(stop: LiveStop, direction: LiveLineStops): string {
    const shown = this.i18n.name(stop.name) ?? stop.id;
    const other = this.i18n.lang() === 'nl' ? stop.name?.fr : stop.name?.nl;
    const name = other && other !== shown ? `${shown} / ${other}` : shown;
    const line = this.i18n.t('map.line', { line: direction.lineId });
    return `<strong>${escapeHtml(name)}</strong><br>${escapeHtml(line)} → ${this.destinationLabel(direction)}`;
  }
}

/** For each direction, its position among the directions of the same line. */
function indexWithinLine(directions: LiveLineStops[]): number[] {
  const seen = new Map<string, number>();
  return directions.map(d => {
    const index = seen.get(d.lineId) ?? 0;
    seen.set(d.lineId, index + 1);
    return index;
  });
}

function hasLocation(stop: LiveStop): stop is LocatedStop {
  return stop.latitude !== null && stop.longitude !== null;
}

/** Compass bearing in degrees (0 = north, clockwise) from one stop to another. */
function bearing(from: LocatedStop, to: LocatedStop): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const lat1 = toRad(from.latitude);
  const lat2 = toRad(to.latitude);
  const dLon = toRad(to.longitude - from.longitude);
  const y = Math.sin(dLon) * Math.cos(lat2);
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
  return (Math.atan2(y, x) * 180) / Math.PI;
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, c => `&#${c.charCodeAt(0)};`);
}
