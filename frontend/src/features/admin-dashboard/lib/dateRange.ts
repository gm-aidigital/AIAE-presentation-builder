/** An inclusive window of days, as the dashboard and the API both understand it. */
export interface DateRange {
    from: string;
    to: string;
}

/** A named window offered in the picker. */
export interface RangePreset {
    id: string;
    label: string;
    range: (today: Date) => DateRange;
}

/** Formats a date as the plain `YYYY-MM-DD` the API expects, in local time. */
export function toIsoDay(date: Date): string {
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Reads a `YYYY-MM-DD` string as a local date, not a UTC instant. */
export function fromIsoDay(value: string): Date {
    const [year, month, day] = value.split("-").map(Number);
    return new Date(year, month - 1, day);
}

/** Days a window covers, both ends inclusive. */
export function spanInDays(range: DateRange): number {
    const ms = fromIsoDay(range.to).getTime() - fromIsoDay(range.from).getTime();
    return Math.round(ms / 86_400_000) + 1;
}

/**
 * Longest window still read week by week.
 *
 * Mirrors the server's own threshold. A little over a calendar month on purpose: a window like
 * 25 July – 5 August spans two months but is eleven days long, and reporting it month over month
 * would show two stubs instead of the weeks it actually contains.
 */
const WEEKLY_MAX_SPAN_DAYS = 40;

/** Picks the trend granularity a window reads best at. */
export function suggestedUnit(range: DateRange): "week" | "month" {
    return spanInDays(range) <= WEEKLY_MAX_SPAN_DAYS ? "week" : "month";
}

/** Shifts a date by whole days. */
function addDays(date: Date, days: number): Date {
    const shifted = new Date(date);
    shifted.setDate(shifted.getDate() + days);
    return shifted;
}

/**
 * The windows offered as one-click choices.
 *
 * Deliberately a mix of rolling and calendar windows. Rolling ones ("last 30 days") answer "how are
 * we doing"; calendar ones ("this month", "last month") are what gets reported to anyone else, and
 * a dashboard that can only do rolling windows quietly forces people into a spreadsheet.
 */
export const RANGE_PRESETS: RangePreset[] = [
    {
        id: "last7",
        label: "Last 7 days",
        range: (today) => ({ from: toIsoDay(addDays(today, -6)), to: toIsoDay(today) }),
    },
    {
        id: "last30",
        label: "Last 30 days",
        range: (today) => ({ from: toIsoDay(addDays(today, -29)), to: toIsoDay(today) }),
    },
    {
        id: "thisMonth",
        label: "This month",
        range: (today) => ({
            from: toIsoDay(new Date(today.getFullYear(), today.getMonth(), 1)),
            to: toIsoDay(today),
        }),
    },
    {
        id: "lastMonth",
        label: "Last month",
        range: (today) => ({
            from: toIsoDay(new Date(today.getFullYear(), today.getMonth() - 1, 1)),
            to: toIsoDay(new Date(today.getFullYear(), today.getMonth(), 0)),
        }),
    },
    {
        id: "last90",
        label: "Last 90 days",
        range: (today) => ({ from: toIsoDay(addDays(today, -89)), to: toIsoDay(today) }),
    },
    {
        id: "thisYear",
        label: "This year",
        range: (today) => ({
            from: toIsoDay(new Date(today.getFullYear(), 0, 1)),
            to: toIsoDay(today),
        }),
    },
];

/** The window the dashboard opens on. */
export function defaultRange(today = new Date()): DateRange {
    return RANGE_PRESETS[1].range(today);
}

const labelFmt = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric" });
const labelWithYearFmt = new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
});

/**
 * Human label for a window — "Jul 1 – Jul 31", or with years when it crosses one.
 *
 * The year is shown only when it disambiguates, because on a dashboard people open every day it is
 * noise the other 11 months of the year.
 */
export function formatRange(range: DateRange): string {
    const from = fromIsoDay(range.from);
    const to = fromIsoDay(range.to);
    const crossesYear = from.getFullYear() !== to.getFullYear();
    const thisYear = from.getFullYear() === new Date().getFullYear();
    const fmt = crossesYear || !thisYear ? labelWithYearFmt : labelFmt;
    return from.getTime() === to.getTime() ? fmt.format(from) : `${fmt.format(from)} – ${fmt.format(to)}`;
}

/** Finds which preset a window corresponds to, if any, so the picker can show it as selected. */
export function matchPreset(range: DateRange, today = new Date()): string | null {
    const match = RANGE_PRESETS.find((preset) => {
        const candidate = preset.range(today);
        return candidate.from === range.from && candidate.to === range.to;
    });
    return match ? match.id : null;
}
