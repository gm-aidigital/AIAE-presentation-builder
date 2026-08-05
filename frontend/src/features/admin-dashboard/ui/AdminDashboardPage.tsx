import { FormEvent, useCallback, useState } from "react";
import { EmptyState } from "@/shared/ui/EmptyState";
import { ErrorAlert } from "@/shared/ui/ErrorAlert";
import { LoadingBlock } from "@/shared/ui/LoadingBlock";
import { useVersionQuery } from "@/shared/api/useVersionQuery";
import type {
    AdminEntry,
    AdminFailedJob,
    AdminTokenLabel,
    AdminTypeStat,
    AdminUserStat,
    ReportSummary,
} from "@/shared/api/types";
import { formatDelta, formatHours, formatTokens, formatUsd } from "../lib/tokenFormat";
import type { DateRange } from "../lib/dateRange";
import { defaultRange, formatRange, suggestedUnit } from "../lib/dateRange";
import { useTableSort } from "../lib/useTableSort";
import { useAdminStats } from "../api/useAdminStats";
import type { ReportSortKey } from "../api/useAllReports";
import { useAllReports } from "../api/useAllReports";
import { useAddAdmin, useAdmins, useRemoveAdmin } from "../api/useAdmins";
import { useClearFailures, useResolveFailure } from "../api/useFailures";
import { ActiveUsersTrend } from "./ActiveUsersTrend";
import { DateRangePicker } from "./DateRangePicker";
import { SavingsPanel } from "./SavingsPanel";
import { SortHeader } from "./SortHeader";
import { TrendTable } from "./TrendTable";
import { VolumeChart } from "./VolumeChart";
import "./admin-dashboard.css";

const dateFmt = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" });
const updatedFmt = new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
});

type Tab = "overview" | "tokens" | "reports" | "failures" | "admins";

const TAB_LABELS: Record<Tab, string> = {
    overview: "Overview",
    tokens: "Token consumption",
    reports: "All reports",
    failures: "Failures",
    admins: "Admins",
};


/** First-letter initials (max 2) from a display name. */
function initials(name: string): string {
    return name.split(/\s+/).filter(Boolean).map((w) => w[0]).join("").slice(0, 2).toUpperCase();
}

/** CSS variable for a report type's accent color; falls back to blue. */
function typeColor(type: string): string {
    switch (type.toUpperCase()) {
        case "EOM":
            return "var(--rc-type-eom)";
        case "EOC":
            return "var(--rc-type-eoc)";
        case "EXCEL":
            return "var(--rc-type-excel)";
        case "AGENDA":
            return "var(--rc-type-agenda)";
        default:
            return "var(--rc-blue)";
    }
}

/** Human-readable status word for a report row. */
function statusLabel(status: string): string {
    switch (status) {
        case "done":
            return "Ready";
        case "running":
        case "queued":
            return "In progress";
        case "error":
            return "Failed";
        default:
            return status;
    }
}

/** Compact "today / yesterday / N days ago / date" label for a timestamp. */
function lastActivityLabel(iso: string | undefined): string {
    if (!iso) return "—";
    const then = new Date(iso);
    const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    const days = Math.round((startOf(new Date()) - startOf(then)) / 86_400_000);
    if (days <= 0) return "today";
    if (days === 1) return "yesterday";
    if (days < 7) return `${days} days ago`;
    return dateFmt.format(then);
}

/** Screen 7 — admin-only team statistics, all-reports history, and admin management. */
export function AdminDashboardPage() {
    const [tab, setTab] = useState<Tab>("overview");
    // One window for the whole screen, held here rather than per tab: switching between Overview and
    // Token consumption to compare the same dates should not mean setting them twice.
    const [range, setRange] = useState<DateRange>(() => defaultRange());
    const stats = useAdminStats(range);

    const updated = stats.data ? updatedFmt.format(new Date(stats.data.updatedAt)) : "…";
    // Everything except the live job counters and the failures list comes from the usage rollup, so
    // that is the timestamp worth showing when the two differ.
    const rollupUpdated = stats.data?.rollupUpdatedAt
        ? updatedFmt.format(new Date(stats.data.rollupUpdatedAt))
        : null;

    return (
        <div className="ad">
            <div className="ad__card">
                <div className="ad__head">
                    <div>
                        <div className="ad__eyebrow">
                            <span className="ad__chip">Admin</span>
                            <span
                                className="ad__updated"
                                title={rollupUpdated ? `Figures computed from usage rolled up at ${rollupUpdated}` : undefined}
                            >
                                Updated {rollupUpdated ?? updated}
                            </span>
                        </div>
                        <h1 className="ad__title">Dashboard</h1>
                    </div>
                    {(tab === "overview" || tab === "tokens") && (
                        <DateRangePicker value={range} onChange={setRange} />
                    )}
                </div>

                <div className="ad-tabs" role="tablist">
                    {(Object.keys(TAB_LABELS) as Tab[]).map((t) => (
                        <button
                            key={t}
                            type="button"
                            role="tab"
                            aria-selected={tab === t}
                            className={`ad-tabs__tab${tab === t ? " ad-tabs__tab--active" : ""}`}
                            onClick={() => setTab(t)}
                        >
                            {TAB_LABELS[t]}
                        </button>
                    ))}
                </div>

                {tab === "overview" && (
                    <OverviewTab query={stats} range={range} onShowFailures={() => setTab("failures")} />
                )}
                {tab === "tokens" && <TokensTab query={stats} range={range} />}
                {tab === "reports" && <AllReportsTab />}
                {tab === "failures" && <FailuresTab query={stats} />}
                {tab === "admins" && <AdminsTab />}
            </div>
        </div>
    );
}

/** The statistics overview for the selected window (cards + savings + by-user + by-type + chart). */
function OverviewTab({
    query,
    range,
    onShowFailures,
}: {
    query: ReturnType<typeof useAdminStats>;
    range: DateRange;
    onShowFailures: () => void;
}) {
    const { data, isLoading, isError, error } = query;
    const { data: version } = useVersionQuery();

    // One row per person, so this table is sorted in the browser. The paged report history is not —
    // see the note on useTableSort.
    const userValue = useCallback(
        (u: AdminUserStat, key: string) => {
            switch (key) {
                case "total":
                    return u.total;
                case "slides":
                    return u.slides;
                case "last":
                    return u.lastActivity;
                default:
                    return u.email ?? u.name;
            }
        },
        [],
    );
    const { sort, sorted: sortedUsers, toggle } = useTableSort<AdminUserStat, string>(
        data?.byUser ?? [],
        userValue,
        { key: "total", dir: "desc" },
    );

    if (isLoading) return <LoadingBlock label="Loading statistics…" />;
    if (isError || !data) {
        return <ErrorAlert message={error instanceof Error ? error.message : "Could not load statistics"} />;
    }

    const { totals, byType, savings, activeUsersMonths, series, seriesUnit } = data;
    const maxType = Math.max(1, ...byType.map((t: AdminTypeStat) => t.count));

    return (
        <>
            <div className="ad__stats">
                <div className="ad-stat ad-stat--hero">
                    <div className="ad-stat__num">{totals.reports}</div>
                    <div className="ad-stat__label">Reports</div>
                    <div className="ad-stat__delta">
                        {savings.slidesTotal.toLocaleString("en-US")} slides · {formatRange(range)}
                    </div>
                </div>
                <div className="ad-stat">
                    <div className="ad-stat__num">{totals.activeUsers}</div>
                    <div className="ad-stat__label">Active users</div>
                    <div className="ad-stat__delta">
                        {totals.newUsers > 0 ? `${totals.newUsers} new in this period` : "no first-timers"}
                    </div>
                </div>
                <div className="ad-stat">
                    <div className="ad-stat__num">{formatHours(savings.savedHours)}</div>
                    <div className="ad-stat__label">Hours saved</div>
                    <div className="ad-stat__delta">{formatUsd(savings.savedUsd)} of time</div>
                </div>
                <button
                    type="button"
                    className={`ad-stat ad-stat--action${totals.failed > 0 ? " ad-stat--alert" : ""}`}
                    onClick={onShowFailures}
                >
                    <div className="ad-stat__num">{totals.failed}</div>
                    <div className="ad-stat__label">Failed jobs</div>
                    <div className="ad-stat__delta">
                        {totals.failed > 0 ? "See what broke →" : `${totals.running} running now`}
                    </div>
                </button>
            </div>

            <SavingsPanel savings={savings} />

            <div className="ad__body">
                <div className="ad-users">
                    <div className="ad-users__head">
                        <span className="ad-users__title">By user</span>
                        <span className="ad-users__count">{byUser.length} active</span>
                    </div>
                    <div className="ad-users__grid">
                        <SortHeader label="User" columnKey="user" activeKey={sort.key} dir={sort.dir} onSort={toggle} />
                        <SortHeader label="Total" columnKey="total" activeKey={sort.key} dir={sort.dir} onSort={toggle} />
                        <SortHeader label="Slides" columnKey="slides" activeKey={sort.key} dir={sort.dir} onSort={toggle} />
                        <SortHeader label="Last" columnKey="last" activeKey={sort.key} dir={sort.dir} onSort={toggle} />
                        {sortedUsers.map((u: AdminUserStat, i: number) => (
                            <div className="ad-users__rowcontents" key={u.userId ?? u.email ?? i}>
                                <div className="ad-users__user">
                                    <span className="ad-users__avatar" style={{ background: `var(--rc-avatar-${(i % 6) + 1})` }}>
                                        {initials(u.name)}
                                    </span>
                                    <div className="ad-users__id">
                                        <div className="ad-users__email">{u.email ?? "—"}</div>
                                        <div className="ad-users__name">{u.name}</div>
                                    </div>
                                </div>
                                <div className="ad-users__total">{u.total}</div>
                                <div className="ad-users__month">{u.slides.toLocaleString("en-US")}</div>
                                <div className="ad-users__last">{lastActivityLabel(u.lastActivity)}</div>
                            </div>
                        ))}
                    </div>
                </div>

                <div className="ad-rail">
                    <div className="ad-rail__card">
                        <div className="ad-rail__title">Active users by month</div>
                        <ActiveUsersTrend periods={activeUsersMonths} />
                    </div>

                    <div className="ad-rail__card">
                        <div className="ad-rail__title">By report type</div>
                        <div className="ad-types">
                            {byType.length === 0 && <div className="ad-rail__empty">No reports yet.</div>}
                            {byType.map((t: AdminTypeStat) => (
                                <div key={t.type}>
                                    <div className="ad-types__row">
                                        <span className="ad-types__name">
                                            <span className="ad-types__dot" style={{ background: typeColor(t.type) }} />
                                            {t.type}
                                        </span>
                                        <span className="ad-types__count">{t.count}</span>
                                    </div>
                                    <div className="ad-types__track">
                                        <div
                                            className="ad-types__fill"
                                            style={{ width: `${Math.round((t.count / maxType) * 100)}%`, background: typeColor(t.type) }}
                                        />
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="ad-rail__card ad-rail__card--tint">
                        <div className="ad-rail__title">Reports over the period</div>
                        <VolumeChart series={series} unit={seriesUnit} />
                    </div>

                    <div className="ad-rail__card">
                        <div className="ad-rail__title">Technical</div>
                        <dl className="ad-tech">
                            <div className="ad-tech__row">
                                <dt>Backend build</dt>
                                <dd>{version?.commitId ?? "unknown"}</dd>
                            </div>
                            <div className="ad-tech__row">
                                <dt>Running jobs</dt>
                                <dd>{totals.running}</dd>
                            </div>
                            <div className="ad-tech__row">
                                <dt>Failed jobs</dt>
                                <dd>{totals.failed}</dd>
                            </div>
                            <div className="ad-tech__row">
                                <dt>Reports in period</dt>
                                <dd>{totals.reports}</dd>
                            </div>
                        </dl>
                    </div>
                </div>
            </div>
        </>
    );
}

/** Claude token consumption — totals, averages, cost, the week's trend, and who spent what. */
function TokensTab({ query, range }: { query: ReturnType<typeof useAdminStats>; range: DateRange }) {
    const { data, isLoading, isError, error } = query;
    // The granularity follows the window unless the reader overrides it: thirteen weekly bars for a
    // quarter is not a trend anyone reads, and two monthly stubs for a fortnight is not one either.
    const [unitOverride, setUnitOverride] = useState<"week" | "month" | null>(null);
    const trendUnit = unitOverride ?? suggestedUnit(range);

    const spendValue = useCallback((u: AdminUserStat, key: string) => {
        switch (key) {
            case "input":
                return u.inputTokens + u.cacheTokens;
            case "output":
                return u.outputTokens;
            case "total":
                return u.totalTokens;
            case "cost":
                return u.costUsd;
            case "reports":
                return u.total;
            default:
                return u.email ?? u.name;
        }
    }, []);
    const spenders = (data?.byUser ?? []).filter((u) => u.totalTokens > 0);
    const { sort, sorted: byUser, toggle } = useTableSort<AdminUserStat, string>(spenders, spendValue, {
        key: "total",
        dir: "desc",
    });

    if (isLoading) return <LoadingBlock label="Loading token consumption…" />;
    if (isError || !data) {
        return <ErrorAlert message={error instanceof Error ? error.message : "Could not load statistics"} />;
    }

    const t = data.tokens;
    const trendPeriods = trendUnit === "week" ? data.tokensByWeek : data.tokensByMonth;
    const latestTrend = trendPeriods.at(-1);

    if (t.reportsWithUsage === 0 && t.claudeCalls === 0 && t.unknownCalls === 0) {
        return (
            <EmptyState message="No token usage recorded yet. Figures appear here once a report runs with Claude enabled." />
        );
    }

    // Input-side classes are charted apart from output because they are billed at different rates —
    // the same reason the backend stores them separately.
    const mix = [
        { label: "Input", value: t.inputTokens, color: "var(--rc-blue)" },
        { label: "Output", value: t.outputTokens, color: "var(--rc-orange)" },
        { label: "Cache write", value: t.cacheWriteTokens, color: "var(--rc-type-eom)" },
        { label: "Cache read", value: t.cacheReadTokens, color: "var(--rc-type-excel)" },
    ];
    const mixTotal = Math.max(1, t.totalTokens);
    const maxStage = Math.max(1, ...data.byLabel.map((l) => l.totalTokens));

    return (
        <>
            <div className="ad__stats">
                <div className="ad-stat ad-stat--hero">
                    <div className="ad-stat__num">{formatTokens(t.totalTokens)}</div>
                    <div className="ad-stat__label">Tokens measured</div>
                    <div className="ad-stat__delta">
                        across {t.reportsWithUsage} report{t.reportsWithUsage === 1 ? "" : "s"}
                        {t.unknownCalls > 0 && ` · +~${formatTokens(t.estimatedTokens)} unmeasured`}
                    </div>
                </div>
                <div className="ad-stat">
                    <div className="ad-stat__num">{formatUsd(t.costUsd)}</div>
                    <div className="ad-stat__label">Estimated cost</div>
                    <div className="ad-stat__delta">
                        {formatRange(range)}
                        {t.unknownCalls > 0 && ` · +~${formatUsd(t.estimatedCostUsd)} unmeasured`}
                    </div>
                </div>
                <div className="ad-stat">
                    <div className="ad-stat__num">{formatTokens(t.avgTokensPerReport)}</div>
                    <div className="ad-stat__label">Avg per report</div>
                    <div className="ad-stat__delta">
                        {formatTokens(t.avgInputPerReport)} in / {formatTokens(t.avgOutputPerReport)} out
                    </div>
                </div>
                <div className="ad-stat">
                    <div className="ad-stat__num">{formatUsd(t.avgCostPerReportUsd)}</div>
                    <div className="ad-stat__label">Avg cost per report</div>
                    <div className="ad-stat__delta">{t.claudeCalls} Claude calls total</div>
                </div>
            </div>

            {(t.unknownCalls > 0 || t.unattributedCalls > 0) && (
                <div className="ad-note">
                    {t.unknownCalls > 0 && (
                        <p className="ad-note__line">
                            <strong>
                                {t.unknownCalls} call{t.unknownCalls === 1 ? "" : "s"} billed but not measured
                            </strong>{" "}
                            — the reply was lost to a timeout, so the real cost can never be known. Their prompts
                            were measured before sending and their replies predicted from what the same batch
                            normally returns: <strong>~{formatTokens(t.estimatedTokens)}</strong> tokens,{" "}
                            <strong>~{formatUsd(t.estimatedCostUsd)}</strong>. This is a floor, and it is kept out
                            of the figures above.
                        </p>
                    )}
                    {t.unattributedCalls > 0 && (
                        <p className="ad-note__line">
                            <strong>{formatTokens(t.unattributedTokens)}</strong> tokens (
                            {formatUsd(t.unattributedCostUsd)}) came from {t.unattributedCalls} call
                            {t.unattributedCalls === 1 ? "" : "s"} made outside any report — line-item matching runs
                            during the wizard. Counted in the team total, excluded from the per-report averages.
                        </p>
                    )}
                </div>
            )}

            <div className="ad__body">
                <div className="ad-users">
                    <div className="ad-users__head">
                        <span className="ad-users__title">Spend by user</span>
                        <span className="ad-users__count">{byUser.length} with usage</span>
                    </div>
                    <div className="ad-users__grid ad-users__grid--tokens">
                        <SortHeader label="User" columnKey="user" activeKey={sort.key} dir={sort.dir} onSort={toggle} />
                        <SortHeader label="Input" columnKey="input" activeKey={sort.key} dir={sort.dir} onSort={toggle} />
                        <SortHeader label="Output" columnKey="output" activeKey={sort.key} dir={sort.dir} onSort={toggle} />
                        <SortHeader label="Total" columnKey="total" activeKey={sort.key} dir={sort.dir} onSort={toggle} />
                        <SortHeader label="Cost" columnKey="cost" activeKey={sort.key} dir={sort.dir} onSort={toggle} />
                        {byUser.map((u: AdminUserStat, i: number) => (
                            <div className="ad-users__rowcontents" key={u.userId ?? u.email ?? i}>
                                <div className="ad-users__user">
                                    <span
                                        className="ad-users__avatar"
                                        style={{ background: `var(--rc-avatar-${(i % 6) + 1})` }}
                                    >
                                        {initials(u.name)}
                                    </span>
                                    <div className="ad-users__id">
                                        <div className="ad-users__email">{u.email ?? "—"}</div>
                                        <div className="ad-users__name">
                                            {u.total} report{u.total === 1 ? "" : "s"}
                                        </div>
                                    </div>
                                </div>
                                <div className="ad-users__month">{formatTokens(u.inputTokens + u.cacheTokens)}</div>
                                <div className="ad-users__month">{formatTokens(u.outputTokens)}</div>
                                <div className="ad-users__total ad-users__total--sm">{formatTokens(u.totalTokens)}</div>
                                <div className="ad-users__month">{formatUsd(u.costUsd)}</div>
                            </div>
                        ))}
                    </div>
                </div>

                <div className="ad-rail">
                    <div className="ad-rail__card">
                        <div className="ad-rail__head">
                            <span className="ad-rail__title">
                                Trend {trendUnit === "week" ? "week over week" : "month over month"}
                            </span>
                            <div className="ad-toggle" role="group" aria-label="Trend granularity">
                                {(["week", "month"] as const).map((unit) => (
                                    <button
                                        key={unit}
                                        type="button"
                                        className={`ad-toggle__btn${trendUnit === unit ? " ad-toggle__btn--active" : ""}`}
                                        aria-pressed={trendUnit === unit}
                                        onClick={() => setUnitOverride(unit)}
                                    >
                                        {unit === "week" ? "Weekly" : "Monthly"}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <TrendTable periods={trendPeriods} unitLabel={trendUnit === "week" ? "weeks" : "months"} />
                        {latestTrend && (
                            <p className="ad-rail__note">
                                Latest {trendUnit}: {formatTokens(latestTrend.totalTokens)} tokens,{" "}
                                {formatDelta(latestTrend.tokensDeltaPct)} against the {trendUnit} before.
                            </p>
                        )}
                    </div>

                    <div className="ad-rail__card">
                        <div className="ad-rail__title">By pipeline stage</div>
                        <div className="ad-stages">
                            {data.byLabel.length === 0 && <div className="ad-rail__empty">Nothing recorded yet.</div>}
                            {data.byLabel.map((s: AdminTokenLabel) => (
                                <div className="ad-stages__row" key={s.label}>
                                    <span className="ad-stages__name">{s.label}</span>
                                    <span className="ad-stages__bar">
                                        <span
                                            className="ad-stages__fill"
                                            style={{ width: `${Math.round((s.totalTokens / maxStage) * 100)}%` }}
                                        />
                                    </span>
                                    <span className="ad-stages__val">
                                        {formatTokens(s.totalTokens)}
                                        {s.unknownCalls > 0 && (
                                            <span
                                                className="ad-stages__lost"
                                                title={`${s.unknownCalls} call(s) lost to a timeout`}
                                            >
                                                +{s.unknownCalls}?
                                            </span>
                                        )}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="ad-rail__card">
                        <div className="ad-rail__title">Token mix</div>
                        <div className="ad-types">
                            {mix.map((m) => (
                                <div key={m.label}>
                                    <div className="ad-types__row">
                                        <span className="ad-types__name">
                                            <span className="ad-types__dot" style={{ background: m.color }} />
                                            {m.label}
                                        </span>
                                        <span className="ad-types__count">{formatTokens(m.value)}</span>
                                    </div>
                                    <div className="ad-types__track">
                                        <div
                                            className="ad-types__fill"
                                            style={{
                                                width: `${Math.round((m.value / mixTotal) * 100)}%`,
                                                background: m.color,
                                            }}
                                        />
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="ad-rail__card ad-rail__card--tint">
                        <div className="ad-rail__title">Tokens over the period</div>
                        <VolumeChart series={data.series} unit={data.seriesUnit} metric="tokens" />
                    </div>

                    <div className="ad-rail__card">
                        <div className="ad-rail__title">Breakdown</div>
                        <dl className="ad-tech">
                            <div className="ad-tech__row">
                                <dt>Input tokens</dt>
                                <dd>{t.inputTokens.toLocaleString("en-US")}</dd>
                            </div>
                            <div className="ad-tech__row">
                                <dt>Output tokens</dt>
                                <dd>{t.outputTokens.toLocaleString("en-US")}</dd>
                            </div>
                            <div className="ad-tech__row">
                                <dt>Cache write</dt>
                                <dd>{t.cacheWriteTokens.toLocaleString("en-US")}</dd>
                            </div>
                            <div className="ad-tech__row">
                                <dt>Cache read</dt>
                                <dd>{t.cacheReadTokens.toLocaleString("en-US")}</dd>
                            </div>
                            <div className="ad-tech__row">
                                <dt>Measured calls</dt>
                                <dd>{t.claudeCalls.toLocaleString("en-US")}</dd>
                            </div>
                            <div className="ad-tech__row">
                                <dt>Unmeasured calls</dt>
                                <dd>{t.unknownCalls.toLocaleString("en-US")}</dd>
                            </div>
                        </dl>
                        <p className="ad-rail__note">
                            Costs are estimates at the server's configured list prices and exclude any negotiated
                            discount.
                        </p>
                    </div>
                </div>
            </div>
        </>
    );
}

/** Failed jobs — what broke, on which pipeline step, and what it had already cost. */
function FailuresTab({ query }: { query: ReturnType<typeof useAdminStats> }) {
    const { data, isLoading, isError, error } = query;
    const resolveFailure = useResolveFailure();
    const clearFailures = useClearFailures();
    const busy = resolveFailure.isPending || clearFailures.isPending;

    if (isLoading) return <LoadingBlock label="Loading failures…" />;
    if (isError || !data) {
        return <ErrorAlert message={error instanceof Error ? error.message : "Could not load statistics"} />;
    }

    const failures = data.failures;
    if (failures.length === 0) {
        return <EmptyState message="No failures or warnings. Every report finished clean." />;
    }

    const onClearAll = () => {
        if (window.confirm(`Clear all ${failures.length} failures and warnings? This cannot be undone.`)) {
            clearFailures.mutate();
        }
    };

    return (
        <div className="ad-fails">
            <div className="ad-reports__head">
                <span className="ad-reports__title">Failures &amp; warnings</span>
                <span className="ad-reports__count">{failures.length} shown</span>
                <button type="button" className="ad-fails__clear" onClick={onClearAll} disabled={busy}>
                    Clear all
                </button>
            </div>
            <div className="ad-fails__list">
                {failures.map((f: AdminFailedJob) => {
                    const isWarning = f.severity === "warning";
                    return (
                        <div key={f.jobId} className="ad-fails__row">
                            <div className="ad-fails__head">
                                <span className="ad-reports__badge" style={{ background: typeColor(f.type ?? "") }}>
                                    {(f.type ?? "REP").toUpperCase()}
                                </span>
                                <div className="ad-fails__meta">
                                    {f.slideUrl ? (
                                        <a
                                            className="ad-reports__name ad-reports__name--link"
                                            href={f.slideUrl}
                                            target="_blank"
                                            rel="noreferrer"
                                            title="Open the presentation"
                                        >
                                            {f.title} ↗
                                        </a>
                                    ) : (
                                        <div className="ad-reports__name">{f.title}</div>
                                    )}
                                    <div className="ad-reports__sub">
                                        {f.ownerEmail ?? "—"} · job #{f.jobId} ·{" "}
                                        {f.failedAt ? updatedFmt.format(new Date(f.failedAt)) : ""}
                                    </div>
                                </div>
                                {isWarning ? (
                                    <span className="ad-fails__sev ad-fails__sev--warning">Warning</span>
                                ) : (
                                    <span className="ad-fails__step">
                                        Step {f.step}/{f.total}
                                        {f.stepLabel ? ` · ${f.stepLabel}` : ""}
                                    </span>
                                )}
                                <button
                                    type="button"
                                    className="ad-fails__dismiss"
                                    onClick={() => resolveFailure.mutate(f.jobId)}
                                    disabled={busy}
                                    aria-label="Clear this issue"
                                    title="Clear this issue"
                                >
                                    ✕
                                </button>
                            </div>
                            {isWarning ? (
                                f.warnings.length > 0 ? (
                                    <details className="ad-fails__details">
                                        <summary className="ad-fails__summary">
                                            <span className="ad-fails__summary-text">{f.errorMessage}</span>
                                            <span className="ad-fails__toggle">
                                                Show details
                                                <span className="ad-fails__chevron" aria-hidden="true">
                                                    ⌄
                                                </span>
                                            </span>
                                        </summary>
                                        <ul className="ad-fails__warnings">
                                            {f.warnings.map((w, i) => (
                                                <li key={i} className="ad-fails__warning">
                                                    {w}
                                                </li>
                                            ))}
                                        </ul>
                                    </details>
                                ) : (
                                    <div className="ad-fails__summary">{f.errorMessage}</div>
                                )
                            ) : (
                                <div className="ad-fails__error">{f.errorMessage}</div>
                            )}
                            {f.totalTokens > 0 && (
                                <div className="ad-fails__spend">
                                    {isWarning
                                        ? `Cost ${formatTokens(f.totalTokens)} tokens (${formatUsd(f.costUsd)}).`
                                        : `Burned ${formatTokens(f.totalTokens)} tokens (${formatUsd(
                                              f.costUsd,
                                          )}) before failing.`}
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

/** Columns the report history offers, in the order they appear above the list. */
const REPORT_SORTS: { key: ReportSortKey; label: string }[] = [
    { key: "createdAt", label: "Date" },
    { key: "tokens", label: "Tokens" },
    { key: "slides", label: "Slides" },
    { key: "owner", label: "Owner" },
    { key: "type", label: "Type" },
    { key: "status", label: "Status" },
];

/** How many rows one page of the history holds. */
const REPORTS_PAGE_SIZE = 50;

/** Team-wide report history — every user's reports, one page at a time. */
function AllReportsTab() {
    const [page, setPage] = useState(0);
    const [sort, setSort] = useState<ReportSortKey>("createdAt");
    const [dir, setDir] = useState<"asc" | "desc">("desc");

    const { data, isLoading, isError, error, isFetching } = useAllReports({
        page,
        size: REPORTS_PAGE_SIZE,
        sort,
        dir,
    });
    const reports = data?.reports ?? [];
    const total = data?.total ?? 0;

    /** A new column starts at its big end; clicking the active column flips it. Either way, back to page 1. */
    function sortBy(key: ReportSortKey) {
        setDir(key === sort && dir === "desc" ? "asc" : "desc");
        setSort(key);
        setPage(0);
    }

    if (isLoading) return <LoadingBlock label="Loading all reports…" />;
    if (isError) return <ErrorAlert message={error instanceof Error ? error.message : "Could not load reports"} />;
    if (total === 0) return <EmptyState message="No reports created yet." />;

    const first = page * REPORTS_PAGE_SIZE + 1;
    const last = page * REPORTS_PAGE_SIZE + reports.length;

    return (
        <div className="ad-reports">
            <div className="ad-reports__head">
                <span className="ad-reports__title">All reports</span>
                <span className="ad-reports__count">
                    {first}–{last} of {total.toLocaleString("en-US")}
                </span>
            </div>
            <div className="ad-reports__sorts" role="group" aria-label="Sort reports by">
                <span className="ad-reports__sortlabel">Sort by</span>
                {REPORT_SORTS.map((column) => (
                    <SortHeader
                        key={column.key}
                        label={column.label}
                        columnKey={column.key}
                        activeKey={sort}
                        dir={dir}
                        onSort={(key) => sortBy(key as ReportSortKey)}
                    />
                ))}
            </div>
            <div className={`ad-reports__list${isFetching ? " ad-reports__list--busy" : ""}`}>
                {reports.map((r: ReportSummary) => (
                    <div key={r.jobId} className="ad-reports__row">
                        <span className="ad-reports__badge" style={{ background: typeColor(r.type ?? "") }}>
                            {(r.type ?? "REP").toUpperCase()}
                        </span>
                        <div className="ad-reports__meta">
                            <div className="ad-reports__name">{r.fileName ?? r.title}</div>
                            <div className="ad-reports__sub">
                                {r.ownerEmail ?? "—"} · {r.createdAt ? dateFmt.format(new Date(r.createdAt)) : ""} ·{" "}
                                {statusLabel(r.status)}
                            </div>
                            {r.totalTokens > 0 && (
                                <div className="ad-reports__tokens">
                                    <span className="ad-reports__token">
                                        <span className="ad-reports__tokenlabel">in</span> {formatTokens(r.inputTokens)}
                                    </span>
                                    <span className="ad-reports__token">
                                        <span className="ad-reports__tokenlabel">out</span> {formatTokens(r.outputTokens)}
                                    </span>
                                    <span className="ad-reports__token ad-reports__token--cost">
                                        {formatUsd(r.costUsd)}
                                    </span>
                                </div>
                            )}
                            {(r.mediaPlanUrl || r.elevateUrl) && (
                                <div className="ad-reports__sources">
                                    <span className="ad-reports__srclabel">Sources:</span>
                                    {r.mediaPlanUrl && (
                                        <a className="ad-reports__srclink" href={r.mediaPlanUrl} target="_blank" rel="noreferrer">
                                            Media plan ↗
                                        </a>
                                    )}
                                    {r.elevateUrl && (
                                        <a className="ad-reports__srclink" href={r.elevateUrl} target="_blank" rel="noreferrer">
                                            Elevate ↗
                                        </a>
                                    )}
                                </div>
                            )}
                        </div>
                        {r.sheetUrl && (
                            <a className="ad-reports__sheet" href={r.sheetUrl} target="_blank" rel="noreferrer">
                                View sheet ↗
                            </a>
                        )}
                        <button
                            type="button"
                            className="ad-reports__open"
                            disabled={!r.slideUrl}
                            onClick={() => r.slideUrl && window.open(r.slideUrl, "_blank", "noopener")}
                        >
                            Open →
                        </button>
                    </div>
                ))}
            </div>
            <div className="ad-reports__pager">
                <button
                    type="button"
                    className="ad-reports__page"
                    disabled={page === 0 || isFetching}
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                    ← Newer
                </button>
                <span className="ad-reports__pagenum">Page {page + 1}</span>
                <button
                    type="button"
                    className="ad-reports__page"
                    disabled={!data?.hasMore || isFetching}
                    onClick={() => setPage((current) => current + 1)}
                >
                    Older →
                </button>
            </div>
        </div>
    );
}

/** Manage who is an admin — add by email and revoke UI-managed grants. */
function AdminsTab() {
    const { data, isLoading, isError, error } = useAdmins();
    const addAdmin = useAddAdmin();
    const removeAdmin = useRemoveAdmin();
    const [email, setEmail] = useState("");

    const admins = data?.admins ?? [];
    const addError = addAdmin.error instanceof Error ? addAdmin.error.message : null;

    function submit(e: FormEvent) {
        e.preventDefault();
        const value = email.trim();
        if (!value) return;
        addAdmin.mutate(value, { onSuccess: () => setEmail("") });
    }

    return (
        <div className="ad-admins">
            <form className="ad-admins__add" onSubmit={submit}>
                <input
                    type="email"
                    className="ad-admins__input"
                    placeholder="name@aidigital.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                />
                <button type="submit" className="ad-admins__addbtn" disabled={addAdmin.isPending || !email.trim()}>
                    {addAdmin.isPending ? "Adding…" : "Add admin"}
                </button>
            </form>
            {addError && <ErrorAlert message={addError} />}

            {isLoading && <LoadingBlock label="Loading admins…" />}
            {isError && <ErrorAlert message={error instanceof Error ? error.message : "Could not load admins"} />}

            {!isLoading && !isError && (
                <div className="ad-admins__list">
                    {admins.map((a: AdminEntry) => (
                        <div key={a.email} className="ad-admins__row">
                            <div className="ad-admins__id">
                                <span className="ad-admins__email">{a.email}</span>
                                <span className={`ad-admins__tag ad-admins__tag--${a.source}`}>
                                    {a.source === "config" ? "Root" : "Added"}
                                </span>
                            </div>
                            {a.removable ? (
                                <button
                                    type="button"
                                    className="ad-admins__remove"
                                    disabled={removeAdmin.isPending}
                                    onClick={() => removeAdmin.mutate(a.email)}
                                >
                                    Remove
                                </button>
                            ) : (
                                <span className="ad-admins__locked">Configured</span>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
