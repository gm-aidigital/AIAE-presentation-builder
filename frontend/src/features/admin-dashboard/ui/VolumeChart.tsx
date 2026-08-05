import type { AdminTokenPeriod } from "@/shared/api/types";
import { formatTokens, formatUsd } from "../lib/tokenFormat";

/**
 * Report volume across the selected window, one bar per bucket.
 *
 * The bucket size comes from the server — days for a short window, weeks or months for a long one —
 * because a chart of four hundred daily bars is not a chart. Buckets with no activity are drawn as
 * stubs rather than omitted: a gap in a bar chart reads as a shorter period, not a quiet one.
 *
 * Labels thin out as the series grows, so they stay legible instead of overlapping into a smear.
 */
export function VolumeChart({
    series,
    unit,
    metric = "reports",
}: {
    series: AdminTokenPeriod[];
    unit: string;
    metric?: "reports" | "tokens";
}) {
    if (series.length === 0) {
        return <div className="ad-rail__empty">Nothing in this period.</div>;
    }

    const value = (point: AdminTokenPeriod) =>
        metric === "tokens" ? point.totalTokens : point.reports;
    const peak = Math.max(1, ...series.map(value));
    // One label per bar up to a dozen, then every Nth, so they never collide.
    const labelEvery = Math.ceil(series.length / 12);

    return (
        <div className="ad-chart">
            <div className="ad-chart__plot">
                {series.map((point, index) => {
                    const size = value(point);
                    const title =
                        metric === "tokens"
                            ? `${point.label}: ${formatTokens(point.totalTokens)} tokens · ${formatUsd(point.costUsd)}`
                            : `${point.label}: ${size} report${size === 1 ? "" : "s"}`;
                    return (
                        <div className="ad-chart__col" key={point.key} title={title}>
                            <div
                                className="ad-chart__bar"
                                style={{ height: `${Math.max(4, Math.round((size / peak) * 84))}px` }}
                            />
                            <span className="ad-chart__label">
                                {index % labelEvery === 0 ? point.label : " "}
                            </span>
                        </div>
                    );
                })}
            </div>
            <div className="ad-chart__unit">by {unit}</div>
        </div>
    );
}
