import { useState } from "react";
import { isGoogleSheetUrl } from "@/shared/api/sheets";
import { useWizard } from "@/shared/wizard/WizardContext";
import { pacingReadyCount } from "../lib/pacing";
import { IconArrowLeft, IconArrowRight, IconCheck, IconSpinner } from "./icons";

export interface InputErrors {
    brief: boolean;
    marketVolume: boolean;
    sheet: boolean;
    adj: boolean;
    dates: boolean;
}

interface Props {
    errors: InputErrors;
    mediaPulling: boolean;
    elevatePulling: boolean;
    datesDetecting: boolean;
    matchRunning: boolean;
    onConnectMediaPlan(url: string): void;
    onConnectElevate(url: string): void;
    // Present when auto-detection found no "Proposal"/"Estimates" tab: the workbook's
    // visible tabs, so the user can point us at the tab that holds the media plan.
    mediaTabPicker: { url: string; tabs: string[] } | null;
    onPickMediaTab(url: string, tab: string): void;
    onDismissMediaTabPicker(): void;
    onDisconnectMediaPlan(): void;
    onDisconnectElevate(): void;
    onOpenMatch(): void;
    onOpenPacing(): void;
    onConfirm(): void;
    onBack(): void;
    clearError(key: keyof InputErrors): void;
    /** True while a user-supplied workbook is being read and registered. */
    adopting: boolean;
    onAdoptSheet(url: string): void;
}

interface ConnectRowProps {
    label: string;
    placeholder: string;
    connected: string | null;
    pulling: boolean;
    onConnect(url: string): void;
    onDisconnect(): void;
}

function ConnectRow({ label, placeholder, connected, pulling, onConnect, onDisconnect }: ConnectRowProps) {
    const [url, setUrl] = useState("");
    const canConnect = isGoogleSheetUrl(url) && !pulling;
    return (
        <div className="rc-field">
            <label className="rc-field__label">{label}</label>
            <div className="rc-connect">
                <input
                    type="text"
                    className="rc-input"
                    placeholder={placeholder}
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                />
                <button
                    type="button"
                    className="rc-btn rc-btn--ghost"
                    disabled={!canConnect}
                    onClick={() => onConnect(url)}
                >
                    {pulling ? <IconSpinner size={14} /> : null}
                    {pulling ? "Reading…" : "Connect"}
                </button>
            </div>
            {connected && (
                <div className="rc-connect__status">
                    <span className="rc-connect__check">
                        <IconCheck size={11} />
                    </span>
                    <span className="rc-connect__name">{connected}</span>
                    <button
                        type="button"
                        className="rc-connect__disconnect"
                        onClick={() => {
                            setUrl("");
                            onDisconnect();
                        }}
                    >
                        Disconnect
                    </button>
                </div>
            )}
        </div>
    );
}

interface MediaTabPickerProps {
    picker: { url: string; tabs: string[] };
    pulling: boolean;
    onPick(url: string, tab: string): void;
    onDismiss(): void;
}

/** Fallback tab chooser shown when no "Proposal"/"Estimates" tab was auto-detected. */
function MediaTabPicker({ picker, pulling, onPick, onDismiss }: MediaTabPickerProps) {
    const [tab, setTab] = useState(picker.tabs[0] ?? "");
    return (
        <div className="rc-tabpick">
            <div className="rc-tabpick__text">
                We couldn't find a standard media-plan tab. Pick the tab that holds the media plan — we'll check it has
                the <strong>Media</strong> and budget columns before loading it.
            </div>
            <div className="rc-tabpick__row">
                <select
                    className="rc-input rc-tabpick__select"
                    value={tab}
                    onChange={(e) => setTab(e.target.value)}
                >
                    {picker.tabs.map((t) => (
                        <option key={t} value={t}>
                            {t}
                        </option>
                    ))}
                </select>
                <button
                    type="button"
                    className="rc-btn rc-btn--ghost rc-btn--sm"
                    disabled={pulling || !tab}
                    onClick={() => onPick(picker.url, tab)}
                >
                    {pulling ? <IconSpinner size={14} /> : null}
                    {pulling ? "Checking…" : "Use this tab"}
                </button>
                <button type="button" className="rc-tabpick__dismiss" onClick={onDismiss}>
                    Cancel
                </button>
            </div>
        </div>
    );
}

interface AdoptSheetCardProps {
    adopting: boolean;
    onAdopt(url: string): void;
}

/**
 * The way out of this whole screen for a user who already has a filled report workbook. Deliberately
 * sits above the normal inputs and looks like a different kind of thing: none of what this step
 * collects survives into the deck — every number, name and date is read back out of the workbook —
 * so for them the link is the only input that matters.
 */
function AdoptSheetCard({ adopting, onAdopt }: AdoptSheetCardProps) {
    const [url, setUrl] = useState("");
    const [open, setOpen] = useState(false);
    const canAdopt = isGoogleSheetUrl(url) && !adopting;

    if (!open) {
        return (
            <div className="rc-adopt rc-adopt--collapsed">
                <div className="rc-adopt__text">
                    <div className="rc-adopt__title">Already have a filled report sheet?</div>
                    <div className="rc-adopt__sub">
                        Skip straight to generating the deck from it — no media plan or matching needed.
                    </div>
                </div>
                <button type="button" className="rc-btn rc-btn--ghost rc-btn--sm" onClick={() => setOpen(true)}>
                    Use my sheet
                </button>
            </div>
        );
    }

    return (
        <div className="rc-adopt">
            <div className="rc-adopt__text">
                <div className="rc-adopt__title">Use a sheet you filled in yourself</div>
                <div className="rc-adopt__sub">
                    Paste the link and we'll read the campaign, the tactics and the breakdowns straight off it.
                    Make sure it's shared with you and follows the report template.
                </div>
            </div>
            <div className="rc-connect">
                <input
                    type="text"
                    className="rc-input"
                    placeholder="https://docs.google.com/spreadsheets/…"
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                />
                <button
                    type="button"
                    className="rc-btn rc-btn--primary"
                    disabled={!canAdopt}
                    onClick={() => onAdopt(url)}
                >
                    {adopting ? <IconSpinner size={14} /> : null}
                    {adopting ? "Reading…" : "Use this sheet"}
                </button>
            </div>
            <button type="button" className="rc-adopt__cancel" onClick={() => setOpen(false)}>
                Cancel — I'll fill in the form
            </button>
        </div>
    );
}

interface StatusRowProps {
    label: string;
    done: boolean;
    value: string;
}

function StatusRow({ label, done, value }: StatusRowProps) {
    return (
        <div className="rc-status__row">
            <span className={`rc-status__key${done ? " rc-status__key--done" : ""}`}>
                <span className={`rc-status__badge${done ? " rc-status__badge--done" : ""}`}>
                    {done && <IconCheck size={11} />}
                </span>
                {label}
            </span>
            <span className={`rc-status__val${done ? " rc-status__val--done" : ""}`}>{value}</span>
        </div>
    );
}

/** Screen 2 — brief, market volume, source sheets, flight dates + mapping confirm. */
export function StepDataInputs({
    errors,
    mediaPulling,
    elevatePulling,
    datesDetecting,
    matchRunning,
    onConnectMediaPlan,
    onConnectElevate,
    mediaTabPicker,
    onPickMediaTab,
    onDismissMediaTabPicker,
    onDisconnectMediaPlan,
    onDisconnectElevate,
    onOpenMatch,
    onOpenPacing,
    onConfirm,
    onBack,
    clearError,
    adopting,
    onAdoptSheet,
}: Props) {
    const w = useWizard();

    const briefDone = w.brief.trim().length > 0;
    const changeLogDone = w.changeLog.trim().length > 0;
    const marketDone = w.marketVolume.trim().length > 0;
    const isEom = w.reportType === "EOM";
    // EOM decks carry no market-volume slide, so the field is hidden (and not required) for that type.
    const needsMarketVolume = !isEom;
    const datesDone = !!w.dateStart && !!w.dateEnd;
    const bothConnected = !!w.mediaPlan && !!w.elevate;
    // Counts describe the tactics being reported, so a plan line the user excluded at matching time
    // neither counts as unmatched nor blocks the pacing gate.
    const matched = w.activeMapping.filter((m) => m.lineItemId).length;
    const matchTotal = w.activeMapping.length;
    // Pacing is an EOM-only input and is entered per tactic, so it only appears once matching
    // produced the tactic list.
    const needsPacing = isEom && matchTotal > 0;
    const pacingReady = pacingReadyCount(w.activeMapping);

    return (
        <div className="rc-content">
            <div className="rc-section-head">
                <div className="rc-section-head__num">02</div>
                <div>
                    <h2 className="rc-section-head__title">Campaign data</h2>
                    <p className="rc-section-head__sub">
                        Provide the brief and connect the source sheets. These get confirmed before generation.
                    </p>
                </div>
            </div>

            <AdoptSheetCard adopting={adopting} onAdopt={onAdoptSheet} />

            <div className="rc-inputs-grid">
                <div className="rc-inputs-col">
                    <div className="rc-field">
                        <label className="rc-field__label">RFP / Campaign Brief</label>
                        <textarea
                            className="rc-textarea"
                            placeholder="Describe the campaign — client, goals, target audience, budget, flight dates, KPIs, channels used."
                            value={w.brief}
                            onChange={(e) => {
                                w.setBrief(e.target.value);
                                clearError("brief");
                            }}
                        />
                        {errors.brief && <div className="rc-field__error">Campaign brief is required.</div>}
                    </div>

                    <div className="rc-field">
                        <label className="rc-field__label">Change Log — mid-flight changes (optional)</label>
                        <textarea
                            className="rc-textarea"
                            placeholder="Log any changes made during the flight — budget shifts, audience weight changes, delayed launches, creative swaps. Helps the bot explain why results moved."
                            value={w.changeLog}
                            onChange={(e) => w.setChangeLog(e.target.value)}
                        />
                    </div>

                    {needsMarketVolume && (
                        <div className="rc-field">
                            <label className="rc-field__label">Market Volume</label>
                            <input
                                type="text"
                                inputMode="numeric"
                                className="rc-input"
                                placeholder="e.g. 48,200,000 addressable impressions"
                                value={w.marketVolume}
                                onChange={(e) => {
                                    w.setMarketVolume(e.target.value);
                                    clearError("marketVolume");
                                }}
                            />
                            {errors.marketVolume && (
                                <div className="rc-field__error">Market volume is required.</div>
                            )}
                        </div>
                    )}

                    <div className="rc-field">
                        <div className="rc-toggle-row">
                            <span
                                className={`rc-toggle-row__label${
                                    w.estimateDaypartGender ? " rc-toggle-row__label--on" : ""
                                }`}
                            >
                                Estimate dayparting &amp; gender split (AI)
                            </span>
                            <button
                                type="button"
                                role="switch"
                                aria-checked={w.estimateDaypartGender}
                                aria-label="Estimate dayparting and gender split with AI"
                                className={`rc-switch${w.estimateDaypartGender ? " rc-switch--on" : ""}`}
                                onClick={() => w.setEstimateDaypartGender(!w.estimateDaypartGender)}
                            >
                                <span className="rc-switch__knob" />
                            </button>
                        </div>
                        <div className="rc-field__hint">
                            Dayparting (weekday / weekend peaks) and gender distribution aren't always tracked
                            reliably on the DSP side, so we estimate them — it's a prediction, not a measured
                            figure. Leave this on for an AI estimate. Turn it off to leave those cells blank
                            (—) and fill them by hand from the DSP when the client needs exact numbers.
                        </div>
                    </div>

                    <ConnectRow
                        label="Media Plan — Google Sheet link"
                        placeholder="https://docs.google.com/spreadsheets/…"
                        connected={w.mediaPlan ? `${w.mediaPlan.title} · ${w.mediaPlan.rows} rows` : null}
                        pulling={mediaPulling}
                        onConnect={onConnectMediaPlan}
                        onDisconnect={onDisconnectMediaPlan}
                    />
                    {errors.sheet && <div className="rc-field__error rc-field__error--nudge">Media plan is required.</div>}

                    {mediaTabPicker && (
                        <MediaTabPicker
                            picker={mediaTabPicker}
                            pulling={mediaPulling}
                            onPick={onPickMediaTab}
                            onDismiss={onDismissMediaTabPicker}
                        />
                    )}

                    <ConnectRow
                        label="Elevate row data — Google Sheet link"
                        placeholder="https://docs.google.com/spreadsheets/…"
                        connected={w.elevate ? `${w.elevate.title} · ${w.elevate.rows} rows` : null}
                        pulling={elevatePulling}
                        onConnect={onConnectElevate}
                        onDisconnect={onDisconnectElevate}
                    />
                    {errors.adj && <div className="rc-field__error rc-field__error--nudge">Elevate data is required.</div>}

                    <div className="rc-field">
                        <label className="rc-field__label">
                            Flight dates
                            {datesDetecting && <span className="rc-field__hint"> — detecting from Elevate…</span>}
                        </label>
                        <div className="rc-daterange">
                            <input
                                type="date"
                                className="rc-input"
                                value={w.dateStart}
                                onChange={(e) => {
                                    w.setDateWindow(e.target.value, w.dateEnd);
                                    clearError("dates");
                                }}
                            />
                            <span className="rc-daterange__sep">→</span>
                            <input
                                type="date"
                                className="rc-input"
                                value={w.dateEnd}
                                onChange={(e) => {
                                    w.setDateWindow(w.dateStart, e.target.value);
                                    clearError("dates");
                                }}
                            />
                        </div>
                        {isEom && datesDone && !w.datesEdited && (
                            <div className="rc-field__hint">
                                Set to last full month, trimmed to the days the campaign actually ran — change
                                it if this deck covers a different period.
                            </div>
                        )}
                        {errors.dates && <div className="rc-field__error">Flight dates are required.</div>}
                    </div>

                    {bothConnected && (
                        <div className={`rc-match${w.matchConfirmed ? " rc-match--done" : ""}`}>
                            <div className="rc-match__text">
                                <div className="rc-match__label">
                                    {w.matchConfirmed ? "Line items mapped" : "Line items — needs review"}
                                </div>
                                <div className="rc-match__sub">
                                    {w.matchConfirmed
                                        ? `${matched} of ${matchTotal} tactics linked to a Line ID`
                                        : "Match tactics to their Line IDs before continuing"}
                                </div>
                            </div>
                            <button type="button" className="rc-btn rc-btn--ghost rc-btn--sm" onClick={onOpenMatch}>
                                {matchRunning ? <IconSpinner size={14} /> : null}
                                {w.matchConfirmed ? "Edit mapping" : "Match line items"}
                            </button>
                        </div>
                    )}

                    {needsPacing && (
                        <div className={`rc-match${w.pacingConfirmed ? " rc-match--done" : ""}`}>
                            <div className="rc-match__text">
                                <div className="rc-match__label">
                                    {w.pacingConfirmed ? "Pacing & rates set" : "Pacing & rates — needs input"}
                                </div>
                                <div className="rc-match__sub">
                                    {w.pacingConfirmed || pacingReady > 0
                                        ? `${pacingReady} of ${matchTotal} tactics with a budget, buy type and rate`
                                        : "Enter the monthly budget, buy type and rate for every tactic"}
                                </div>
                            </div>
                            <button type="button" className="rc-btn rc-btn--ghost rc-btn--sm" onClick={onOpenPacing}>
                                {w.pacingConfirmed ? "Edit pacing" : "Set pacing"}
                            </button>
                        </div>
                    )}

                    <div className="rc-actions rc-actions--split">
                        <button type="button" className="rc-btn rc-btn--outline" onClick={onBack}>
                            <IconArrowLeft size={16} />
                            Back
                        </button>
                        <button type="button" className="rc-btn rc-btn--primary" onClick={onConfirm}>
                            Confirm inputs
                            <IconArrowRight size={16} />
                        </button>
                    </div>
                </div>

                <aside className="rc-status">
                    <div className="rc-status__head">Input status</div>
                    <div className="rc-status__body">
                        <StatusRow label="RFP / Brief" done={briefDone} value={briefDone ? "Filled" : "Waiting"} />
                        <StatusRow
                            label="Change Log"
                            done={changeLogDone}
                            value={changeLogDone ? "Filled" : "Optional"}
                        />
                        {needsMarketVolume && (
                            <StatusRow
                                label="Market Volume"
                                done={marketDone}
                                value={marketDone ? "Filled" : "Waiting"}
                            />
                        )}
                        <StatusRow
                            label="Dayparting & gender"
                            done={w.estimateDaypartGender}
                            value={w.estimateDaypartGender ? "AI estimate" : "Blank (—)"}
                        />
                        <StatusRow
                            label="Media Plan"
                            done={!!w.mediaPlan}
                            value={w.mediaPlan ? "Connected" : "Waiting"}
                        />
                        <StatusRow
                            label="Elevate data"
                            done={!!w.elevate}
                            value={w.elevate ? "Connected" : "Waiting"}
                        />
                        <StatusRow label="Flight dates" done={datesDone} value={datesDone ? "Set" : "Waiting"} />
                        {isEom && (
                            <StatusRow
                                label="Pacing & rates"
                                done={w.pacingConfirmed}
                                value={
                                    w.pacingConfirmed ? "Confirmed" : matchTotal > 0 ? "Waiting" : "After matching"
                                }
                            />
                        )}
                    </div>
                </aside>
            </div>
        </div>
    );
}
