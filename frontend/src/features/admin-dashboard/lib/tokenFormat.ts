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
