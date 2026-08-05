import type { AdminSavings } from "@/shared/api/types";
import { formatHours, formatUsd } from "../lib/tokenFormat";

/**
 * What the generated reports would have cost to build by hand, and what that is worth.
 *
 * The panel states its own assumptions rather than only its conclusion. Every figure here is a
 * model, and a dollar amount with no visible basis is a number nobody can check or argue with — so
 * the rate and the per-slide baseline are printed alongside, and the two reference deck sizes give
 * a reader something to compare against decks they remember building.
 */
export function SavingsPanel({ savings }: { savings: AdminSavings }) {
    const modelled = Math.max(0, savings.slidesTotal - savings.slidesMeasured);

    return (
        <div className="ad-savings">
            <div className="ad-savings__head">
                <span className="ad-savings__title">Time and money saved</span>
                <span className="ad-savings__basis">
                    {savings.minutesPerSlide} min/slide · {formatUsd(savings.hourlyRateUsd)}/hour
                </span>
            </div>

            <div className="ad-savings__figures">
                <div className="ad-savings__figure ad-savings__figure--hero">
                    <div className="ad-savings__num">{formatHours(savings.savedHours)}</div>
                    <div className="ad-savings__label">Hours saved</div>
                    <div className="ad-savings__sub">{formatHours(savings.savedHoursThisMonth)} this month</div>
                </div>
                <div className="ad-savings__figure">
                    <div className="ad-savings__num">{formatUsd(savings.savedUsd)}</div>
                    <div className="ad-savings__label">Value of that time</div>
                    <div className="ad-savings__sub">{formatUsd(savings.savedUsdThisMonth)} this month</div>
                </div>
                <div className="ad-savings__figure">
                    <div className="ad-savings__num">{savings.slidesTotal.toLocaleString("en-US")}</div>
                    <div className="ad-savings__label">Slides produced</div>
                    <div className="ad-savings__sub">
                        {savings.avgSlidesPerReport.toFixed(1)} per report · {savings.reportsCounted} reports
                    </div>
                </div>
            </div>

            <dl className="ad-savings__detail">
                <div className="ad-savings__row">
                    <dt>By hand</dt>
                    <dd>{formatHours(savings.manualHours)}</dd>
                </div>
                <div className="ad-savings__row">
                    <dt>Generated in</dt>
                    <dd>{formatHours(savings.automationHours)}</dd>
                </div>
                <div className="ad-savings__row">
                    <dt>A 25-slide deck by hand</dt>
                    <dd>{formatHours(savings.manualHoursFor25Slides)}</dd>
                </div>
                <div className="ad-savings__row">
                    <dt>A 16-slide deck by hand</dt>
                    <dd>{formatHours(savings.manualHoursFor16Slides)}</dd>
                </div>
            </dl>

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
