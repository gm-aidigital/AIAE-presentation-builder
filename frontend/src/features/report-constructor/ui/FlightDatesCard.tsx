interface FlightDatesCardProps {
    /** Whether the Elevate raw-data datasheet is connected yet. */
    connected: boolean;
    /** True while the raw-data date range is being detected. */
    detecting: boolean;
    /** True once detection returned a usable range (vs. manual entry needed). */
    detected: boolean;
    start: string;
    end: string;
    confirmed: boolean;
    onChange(start: string, end: string): void;
    onConfirm(): void;
}

/**
 * Step 05 — confirm the reporting flight window. The dates are detected from the
 * Elevate "Basic" raw-data tab (never the media plan). The user must confirm (or
 * correct) them before generation runs, so the report is built for the right range.
 */
export function FlightDatesCard({
    connected,
    detecting,
    detected,
    start,
    end,
    confirmed,
    onChange,
    onConfirm,
}: FlightDatesCardProps) {
    const rangeInvalid = !start || !end || start > end;

    return (
        <div className="section-card" id="s5">
            <div className="card-header">
                <div className="card-num">05</div>
                <div>
                    <div className="card-title">Flight Dates</div>
                    <div className="card-desc">Confirm the reporting date range from the raw data</div>
                </div>
            </div>
            <div className="card-body">
                {!connected ? (
                    <div className="sheets-hint">
                        Connect the <strong>Elevate Dashboard Datasheet</strong> above first — the flight dates
                        are read from its <strong>Basic</strong> tab.
                    </div>
                ) : detecting ? (
                    <div className="sheets-hint">Detecting the date range from the raw data…</div>
                ) : (
                    <>
                        <div className="sheets-hint">
                            {detected ? (
                                <>
                                    The report will be built for the following dates, taken from the raw-data{" "}
                                    <strong>Basic</strong> tab. Is this correct? If not, set the correct flight
                                    dates below.
                                </>
                            ) : (
                                <>
                                    No dates could be detected in the raw data. Enter the correct{" "}
                                    <strong>flight dates</strong> for this report.
                                </>
                            )}
                        </div>
                        <div className="flight-dates__row">
                            <label className="flight-dates__field">
                                <span className="flight-dates__label">From</span>
                                <input
                                    className="input-field"
                                    type="date"
                                    value={start}
                                    max={end || undefined}
                                    onChange={(e) => onChange(e.target.value, end)}
                                />
                            </label>
                            <label className="flight-dates__field">
                                <span className="flight-dates__label">To</span>
                                <input
                                    className="input-field"
                                    type="date"
                                    value={end}
                                    min={start || undefined}
                                    onChange={(e) => onChange(start, e.target.value)}
                                />
                            </label>
                        </div>
                        {confirmed ? (
                            <div className="flight-dates__confirmed">
                                ✓ Flight dates confirmed — the report will cover {start} → {end}
                            </div>
                        ) : (
                            <button
                                className="btn-dates-confirm"
                                disabled={rangeInvalid}
                                onClick={onConfirm}
                            >
                                Confirm these dates
                            </button>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
