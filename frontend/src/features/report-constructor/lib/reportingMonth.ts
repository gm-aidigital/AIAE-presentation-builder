// EOM decks always report on the month that just closed: a user opening the
// constructor on 1 August wants July. The default is the detected campaign range
// clipped to that month, so a campaign that started 17 July gives 17–31 July.
// The window stays editable — this only supplies the default.

/** ISO yyyy-MM-dd for a local date (never UTC-shifted like toISOString). */
export function toIsoDate(date: Date): string {
    const month = `${date.getMonth() + 1}`.padStart(2, "0");
    const day = `${date.getDate()}`.padStart(2, "0");
    return `${date.getFullYear()}-${month}-${day}`;
}

export interface DateWindow {
    start: string;
    end: string;
}

/** First and last day of the calendar month before `today`, as ISO dates. */
export function previousMonthWindow(today: Date = new Date()): DateWindow {
    const start = new Date(today.getFullYear(), today.getMonth() - 1, 1);
    const end = new Date(today.getFullYear(), today.getMonth(), 0);
    return { start: toIsoDate(start), end: toIsoDate(end) };
}

/**
 * The detected campaign range narrowed to the previous calendar month — the EOM
 * reporting window. A campaign live 17 Jul–30 Sep reported on 1 Aug gives
 * 17–31 July. Falls back to the detected range when the campaign did not run in
 * that month at all, so the user still sees real dates to correct.
 * ISO yyyy-MM-dd strings compare lexicographically, so plain min/max is enough.
 */
export function eomWindow(detected: DateWindow, today: Date = new Date()): DateWindow {
    const month = previousMonthWindow(today);
    const start = detected.start > month.start ? detected.start : month.start;
    const end = detected.end < month.end ? detected.end : month.end;
    return start > end ? detected : { start, end };
}
