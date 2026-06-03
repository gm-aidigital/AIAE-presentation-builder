import { ReactNode } from "react";
import "./app-shell.css";

interface Props {
    title: string;
    subtitle?: string;
    actions?: ReactNode;
}

/** Page-level title row — one primary action slot on the right. */
export function PageHeader({ title, subtitle, actions }: Props) {
    return (
        <div className="page-header">
            <div className="page-header__text">
                <h1 className="page-header__title">{title}</h1>
                {subtitle && <p className="page-header__subtitle">{subtitle}</p>}
            </div>
            {actions && <div className="page-header__actions">{actions}</div>}
        </div>
    );
}
