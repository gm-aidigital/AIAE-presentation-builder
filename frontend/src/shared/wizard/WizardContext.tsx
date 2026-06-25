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
    geoRows: Rows2D; // Geo (optional)
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

    const invalidateMatch = useCallback(() => {
        setMappingState(null);
        setMatchConfirmed(false);
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
            },
            disconnectElevate: () => {
                setElevate(null);
                invalidateMatch();
            },
            setMapping: (m) => {
                setMappingState(m);
                setMatchConfirmed(false);
            },
            confirmMatch: () => setMatchConfirmed(true),
            resetMatch: invalidateMatch,
        }),
        [brief, reportType, marketVolume, mediaPlan, elevate, mapping, matchConfirmed, invalidateMatch]
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
