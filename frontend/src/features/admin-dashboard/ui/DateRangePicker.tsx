import { useEffect, useRef, useState } from "react";
import {
    DateRange,
    RANGE_PRESETS,
    formatRange,
    matchPreset,
    toIsoDay,
} from "../lib/dateRange";

/**
 * The window every figure on the dashboard is scoped to.
 *
 * Presets and two date inputs, not a drawn calendar grid: the windows people actually want are
 * named ones, and for the rest a pair of native date fields is more precise than clicking through
 * months — and it comes with the platform's own keyboard handling and locale for free.
 *
 * Custom dates are applied on submit rather than on every keystroke, because a half-typed year
 * would otherwise fire a request for the year 20.
 */
export function DateRangePicker({
    value,
    onChange,
}: {
    value: DateRange;
    onChange: (range: DateRange) => void;
}) {
    const [open, setOpen] = useState(false);
    const [draft, setDraft] = useState<DateRange>(value);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const activePreset = matchPreset(value);
    const today = toIsoDay(new Date());

    // Reopening should show what is currently applied, not whatever was half-typed and abandoned
    // last time.
    useEffect(() => {
        if (open) setDraft(value);
    }, [open, value]);

    useEffect(() => {
        if (!open) return undefined;
        function onDocumentPointerDown(event: MouseEvent) {
            if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
                setOpen(false);
            }
        }
        function onEscape(event: KeyboardEvent) {
            if (event.key === "Escape") setOpen(false);
        }
        document.addEventListener("mousedown", onDocumentPointerDown);
        document.addEventListener("keydown", onEscape);
        return () => {
            document.removeEventListener("mousedown", onDocumentPointerDown);
            document.removeEventListener("keydown", onEscape);
        };
    }, [open]);

    function applyPreset(id: string) {
        const preset = RANGE_PRESETS.find((candidate) => candidate.id === id);
        if (!preset) return;
        onChange(preset.range(new Date()));
        setOpen(false);
    }

    function applyDraft() {
        // Reversed ends are a slip, not an error — the server swaps them too, but fixing it here
        // means the field never shows a window the user did not mean.
        const ordered: DateRange =
            draft.from > draft.to ? { from: draft.to, to: draft.from } : draft;
        onChange(ordered);
        setOpen(false);
    }

    return (
        <div className="ad-range" ref={wrapperRef}>
            <button
                type="button"
                className="ad-range__trigger"
                aria-expanded={open}
                aria-haspopup="dialog"
                onClick={() => setOpen((current) => !current)}
            >
                <span className="ad-range__icon" aria-hidden="true">
                    🗓
                </span>
                {formatRange(value)}
                <span className="ad-range__caret" aria-hidden="true">
                    ▾
                </span>
            </button>

            {open && (
                <div className="ad-range__panel" role="dialog" aria-label="Select a date range">
                    <div className="ad-range__presets">
                        {RANGE_PRESETS.map((preset) => (
                            <button
                                key={preset.id}
                                type="button"
                                className={`ad-range__preset${
                                    activePreset === preset.id ? " ad-range__preset--active" : ""
                                }`}
                                onClick={() => applyPreset(preset.id)}
                            >
                                {preset.label}
                            </button>
                        ))}
                    </div>

                    <form
                        className="ad-range__custom"
                        onSubmit={(event) => {
                            event.preventDefault();
                            applyDraft();
                        }}
                    >
                        <label className="ad-range__field">
                            <span className="ad-range__label">From</span>
                            <input
                                type="date"
                                className="ad-range__input"
                                value={draft.from}
                                max={today}
                                onChange={(event) =>
                                    setDraft((current) => ({ ...current, from: event.target.value }))
                                }
                            />
                        </label>
                        <label className="ad-range__field">
                            <span className="ad-range__label">To</span>
                            <input
                                type="date"
                                className="ad-range__input"
                                value={draft.to}
                                max={today}
                                onChange={(event) =>
                                    setDraft((current) => ({ ...current, to: event.target.value }))
                                }
                            />
                        </label>
                        <button type="submit" className="ad-range__apply" disabled={!draft.from || !draft.to}>
                            Apply
                        </button>
                    </form>
                </div>
            )}
        </div>
    );
}
