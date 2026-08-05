import type { AdminActiveUsersPeriod } from "@/shared/api/types";
import { deltaTone, formatDelta } from "../lib/tokenFormat";

/**
 * Active users per month, newest first, with the month-over-month change and how many of each
 * month's users were new.
 *
 * The new-user column is what makes the growth readable: a flat month-over-month total can be the
 * same people coming back or an equal number arriving and leaving, and only the first-seen count
 * tells those apart.
 */
export function ActiveUsersTrend({ periods }: { periods: AdminActiveUsersPeriod[] }) {
    if (periods.length === 0) {
        return <div className="ad-rail__empty">No activity recorded yet.</div>;
    }
    const newestFirst = [...periods].reverse();
    const peak = Math.max(1, ...periods.map((p) => p.activeUsers));

    return (
        <div className="ad-trend">
            <div className="ad-trend__grid ad-trend__grid--users">
                <div className="ad-trend__th">Month</div>
                <div className="ad-trend__th">Active</div>
                <div className="ad-trend__th">New</div>
                <div className="ad-trend__th">MoM</div>
                {newestFirst.map((p) => (
                    <div className="ad-trend__rowcontents" key={p.key}>
                        <div className="ad-trend__period">
                            <span className="ad-trend__label">{p.label}</span>
                            <span className="ad-trend__track">
                                <span
                                    className="ad-trend__fill"
                                    style={{ width: `${Math.round((p.activeUsers / peak) * 100)}%` }}
                                />
                            </span>
                        </div>
                        <div className="ad-trend__num">{p.activeUsers}</div>
                        <div className="ad-trend__num">{p.newUsers > 0 ? `+${p.newUsers}` : "—"}</div>
                        <div className={`ad-trend__delta ad-trend__delta--${deltaTone(p.deltaPct)}`}>
                            {formatDelta(p.deltaPct)}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
