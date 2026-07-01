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
    reportType: ReportType;
    marketVolume: string;
    mediaPlan: MediaPlanState | null;
    elevate: ElevateState | null;
    mapping: MappingEntry[] | null;
    matchConfirmed: boolean;
    // Flight window confirmed by the user, derived from the raw-data ("Basic" tab)
    // date range. Dates are ISO yyyy-MM-dd; the media plan is never used for dates.
    dateStart: string;
    dateEnd: string;
    dateConfirmed: boolean;

    setBrief(value: string): void;
    setReportType(value: ReportType): void;
    setMarketVolume(value: string): void;
    connectMediaPlan(value: MediaPlanState): void;
    updateMediaPlanTabs(patch: Partial<OptionalTabs>): void;
    disconnectMediaPlan(): void;
    connectElevate(value: ElevateState): void;
    disconnectElevate(): void;
    setMapping(mapping: MappingEntry[]): void;
    confirmMatch(): void;
    resetMatch(): void;
    setDateWindow(start: string, end: string): void;
    confirmDates(): void;
    resetDates(): void;
}

const WizardContext = createContext<WizardContextValue | null>(null);

export function WizardProvider({ children }: { children: ReactNode }) {
    const [brief, setBriefState] = useState("");
    const [reportType, setReportTypeState] = useState<ReportType>("EOC");
    const [marketVolume, setMarketVolumeState] = useState("");
    const [mediaPlan, setMediaPlan] = useState<MediaPlanState | null>(null);
    const [elevate, setElevate] = useState<ElevateState | null>(null);
    const [mapping, setMappingState] = useState<MappingEntry[] | null>(null);
    const [matchConfirmed, setMatchConfirmed] = useState(false);
    const [dateStart, setDateStart] = useState("");
    const [dateEnd, setDateEnd] = useState("");
    const [dateConfirmed, setDateConfirmed] = useState(false);

    const invalidateMatch = useCallback(() => {
        setMappingState(null);
        setMatchConfirmed(false);
    }, []);

    // Reconnecting/disconnecting the Elevate raw data replaces the "Basic" tab the
    // date window is derived from, so any previously confirmed window is stale.
    const invalidateDates = useCallback(() => {
        setDateStart("");
        setDateEnd("");
        setDateConfirmed(false);
    }, []);

    const value = useMemo<WizardContextValue>(
        () => ({
            brief,
            reportType,
            marketVolume,
            mediaPlan,
            elevate,
            mapping,
            matchConfirmed,
            dateStart,
            dateEnd,
            dateConfirmed,
            setBrief: setBriefState,
            setReportType: setReportTypeState,
            setMarketVolume: setMarketVolumeState,
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
                setMatchConfirmed(false);
            },
            confirmMatch: () => setMatchConfirmed(true),
            resetMatch: invalidateMatch,
            setDateWindow: (start, end) => {
                setDateStart(start);
                setDateEnd(end);
                setDateConfirmed(false);
            },
            confirmDates: () => setDateConfirmed(true),
            resetDates: invalidateDates,
        }),
        [
            brief,
            reportType,
            marketVolume,
            mediaPlan,
            elevate,
            mapping,
            matchConfirmed,
            dateStart,
            dateEnd,
            dateConfirmed,
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
