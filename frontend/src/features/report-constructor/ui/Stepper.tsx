import { IconCheck } from "./icons";

const LABELS = ["Report Type", "Data Inputs", "Breakdowns", "Review Sheet", "Generate"];

interface Props {
    /** 0-based index of the active step. */
    active: number;
}

/** Full-width 5-step progress bar under the header. */
export function Stepper({ active }: Props) {
    return (
        <div className="rc-stepper">
            {LABELS.map((label, i) => {
                const state = i < active ? "done" : i === active ? "active" : "todo";
                return (
                    <div className="rc-stepper__group" key={label}>
                        {i > 0 && <span className="rc-stepper__line" />}
                        <div className={`rc-stepper__step rc-stepper__step--${state}`}>
                            <span className="rc-stepper__circle">
                                {state === "done" ? <IconCheck size={13} /> : i + 1}
                            </span>
                            <span className="rc-stepper__label">{label}</span>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
