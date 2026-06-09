import type { ReactNode } from "react";

interface Props {
    index: number;
    title: string;
    description?: string;
    done?: boolean;
    children: ReactNode;
}

/** Numbered wizard step card. Shows a check when `done`. */
export function StepCard({ index, title, description, done = false, children }: Props) {
    return (
        <section className={`rc-step${done ? " rc-step--done" : ""}`}>
            <header className="rc-step__head">
                <span className="rc-step__num">{done ? "✓" : index}</span>
                <div>
                    <h2 className="rc-step__title">{title}</h2>
                    {description && <p className="rc-step__desc">{description}</p>}
                </div>
            </header>
            <div className="rc-step__body">{children}</div>
        </section>
    );
}
