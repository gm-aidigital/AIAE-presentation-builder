// Display formatting for the admin dashboard's token-consumption figures.

const usdFmt = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
const usdPreciseFmt = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
});

/**
 * Compact token count — 1.2M / 340k / 812. Raw counts run to eight digits and turn every
 * table column into a wall of numbers, so magnitudes are what the dashboard shows.
 */
export function formatTokens(value: number): string {
    if (!Number.isFinite(value) || value <= 0) return "0";
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(value >= 10_000_000 ? 0 : 1)}M`;
    if (value >= 1_000) return `${(value / 1_000).toFixed(value >= 10_000 ? 0 : 1)}k`;
    return String(Math.round(value));
}

/**
 * Dollar amount. Sub-cent figures keep extra decimals, because a cheap report rendered as
 * "$0.00" reads as "not measured" rather than "measured and tiny".
 */
export function formatUsd(value: number): string {
    if (!Number.isFinite(value)) return usdFmt.format(0);
    return value > 0 && value < 0.01 ? usdPreciseFmt.format(value) : usdFmt.format(value);
}

/**
 * Signed percentage change — "+18%" / "−7%" / "—".
 *
 * An undefined delta renders as a dash rather than as zero. The backend leaves it undefined when
 * there is no previous period, or when the previous period was zero; both mean "no comparison
 * exists", and showing 0% would claim the opposite — that nothing changed.
 */
export function formatDelta(value: number | undefined): string {
    if (value === undefined || !Number.isFinite(value)) return "—";
    const rounded = Math.abs(value) >= 100 ? Math.round(value) : Math.round(value * 10) / 10;
    if (rounded === 0) return "0%";
    // U+2212 minus, not a hyphen: it aligns with the digits at these sizes.
    return rounded > 0 ? `+${rounded}%` : `−${Math.abs(rounded)}%`;
}

/**
 * Direction of a change, for styling. Undefined and exactly-flat both read as neutral.
 */
export function deltaTone(value: number | undefined): "up" | "down" | "flat" {
    if (value === undefined || !Number.isFinite(value) || Math.round(value * 10) === 0) return "flat";
    return value > 0 ? "up" : "down";
}

/**
 * Hours as "6h 15m" / "18m" / "1,240h".
 *
 * Minutes are dropped past a hundred hours: at that size they are noise, and the figure is a model
 * rather than a measurement anyway.
 */
export function formatHours(value: number): string {
    if (!Number.isFinite(value) || value <= 0) return "0h";
    if (value >= 100) return `${Math.round(value).toLocaleString("en-US")}h`;
    const hours = Math.floor(value);
    const minutes = Math.round((value - hours) * 60);
    if (hours === 0) return `${minutes}m`;
    return minutes === 0 ? `${hours}h` : `${hours}h ${minutes}m`;
}
