import type { ReportType } from "@/shared/api/types";
import { useWizard } from "@/shared/wizard/WizardContext";
import { IconArrowRight, IconCheck } from "./icons";

interface TypeCard {
    /** Backend report type, or null when the card is not yet wired up. */
    id: ReportType | null;
    name: string;
    tag: string;
    desc: string;
}

// EOC and EOM are active (backend supports both); Agenda & Excel don't exist
// server-side yet and are shown disabled with a "Soon" marker.
const CARDS: TypeCard[] = [
    { id: null, name: "Agenda", tag: "Document", desc: "Kickoff agenda — objectives, timeline, owners and next steps." },
    { id: null, name: "Excel", tag: "Spreadsheet", desc: "Formatted performance workbook with raw pivots per tactic." },
    { id: "EOM", name: "EOM", tag: "Slides", desc: "End-of-month performance review across every active tactic." },
    { id: "EOC", name: "EOC", tag: "Slides", desc: "End-of-campaign wrap — full results, insights and learnings." },
];

interface Props {
    onContinue(): void;
}

/** Screen 1 — pick the deliverable (report type). */
export function StepReportType({ onContinue }: Props) {
    const { reportType, setReportType } = useWizard();

    return (
        <div className="rc-content">
            <div className="rc-hero">
                <span className="rc-hero__eyebrow">
                    <span className="rc-hero__eyebrow-dot" />
                    Campaign Report Generator
                </span>
                <h1 className="rc-hero__title">Start a new report</h1>
                <p className="rc-hero__sub">
                    Pick a deliverable. We read your Sheets, apply the brief, and build the report for you.
                </p>
            </div>

            <div className="rc-section-head">
                <div className="rc-section-head__num">01</div>
                <div>
                    <h2 className="rc-section-head__title">Choose report type</h2>
                    <p className="rc-section-head__sub">This sets the template, sections and output format.</p>
                </div>
            </div>

            <div className="rc-typegrid">
                {CARDS.map((card) => {
                    const disabled = card.id === null;
                    const selected = !disabled && card.id === reportType;
                    return (
                        <button
                            type="button"
                            key={card.name}
                            className={`rc-typecard${selected ? " rc-typecard--selected" : ""}${
                                disabled ? " rc-typecard--disabled" : ""
                            }`}
                            disabled={disabled}
                            aria-pressed={selected}
                            onClick={() => card.id && setReportType(card.id)}
                        >
                            <div className="rc-typecard__top">
                                <span className="rc-typecard__name">{card.name}</span>
                                {disabled ? (
                                    <span className="rc-typecard__soon">Soon</span>
                                ) : (
                                    <span className="rc-typecard__tick">{selected && <IconCheck size={12} />}</span>
                                )}
                            </div>
                            <span className="rc-typecard__tag">{card.tag}</span>
                            <p className="rc-typecard__desc">{card.desc}</p>
                        </button>
                    );
                })}
            </div>

            <div className="rc-actions rc-actions--end">
                <button type="button" className="rc-btn rc-btn--primary" onClick={onContinue}>
                    Continue
                    <IconArrowRight size={16} />
                </button>
            </div>
        </div>
    );
}
