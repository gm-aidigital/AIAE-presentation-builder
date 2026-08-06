import { IconCheck } from "./icons";

const LABELS = ["Report Type", "Data Inputs", "Breakdowns", "Review Sheet", "Generate"];

interface Props {
    /** 0-based index of the active step. */
    active: number;
    /** Highest step the user has reached — steps up to here are navigable. */
    maxReached: number;
    /**
     * Lowest step still navigable. A resumed draft starts at the review step with no source grids
     * in memory, so the earlier steps cannot be re-run and are not offered.
     */
    minReached?: number;
    /** When true, navigation is disabled (e.g. a build/generate job is running). */
    locked?: boolean;
    onNavigate(index: number): void;
}

/** Full-width 5-step progress bar under the header. Completed steps are clickable. */
export function Stepper({ active, maxReached, minReached = 0, locked = false, onNavigate }: Props) {
    return (
        <div className="rc-stepper">
            {LABELS.map((label, i) => {
                const state = i < active ? "done" : i === active ? "active" : "todo";
                const navigable = i <= maxReached && i >= minReached && i !== active && !locked;
                return (
                    <div className="rc-stepper__group" key={label}>
                        {i > 0 && <span className="rc-stepper__line" />}
                        <button
                            type="button"
                            className={`rc-stepper__step rc-stepper__step--${state}${
                                navigable ? " rc-stepper__step--nav" : ""
                            }`}
                            disabled={!navigable}
                            aria-current={i === active ? "step" : undefined}
                            onClick={() => navigable && onNavigate(i)}
                        >
                            <span className="rc-stepper__circle">
                                {state === "done" ? <IconCheck size={13} /> : i + 1}
                            </span>
                            <span className="rc-stepper__label">{label}</span>
                        </button>
                    </div>
                );
            })}
        </div>
    );
}
