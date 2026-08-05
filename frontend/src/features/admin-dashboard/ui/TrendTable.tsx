import type { AdminTokenPeriod } from "@/shared/api/types";
import { deltaTone, formatDelta, formatTokens, formatUsd } from "../lib/tokenFormat";

/**
 * Token spend per week or per month, newest first, with each period's change against the one
 * before it.
 *
 * Newest first because the question this answers is "what is happening now, and is it more or less
 * than last time" — the current period belongs at the top, not at the end of a scroll. The series
 * arrives oldest-first (charts want it that way) and is reversed here.
 *
 * The delta comes from the server rather than being computed from the neighbouring row: a period in
 * which nothing happened is absent from the series entirely, so subtracting the next row along would
 * quietly compare across the gap.
 */
export function TrendTable({ periods, unitLabel }: { periods: AdminTokenPeriod[]; unitLabel: string }) {
    if (periods.length === 0) {
        return <div className="ad-rail__empty">No {unitLabel} recorded yet.</div>;
    }
    const newestFirst = [...periods].reverse();
    const peak = Math.max(1, ...periods.map((p) => p.totalTokens));

    return (
        <div className="ad-trend">
            <div className="ad-trend__grid">
                <div className="ad-trend__th">Period</div>
                <div className="ad-trend__th">Reports</div>
                <div className="ad-trend__th">Tokens</div>
                <div className="ad-trend__th">Change</div>
                <div className="ad-trend__th">Cost</div>
                {newestFirst.map((p) => (
                    <div className="ad-trend__rowcontents" key={p.key}>
                        <div className="ad-trend__period">
                            <span className="ad-trend__label">{p.label}</span>
                            <span className="ad-trend__track">
                                <span
                                    className="ad-trend__fill"
                                    style={{ width: `${Math.round((p.totalTokens / peak) * 100)}%` }}
                                />
                            </span>
                        </div>
                        <div className="ad-trend__num">{p.reports}</div>
                        <div className="ad-trend__num">{formatTokens(p.totalTokens)}</div>
                        <div className={`ad-trend__delta ad-trend__delta--${deltaTone(p.tokensDeltaPct)}`}>
                            {formatDelta(p.tokensDeltaPct)}
                        </div>
                        <div className="ad-trend__num">{formatUsd(p.costUsd)}</div>
                    </div>
                ))}
            </div>
        </div>
    );
}
