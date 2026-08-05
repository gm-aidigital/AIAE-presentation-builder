import type { SortDir } from "../lib/useTableSort";

/**
 * A clickable column heading.
 *
 * A button rather than a styled `div` so the table can be sorted from the keyboard, and
 * `aria-sort` so a screen reader announces the order rather than leaving the arrow as the only
 * evidence of it.
 */
export function SortHeader({
    label,
    columnKey,
    activeKey,
    dir,
    onSort,
    align = "left",
}: {
    label: string;
    columnKey: string;
    activeKey: string;
    dir: SortDir;
    onSort: (key: string) => void;
    align?: "left" | "right";
}) {
    const active = activeKey === columnKey;
    return (
        <button
            type="button"
            className={`ad-sort${active ? " ad-sort--active" : ""}${align === "right" ? " ad-sort--right" : ""}`}
            aria-sort={active ? (dir === "asc" ? "ascending" : "descending") : "none"}
            onClick={() => onSort(columnKey)}
        >
            {label}
            <span className="ad-sort__arrow" aria-hidden="true">
                {active ? (dir === "asc" ? "↑" : "↓") : "↕"}
            </span>
        </button>
    );
}
