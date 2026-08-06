import type { AdminSavings } from "@/shared/api/types";
import { formatHours, formatUsd } from "../lib/tokenFormat";

/**
 * What the selected window's reports would have cost to build by hand, and what that is worth.
 *
 * The panel states its own assumptions rather than only its conclusion. Every figure here is a
 * model, and a dollar amount with no visible basis is a number nobody can check or argue with — so
 * the rate and the per-slide baseline are printed alongside it.
 */
export function SavingsPanel({ savings }: { savings: AdminSavings }) {
    const modelled = Math.max(0, savings.slidesTotal - savings.slidesMeasured);

    return (
        <div className="ad-savings">
            <div className="ad-savings__head">
                <span className="ad-savings__title">Time and money saved</span>
                <span
                    className="ad-savings__basis"
                    title={`${formatHours(savings.manualHours)} of manual work, less the ${formatHours(
                        savings.automationHours,
                    )} the runs actually took`}
                >
                    {savings.minutesPerSlide} min/slide · {formatUsd(savings.hourlyRateUsd)}/hour
                </span>
            </div>

            <div className="ad-savings__figures">
                <div className="ad-savings__figure ad-savings__figure--hero">
                    <div className="ad-savings__num">{formatHours(savings.savedHours)}</div>
                    <div className="ad-savings__label">Hours saved</div>
                </div>
                <div className="ad-savings__figure">
                    <div className="ad-savings__num">{formatUsd(savings.savedUsd)}</div>
                    <div className="ad-savings__label">Value of that time</div>
                </div>
                <div className="ad-savings__figure">
                    <div className="ad-savings__num">{savings.slidesTotal.toLocaleString("en-US")}</div>
                    <div className="ad-savings__label">Slides produced</div>
                    <div className="ad-savings__sub">
                        {savings.avgSlidesPerReport.toFixed(1)} per report · {savings.reportsCounted} reports
                    </div>
                </div>
            </div>

            <p className="ad-savings__note">
                A modelled comparison, not a measurement. The generated runs' own wall-clock time is already
                subtracted.
                {modelled > 0 && (
                    <>
                        {" "}
                        {savings.slidesMeasured.toLocaleString("en-US")} of these slides were counted in a finished
                        deck; the other {modelled.toLocaleString("en-US")} are assumed at the configured per-type
                        default, for reports generated before slide counting existed.
                    </>
                )}
            </p>
        </div>
    );
}
