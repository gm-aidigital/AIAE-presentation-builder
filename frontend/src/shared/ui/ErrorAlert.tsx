import "./ui-states.css";

interface Props {
    message: string;
}

/** Accessible error banner for failed queries or form submissions. */
export function ErrorAlert({ message }: Props) {
    return (
        <p className="ui-state ui-state--error" role="alert">
            {message}
        </p>
    );
}
