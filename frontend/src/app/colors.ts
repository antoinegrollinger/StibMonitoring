/** With a single line shown, its directions get clearly distinct colours. */
const DIRECTION_COLORS = ['#1f6feb', '#d1242f', '#1a7f37', '#8250df'];

/**
 * Colour for a line, from its position among the lines shown: golden-angle steps around the
 * colour wheel keep the first few lines far apart, so a small selection is always easy to tell
 * apart. Lines are appended when added, so existing ones keep their colour.
 */
export function lineColor(lineId: string, shownLineIds: readonly string[]): string {
  const index = shownLineIds.indexOf(lineId);
  const position = index >= 0 ? index : hash(lineId);
  const hue = Math.round((position * 137.508) % 360);
  return `hsl(${hue} 70% 38%)`;
}

/** Colour of one direction of a line: per direction when one line is shown, per line otherwise. */
export function routeColor(lineId: string, directionIndex: number, shownLineIds: readonly string[]): string {
  return shownLineIds.length === 1
    ? DIRECTION_COLORS[directionIndex % DIRECTION_COLORS.length]
    : lineColor(lineId, shownLineIds);
}

function hash(value: string): number {
  let h = 0;
  for (const c of value) {
    h = (h * 31 + c.charCodeAt(0)) >>> 0;
  }
  return h;
}
