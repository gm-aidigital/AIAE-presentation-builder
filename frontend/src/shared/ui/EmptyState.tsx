import "./ui-states.css";

interface Props {
    message: string;
}

/** Neutral empty-state copy when a list or query returns no rows. */
export function EmptyState({ message }: Props) {
    return (
        <p className="ui-state ui-state--empty" role="status">
            {message}
        </p>
    );
}
