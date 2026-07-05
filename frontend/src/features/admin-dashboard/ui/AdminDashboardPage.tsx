import { FormEvent, useState } from "react";
import { EmptyState } from "@/shared/ui/EmptyState";
import { ErrorAlert } from "@/shared/ui/ErrorAlert";
import { LoadingBlock } from "@/shared/ui/LoadingBlock";
import { useVersionQuery } from "@/shared/api/useVersionQuery";
import type { AdminEntry, AdminTypeStat, AdminUserStat, ReportSummary } from "@/shared/api/types";
import { useAdminStats } from "../api/useAdminStats";
import { useAllReports } from "../api/useAllReports";
import { useAddAdmin, useAdmins, useRemoveAdmin } from "../api/useAdmins";
import "./admin-dashboard.css";

const dateFmt = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" });
const updatedFmt = new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
});

type Tab = "overview" | "reports" | "admins";

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
    const stats = useAdminStats();

    const updated = stats.data ? updatedFmt.format(new Date(stats.data.updatedAt)) : "…";

    return (
        <div className="ad">
            <div className="ad__card">
                <div className="ad__head">
                    <div>
                        <div className="ad__eyebrow">
                            <span className="ad__chip">Admin</span>
                            <span className="ad__updated">Updated {updated}</span>
                        </div>
                        <h1 className="ad__title">Dashboard</h1>
                    </div>
                </div>

                <div className="ad-tabs" role="tablist">
                    {(["overview", "reports", "admins"] as Tab[]).map((t) => (
                        <button
                            key={t}
                            type="button"
                            role="tab"
                            aria-selected={tab === t}
                            className={`ad-tabs__tab${tab === t ? " ad-tabs__tab--active" : ""}`}
                            onClick={() => setTab(t)}
                        >
                            {t === "overview" ? "Overview" : t === "reports" ? "All reports" : "Admins"}
                        </button>
                    ))}
                </div>

                {tab === "overview" && <OverviewTab query={stats} />}
                {tab === "reports" && <AllReportsTab />}
                {tab === "admins" && <AdminsTab />}
            </div>
        </div>
    );
}

/** The statistics overview (cards + by-user + by-type + weekly + technical). */
function OverviewTab({ query }: { query: ReturnType<typeof useAdminStats> }) {
    const { data, isLoading, isError, error } = query;
    const { data: version } = useVersionQuery();

    if (isLoading) return <LoadingBlock label="Loading statistics…" />;
    if (isError || !data) {
        return <ErrorAlert message={error instanceof Error ? error.message : "Could not load statistics"} />;
    }

    const { totals, byUser, byType, weekly } = data;
    const maxType = Math.max(1, ...byType.map((t: AdminTypeStat) => t.count));
    const maxWeek = Math.max(1, ...weekly.map((d) => d.count));

    const cards = [
        { value: String(totals.reportsTotal), label: "Reports total", delta: `${totals.thisMonth} this month`, hero: true },
        { value: String(totals.thisMonth), label: "This month", delta: "created so far" },
        { value: String(totals.activeUsers), label: "Active users", delta: "with a report" },
        { value: String(totals.failed), label: "Failed jobs", delta: `${totals.running} running now` },
    ];

    return (
        <>
            <div className="ad__stats">
                {cards.map((c) => (
                    <div key={c.label} className={`ad-stat${c.hero ? " ad-stat--hero" : ""}`}>
                        <div className="ad-stat__num">{c.value}</div>
                        <div className="ad-stat__label">{c.label}</div>
                        <div className="ad-stat__delta">{c.delta}</div>
                    </div>
                ))}
            </div>

            <div className="ad__body">
                <div className="ad-users">
                    <div className="ad-users__head">
                        <span className="ad-users__title">By user</span>
                        <span className="ad-users__count">{byUser.length} active</span>
                    </div>
                    <div className="ad-users__grid">
                        <div className="ad-users__th">User</div>
                        <div className="ad-users__th">Total</div>
                        <div className="ad-users__th">This month</div>
                        <div className="ad-users__th">Last</div>
                        {byUser.map((u: AdminUserStat, i: number) => (
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
                                <div className="ad-users__month">{u.thisMonth}</div>
                                <div className="ad-users__last">{lastActivityLabel(u.lastActivity)}</div>
                            </div>
                        ))}
                    </div>
                </div>

                <div className="ad-rail">
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
                        <div className="ad-rail__title">This week</div>
                        <div className="ad-week">
                            {weekly.map((d) => (
                                <div className="ad-week__col" key={d.date}>
                                    <div
                                        className="ad-week__bar"
                                        style={{ height: `${Math.max(6, Math.round((d.count / maxWeek) * 76))}px` }}
                                        title={`${d.count} report${d.count === 1 ? "" : "s"}`}
                                    />
                                    <span className="ad-week__label">{d.label}</span>
                                </div>
                            ))}
                        </div>
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
                                <dt>Reports total</dt>
                                <dd>{totals.reportsTotal}</dd>
                            </div>
                        </dl>
                    </div>
                </div>
            </div>
        </>
    );
}

/** Team-wide report history — every user's reports in one list. */
function AllReportsTab() {
    const { data, isLoading, isError, error } = useAllReports();
    const reports = data?.reports ?? [];

    if (isLoading) return <LoadingBlock label="Loading all reports…" />;
    if (isError) return <ErrorAlert message={error instanceof Error ? error.message : "Could not load reports"} />;
    if (reports.length === 0) return <EmptyState message="No reports created yet." />;

    return (
        <div className="ad-reports">
            <div className="ad-reports__head">
                <span className="ad-reports__title">All reports</span>
                <span className="ad-reports__count">{data?.total ?? reports.length} total</span>
            </div>
            <div className="ad-reports__list">
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
