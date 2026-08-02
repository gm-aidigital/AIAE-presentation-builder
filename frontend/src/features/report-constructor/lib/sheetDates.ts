// Date parsing/arithmetic shared by the media-plan and raw-data readers. Mirrors the
// backend's SheetRowHelper.parseDate patterns (the formats Google Sheets actually emits)
// so the UI never disagrees with the generated deck about when a flight ran.
// Dates are handled as ISO yyyy-MM-dd strings — they compare lexicographically and never
// pick up a timezone shift on the way through Date.

const MONTHS: Record<string, number> = {
    jan: 1, january: 1,
    feb: 2, february: 2,
    mar: 3, march: 3,
    apr: 4, april: 4,
    may: 5,
    jun: 6, june: 6,
    jul: 7, july: 7,
    aug: 8, august: 8,
    sep: 9, sept: 9, september: 9,
    oct: 10, october: 10,
    nov: 11, november: 11,
    dec: 12, december: 12,
};

function iso(year: number, month: number, day: number): string | null {
    if (month < 1 || month > 12 || day < 1 || day > 31) return null;
    const d = new Date(Date.UTC(year, month - 1, day));
    // Rejects overflow like 2026-02-31, which Date would silently roll forward.
    if (d.getUTCMonth() !== month - 1 || d.getUTCDate() !== day) return null;
    return `${year}-${`${month}`.padStart(2, "0")}-${`${day}`.padStart(2, "0")}`;
}

/**
 * Parses a sheet date cell to ISO yyyy-MM-dd, or null when it is blank or not a date.
 * Accepts "2026-06-03", "2026/6/3", "6/3/2026", "6-3-26", "Jun 3, 2026" and "3 June 2026",
 * with an optional trailing time (BigQuery exports sometimes carry "2026-06-03 00:00:00").
 */
export function parseSheetDate(raw: string | undefined | null): string | null {
    if (!raw) return null;
    const s = raw
        .replace(/\u00A0/g, " ")
        .trim()
        .replace(/[T ]\d{1,2}:\d{2}(:\d{2})?.*$/, "")
        .trim();
    if (!s) return null;

    let m = /^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/.exec(s);
    if (m) return iso(Number(m[1]), Number(m[2]), Number(m[3]));

    // US order — the media plans and BigQuery exports this app reads are all en-US.
    m = /^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2}|\d{4})$/.exec(s);
    if (m) {
        const year = m[3].length === 2 ? 2000 + Number(m[3]) : Number(m[3]);
        return iso(year, Number(m[1]), Number(m[2]));
    }

    m = /^([A-Za-z]{3,9})\.?\s+(\d{1,2}),?\s+(\d{4})$/.exec(s);
    if (m) {
        const month = MONTHS[m[1].toLowerCase()];
        return month ? iso(Number(m[3]), month, Number(m[2])) : null;
    }

    m = /^(\d{1,2})\s+([A-Za-z]{3,9})\.?,?\s+(\d{4})$/.exec(s);
    if (m) {
        const month = MONTHS[m[2].toLowerCase()];
        return month ? iso(Number(m[3]), month, Number(m[1])) : null;
    }
    return null;
}

/** Days between two ISO dates counting both ends ("Apr 3"–"Apr 4" = 2); 0 when end precedes start. */
export function daysInclusive(startIso: string, endIso: string): number {
    const start = Date.parse(`${startIso}T00:00:00Z`);
    const end = Date.parse(`${endIso}T00:00:00Z`);
    if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return 0;
    return Math.round((end - start) / 86_400_000) + 1;
}

/** Days both ranges share, counting both ends; 0 when they do not overlap. */
export function overlapDays(aStart: string, aEnd: string, bStart: string, bEnd: string): number {
    const start = aStart > bStart ? aStart : bStart;
    const end = aEnd < bEnd ? aEnd : bEnd;
    return daysInclusive(start, end);
}
