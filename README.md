# StibMonitoring
Personal project to use STIB API

## Running

Requires Java 21+ and Maven. The STIB open data key is read from the `STIB-TOKEN`
environment variable (or `STIB_TOKEN`, since most shells can't `export` a name with a hyphen)
and sent as the `bmc-partner-key` header.

```sh
export STIB_TOKEN=<your key>
mvn spring-boot:run
curl http://localhost:8080/api/lines/1/stops
```

Response: one entry per direction of the line, with its stops in order:

```json
[{ "lineId": "1", "direction": "City",
   "destination": { "fr": "GARE DE L'OUEST", "nl": "WESTSTATION" },
   "stops": [{ "id": "8733", "order": 1 }, ...] }]
```

### Stop details for a line

```sh
curl http://localhost:8080/api/lines/1/stops/details
```

Same shape as above, but each stop also carries its name and GPS position, fetched from
`static/StopDetails` in a single batched call (`where=id in ("8733","8742",...)`):

```json
{ "id": "8733", "order": 1,
  "name": { "fr": "GARE DE L'OUEST", "nl": "WESTSTATION" },
  "latitude": 50.848995, "longitude": 4.320946 }
```

### Live vehicle presence

```sh
curl http://localhost:8080/api/lines/1/stops/live
```

Same as `/stops/details`, plus a `vehiclePresent` flag per stop, computed from
`rt/VehiclePositions` (`where=lineid=1`). STIB reports each vehicle as the last stop it passed
(`pointId`) plus the metres travelled since (`distanceFromPoint`). Each vehicle is assigned to
whichever of that stop and the next one it is closest to, using the straight-line (GPS) distance
between the two stops as the segment length.

### Waiting times at a stop

```sh
curl http://localhost:8080/api/stops/8742/waiting-times
```

Next passages of every line serving the stop, soonest first, from `rt/WaitingTimes`
(`where=pointid=8742`). `message` is set when STIB flags the time, e.g. `"Theoretical time"` when no
live data is available. If STIB rate-limits the key the backend answers `429`.

### Service messages for a line

```sh
curl http://localhost:8080/api/lines/1/messages
```

Disruption/works messages from `rt/TravellersInformation` that concern the line: messages tagged
with the line, plus stop-level messages (tagged with no line) about one of its stops. Sorted by STIB
priority (lower = more important). `affectedStopIds` lists the line's stops the message mentions,
and is empty for line-wide messages (including ones that list every stop, such as a strike).

### Ticket controls

```sh
curl -X POST http://localhost:8080/api/stops/8742/controls \
     -H 'Content-Type: application/json' \
     -d '{"type":"POLICE","lineId":"5","message":"At the exit","bothDirections":true}'
     # type: POLICE or CONTROLLERS; lineId, message and bothDirections optional
curl http://localhost:8080/api/controls                # every active report, most recent first
```

Travellers can report a ticket control at a stop, saying whether police are present or only
ticket controllers, optionally naming the line, with an optional message (`controls.max-message-length`, default 280).
Reports are kept in memory only and expire after `controls.ttl` (default 30 minutes), so they
are lost when the backend restarts. The stop's name and position are attached to each report, so
the frontend can show it even when none of the stop's lines is displayed.

STIB gives each platform its own stop id. With `bothDirections`, the control is also reported at
the platform(s) of the same stop in the line's other direction (without `lineId`, the first line
serving the stop is used); the response lists one report per platform.

### Merged directions

```sh
curl "http://localhost:8080/api/lines/live/merged?ids=1,5"
```

Each line once, with its `directions` (as returned by `/live`) and a single list of `stops` in the
order of the first direction. Each stop groups its `platforms`, one per direction serving it, each
with its own `vehiclePresent`. Platforms are grouped when they have the same name and lie within
500 m of each other; stops served in one direction only (branches, loops) appear on their own.

### Several lines at once

```sh
curl http://localhost:8080/api/lines                       # every line id: ["1","2",...,"T39","T81"]
curl "http://localhost:8080/api/lines/live?ids=1,5,92"     # live view of several lines
curl "http://localhost:8080/api/lines/messages?ids=1,5,92" # their service messages, each listed once
```

`/live` returns the same shape as `/{lineId}/stops/live`, for every direction of every requested
line. Each message from `/messages` carries the `lineIds` it concerns among those requested. The
single-line endpoints above still work.

Whatever the number of lines, the backend fetches each STIB dataset (`stopsByLine`,
`StopDetails`, `VehiclePositions`) for the whole network in a single call and filters in memory,
so showing all 74 lines costs the same STIB calls as showing one. JSON responses are gzipped
(about 70 KB for all lines).

### Caching

The traveller information dataset has no usable per-line filter, so the backend fetches all
messages in one call and caches them (`stib.cache.messages-ttl`, default 5 minutes). The static
`stopsByLine` and `StopDetails` datasets are cached too (`stib.cache.static-ttl`, default 1 hour),
which keeps the live endpoints down to a single STIB call (vehicle positions) per refresh.
Real-time data (vehicle positions, and waiting times per stop) is reused for `stib.cache.live-ttl`
(default 5 s): visitors refreshing at the same time share one STIB call, and concurrent requests
wait for that call rather than each making their own. Caching uses Caffeine through Spring's cache
abstraction.

### Rate limiting

Each client IP may make `rate-limit.requests-per-minute` API calls (default 180, in bursts up to
that many) and `rate-limit.reports-per-hour` ticket control reports (default 10). Beyond that the
API answers `429 Too Many Requests` with a `Retry-After` header. Behind a reverse proxy, set
`rate-limit.client-ip-header` (e.g. `X-Forwarded-For`, or `CF-Connecting-IP` behind Cloudflare) so
clients are told apart by their own address rather than the proxy's; leave it empty otherwise, as
clients could spoof it. Counters are kept in memory.

### Auto-refresh

The live view's default refresh interval is set in `application.yml` and served to the frontend by
`GET /api/config`:

```yaml
frontend:
  refresh-interval: 15s   # 0s disables auto-refresh
```

Users can override it from the toolbar (Off, 5–60 s, plus a "refresh now" button); their choice
is remembered per browser. Vehicles, waiting times and service messages all follow it.

Whether the sidebar merges each line's directions is set the same way, with a "Merge directions"
toggle in the toolbar overriding it per browser:

```yaml
frontend:
  merge-directions: false
```

## Frontend

Angular 21 app in [`frontend/`](frontend) that shows a line's stops and vehicles on an
OpenStreetMap map of Brussels (Leaflet), refreshing automatically. Pick lines in the toolbar
(type `1, 5, 92` and press Add, remove them with ×) or switch to **All lines**; the selection is
remembered per browser. With several lines, each gets its own colour and the sidebar groups stops
by line in collapsible sections. Clicking a stop, in the
sidebar or on the map, opens its upcoming passages (the selected line first, then other lines
serving the stop). Service messages for the line appear in a collapsible banner, stops they
mention are flagged with ⚠ in the sidebar, and the stop panel repeats the relevant ones.

The UI is available in English, French and Dutch (switcher in the toolbar; defaults to the browser
language and is remembered per browser). STIB data follows the chosen language too: stop names and
destinations are published in French and Dutch only, so English shows the French names, while
service messages and waiting-time remarks are published in all three languages. Interface strings
live in `frontend/src/app/i18n.ts`. Requests to `/api` are
proxied to the backend on `localhost:8080` (see `frontend/proxy.conf.json`).

Requires Node 22.12+ or 24+ (`frontend/.nvmrc` pins 22.12.0) and npm 11 — the npm 10.9.0 bundled
with Node 22.12 crashes resolving this dependency tree, so run `npm install -g npm@11` once after
`nvm use`.

```sh
# terminal 1 – backend
export STIB_TOKEN=<your key>
mvn spring-boot:run

# terminal 2 – frontend
cd frontend
nvm use
npm install
npm start          # http://localhost:4200
```
