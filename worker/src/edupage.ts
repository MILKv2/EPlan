// Port of the parts of android/core/.../SubstitutionsParser.kt the worker needs:
// which classes and teachers each row of the substitutions page is about.
// Grammar: docs/edupage-api.md, section 3.

const BASE_URL = 'https://elektryk.edupage.org';

export interface Row {
  className: string;
  period: string;
  /** Text of the "what" cell; struck-out parts are wrapped in STRIKE_START/STRIKE_END. */
  what: string;
  /** Same for the "info" cell. */
  info: string;
  /** Full names the row names (planned, substitute or absent). */
  teachers: string[];
}

const STRIKE_START = '\u0001';
const STRIKE_END = '\u0002';

const LABELLED = /^([^:\u0001(]+):\s*(.*)$/;
const WITHOUT_CLASS = /^\((?:Bez klasy|Without class)\s+(.+)\)$/i;
const TEACHER_LABELS = new Set(['zastępstwa', 'zastępstwo', 'substitution', 'substitutions', 'nauczyciel', 'teacher']);

/** The day's HTML (the "r" string of getSubstViewerDayDataHtml). */
export async function fetchSubstitutionsHtml(date: string, fetchImpl: typeof fetch = fetch): Promise<string> {
  const response = await fetchImpl(`${BASE_URL}/substitution/server/viewer.js?__func=getSubstViewerDayDataHtml`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=UTF-8', 'User-Agent': 'E-Plan-worker (nieoficjalna apka)' },
    // __gsh is required; "00000000" is what the public page uses.
    body: JSON.stringify({ __args: [null, { date, mode: 'classes' }], __gsh: '00000000' }),
  });
  if (!response.ok) throw new Error(`EduPage HTTP ${response.status} for ${date}`);
  const json = (await response.json()) as { r?: unknown };
  if (typeof json.r !== 'string') throw new Error(`EduPage: unexpected substitutions response for ${date}`);
  return json.r;
}

/** Rows in page order; empty for "no substitutions" days. */
export function parseSubstitutions(html: string): Row[] {
  const rows: Row[] = [];
  // One <tbody> per class; the class name is only in the first row (rowspan).
  for (const tbody of html.split(/<tbody\b/).slice(1)) {
    let className = '';
    for (const tr of tbody.split(/<tr\b/).slice(1)) {
      const cells: Record<string, string> = {};
      for (const match of tr.matchAll(/<td\b([^>]*)>([\s\S]*?)<\/td>/g)) {
        const cssClass = /\bclass="([^"]*)"/.exec(match[1] ?? '')?.[1]?.split(/\s+/)[0];
        if (cssClass) cells[cssClass] = match[2] ?? '';
      }
      if (cells.header !== undefined) className = markedText(cells.header);
      const period = markedText(cells.period ?? '');
      const what = markedText(cells.what ?? '');
      const info = markedText(cells.info ?? '');
      // The "no substitutions" row has only an info cell.
      if (!className && !period && !what) continue;
      rows.push({ className, period, what, info, teachers: teachersOf(info) });
    }
  }
  return rows;
}

/** Names in the info cell: "Zastępstwa: <s>A</s> ➔ B", "Nauczyciel: A", "A, Anulowano", "<s>A</s>" (no substitute). */
function teachersOf(info: string): string[] {
  const names: string[] = [];
  let bare = false;
  for (const part of splitTopLevel(info)) {
    const labelled = LABELLED.exec(part);
    if (labelled) {
      if (TEACHER_LABELS.has((labelled[1] ?? '').trim().toLowerCase())) names.push(...changeValues(labelled[2] ?? ''));
      continue;
    }
    if (/^(anulowano|cancelled)$/i.test(part) || WITHOUT_CLASS.test(part)) continue;
    if (part.startsWith(STRIKE_START) && part.endsWith(STRIKE_END)) {
      names.push(...changeValues(part));
    } else if (!bare && !part.includes(STRIKE_START)) {
      // Bare name, as in "X, Anulowano" / "X, Moved to period: 4".
      names.push(part);
      bare = true;
    }
  }
  return [...new Set(names.map(clean).filter(Boolean))];
}

/** "<s>A</s> ➔ B" → [A, B]; "<s>A</s>" → [A]; "A" → [A]. */
function changeValues(value: string): string[] {
  const start = value.indexOf(STRIKE_START);
  const end = value.indexOf(STRIKE_END);
  if (start < 0 || end < start) return [value];
  return [value.slice(start + 1, end), value.slice(end + 1).replace(/➔|->/g, '')];
}

/** Splits on commas outside parentheses, so "(Bez klasy 1a, 2b)" stays whole. */
function splitTopLevel(text: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let current = '';
  for (const ch of text) {
    if (ch === '(') depth++;
    if (ch === ')') depth = Math.max(0, depth - 1);
    if (ch === ',' && depth === 0) {
      parts.push(current.trim());
      current = '';
    } else {
      current += ch;
    }
  }
  parts.push(current.trim());
  return parts.filter(Boolean);
}

/** Cell HTML → text with struck-out parts marked. */
function markedText(html: string): string {
  return clean(
    decodeEntities(
      html
        .replace(/<s\b[^>]*>/gi, STRIKE_START)
        .replace(/<\/s>/gi, STRIKE_END)
        .replace(/<[^>]*>/g, ''),
    ),
  );
}

function decodeEntities(text: string): string {
  return text
    .replace(/&nbsp;/g, ' ')
    .replace(/&#(\d+);/g, (_, code: string) => String.fromCodePoint(Number(code)))
    .replace(/&#x([0-9a-f]+);/gi, (_, code: string) => String.fromCodePoint(parseInt(code, 16)))
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

function clean(text: string): string {
  return text.replace(/ /g, ' ').replace(/\s+/g, ' ').trim();
}
