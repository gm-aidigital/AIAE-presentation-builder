// Wizard state shared across all Report Constructor steps. Mirrors the legacy
// single `state` object → React state (no localStorage). Connecting or
// disconnecting either sheet invalidates the line-item match (matchConfirmed
// + mapping reset), matching the legacy gate.
import {
    createContext,
    useCallback,
    useContext,
    useMemo,
    useState,
    type ReactNode,
} from "react";
import type { MappingEntry, ReportType, Rows2D } from "../api/types";

export interface ConnectedSheet {
    title: string;
    tab: string;
    sheetId: string;
    rows: number;
    cols: number;
    tabsCount: number;
    headers: string[];
    preview: string[][];
}

export interface MediaPlanState extends ConnectedSheet {
    sheetRows: Rows2D; // Proposal (primary)
    audienceRows: Rows2D; // Audience&Inventory (optional)
    estimatesRows: Rows2D; // Estimates (optional, falls back to Proposal)
    geoRows: Rows2D; // all workbook tabs bundled (each prefixed "### TAB: <name> ###") for Claude geo extraction
}

export interface ElevateState extends ConnectedSheet {
    adjRows: Rows2D; // Basic; sheetId doubles as bqSheetId (presence gate)
}

type OptionalTabs = Pick<MediaPlanState, "audienceRows" | "estimatesRows" | "geoRows">;

interface WizardContextValue {
    brief: string;
    changeLog: string;
    reportType: ReportType;
    marketVolume: string;
    // When false the per-tactic dayparting (weekdays/weekends) and gender split (male/female) are written
    // to the deck as an em-dash instead of being estimated by Claude — those DSP metrics aren't always
    // tracked reliably, so a data-sensitive client can fill them by hand instead of showing a prediction.
    estimateDaypartGender: boolean;
    mediaPlan: MediaPlanState | null;
    elevate: ElevateState | null;
    mapping: MappingEntry[] | null;
    // Media-plan rows the user dropped on the matching screen (by their original tacticNum). A media
    // plan often carries line items this report is not about; a dropped row keeps its place in
    // `mapping` — so the plan-order figures stay aligned and the drop stays undoable — but is absent
    // from `activeMapping`, which is what every later step and the generate payload are built from.
    excludedTactics: number[];
    /** The tactics the report actually covers: `mapping` minus the excluded rows, in plan order. */
    activeMapping: MappingEntry[];
    matchConfirmed: boolean;
    // EOM pacing (monthly budget + buy type + final rate per tactic) is confirmed in its own
    // dialog, separately from the line-item mapping, so editing one never re-opens the other.
    pacingConfirmed: boolean;
    // Flight window confirmed by the user, derived from the raw-data ("Basic" tab)
    // date range. Dates are ISO yyyy-MM-dd; the media plan is never used for dates.
    dateStart: string;
    dateEnd: string;
    dateConfirmed: boolean;
    // True once the user typed a date themselves — suggestions (EOM's previous-month
    // default, the Elevate detection) never overwrite a hand-picked window.
    datesEdited: boolean;

    setBrief(value: string): void;
    setChangeLog(value: string): void;
    setReportType(value: ReportType): void;
    setMarketVolume(value: string): void;
    setEstimateDaypartGender(value: boolean): void;
    connectMediaPlan(value: MediaPlanState): void;
    updateMediaPlanTabs(patch: Partial<OptionalTabs>): void;
    disconnectMediaPlan(): void;
    connectElevate(value: ElevateState): void;
    disconnectElevate(): void;
    setMapping(mapping: MappingEntry[]): void;
    /** Drops a media-plan row from the report, or puts it back; identified by its original tacticNum. */
    toggleTacticExcluded(tacticNum: number): void;
    confirmMatch(): void;
    resetMatch(): void;
    /** Writes the pacing fields (monthly budget, rate type, unit price) without touching the confirmed mapping. */
    setPacing(mapping: MappingEntry[]): void;
    confirmPacing(): void;
    setDateWindow(start: string, end: string): void;
    suggestDateWindow(start: string, end: string): void;
    confirmDates(): void;
    resetDates(): void;
}

const WizardContext = createContext<WizardContextValue | null>(null);

export function WizardProvider({ children }: { children: ReactNode }) {
    const [brief, setBriefState] = useState("");
    const [changeLog, setChangeLogState] = useState("");
    const [reportType, setReportTypeState] = useState<ReportType>("EOC");
    const [marketVolume, setMarketVolumeState] = useState("");
    const [estimateDaypartGender, setEstimateDaypartGenderState] = useState(true);
    const [mediaPlan, setMediaPlan] = useState<MediaPlanState | null>(null);
    const [elevate, setElevate] = useState<ElevateState | null>(null);
    const [mapping, setMappingState] = useState<MappingEntry[] | null>(null);
    const [excludedTactics, setExcludedTactics] = useState<number[]>([]);
    const [matchConfirmed, setMatchConfirmed] = useState(false);
    const [pacingConfirmed, setPacingConfirmed] = useState(false);
    const [dateStart, setDateStart] = useState("");
    const [dateEnd, setDateEnd] = useState("");
    const [dateConfirmed, setDateConfirmed] = useState(false);
    const [datesEdited, setDatesEdited] = useState(false);

    // Re-matching rebuilds the tactic list, so any pacing entered against the old list is stale too.
    const invalidateMatch = useCallback(() => {
        setMappingState(null);
        setExcludedTactics([]);
        setMatchConfirmed(false);
        setPacingConfirmed(false);
    }, []);

    // The report's tactic list. Kept in plan order: the renumbering to a dense 1..N happens once, at
    // the edge where the generate payload is built, so nothing in the UI has to track two numberings.
    const activeMapping = useMemo(
        () => (mapping ?? []).filter((m) => !excludedTactics.includes(m.tacticNum)),
        [mapping, excludedTactics]
    );

    // Reconnecting/disconnecting the Elevate raw data replaces the "Basic" tab the
    // date window is derived from, so any previously confirmed window is stale.
    const invalidateDates = useCallback(() => {
        setDateStart("");
        setDateEnd("");
        setDateConfirmed(false);
        setDatesEdited(false);
    }, []);

    const value = useMemo<WizardContextValue>(
        () => ({
            brief,
            changeLog,
            reportType,
            marketVolume,
            estimateDaypartGender,
            mediaPlan,
            elevate,
            mapping,
            excludedTactics,
            activeMapping,
            matchConfirmed,
            pacingConfirmed,
            dateStart,
            dateEnd,
            dateConfirmed,
            datesEdited,
            setBrief: setBriefState,
            setChangeLog: setChangeLogState,
            setReportType: setReportTypeState,
            setMarketVolume: setMarketVolumeState,
            setEstimateDaypartGender: setEstimateDaypartGenderState,
            connectMediaPlan: (v) => {
                setMediaPlan(v);
                invalidateMatch();
            },
            updateMediaPlanTabs: (patch) =>
                setMediaPlan((prev) => (prev ? { ...prev, ...patch } : prev)),
            disconnectMediaPlan: () => {
                setMediaPlan(null);
                invalidateMatch();
            },
            connectElevate: (v) => {
                setElevate(v);
                invalidateMatch();
                invalidateDates();
            },
            disconnectElevate: () => {
                setElevate(null);
                invalidateMatch();
                invalidateDates();
            },
            setMapping: (m) => {
                setMappingState(m);
                // A fresh match rebuilds the tactic list, so the old row numbers no longer mean anything.
                setExcludedTactics([]);
                setMatchConfirmed(false);
                setPacingConfirmed(false);
            },
            // Dropping or restoring a row changes which tactics the report covers, so the mapping has
            // to be confirmed again — and the pacing with it, since its rows follow the same list.
            toggleTacticExcluded: (tacticNum) => {
                setExcludedTactics((prev) =>
                    prev.includes(tacticNum) ? prev.filter((n) => n !== tacticNum) : [...prev, tacticNum]
                );
                setMatchConfirmed(false);
                setPacingConfirmed(false);
            },
            confirmMatch: () => setMatchConfirmed(true),
            resetMatch: invalidateMatch,
            setPacing: (m) => {
                setMappingState(m);
                setPacingConfirmed(false);
            },
            confirmPacing: () => setPacingConfirmed(true),
            setDateWindow: (start, end) => {
                setDateStart(start);
                setDateEnd(end);
                setDateConfirmed(false);
                setDatesEdited(true);
            },
            suggestDateWindow: (start, end) => {
                if (datesEdited) return;
                setDateStart(start);
                setDateEnd(end);
                setDateConfirmed(false);
            },
            confirmDates: () => setDateConfirmed(true),
            resetDates: invalidateDates,
        }),
        [
            brief,
            changeLog,
            reportType,
            marketVolume,
            estimateDaypartGender,
            mediaPlan,
            elevate,
            mapping,
            excludedTactics,
            activeMapping,
            matchConfirmed,
            pacingConfirmed,
            dateStart,
            dateEnd,
            dateConfirmed,
            datesEdited,
            invalidateMatch,
            invalidateDates,
        ]
    );

    return <WizardContext.Provider value={value}>{children}</WizardContext.Provider>;
}

export function useWizard(): WizardContextValue {
    const ctx = useContext(WizardContext);
    if (!ctx) {
        throw new Error("useWizard must be used within a WizardProvider");
    }
    return ctx;
}
