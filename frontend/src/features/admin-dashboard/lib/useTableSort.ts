import { useMemo, useState } from "react";

/** Which way a sorted column runs. */
export type SortDir = "asc" | "desc";

/** The column a table is currently ordered by, and which way. */
export interface SortState<K extends string> {
    key: K;
    dir: SortDir;
}

/**
 * Client-side column sorting for the dashboard's small tables.
 *
 * Only for tables the server hands over whole — "By user" and "Spend by user" have one row per
 * person, so sorting them in the browser is instant and costs no request. The team-wide report
 * history is not one of these: it is paged, and sorting a page in the browser would reorder fifty
 * rows out of thousands while pretending to have ordered the table. That one sorts in the database.
 *
 * Rows sort by whatever `value` returns for the active key. Numbers compare numerically, everything
 * else by locale string; absent values always sink to the bottom, in both directions, because a row
 * with nothing to show is not "the smallest" — it is unranked.
 */
export function useTableSort<T, K extends string>(
    rows: T[],
    value: (row: T, key: K) => string | number | undefined | null,
    initial: SortState<K>,
) {
    const [sort, setSort] = useState<SortState<K>>(initial);

    const sorted = useMemo(() => {
        const factor = sort.dir === "asc" ? 1 : -1;
        return [...rows].sort((a, b) => {
            const left = value(a, sort.key);
            const right = value(b, sort.key);
            const leftMissing = left === undefined || left === null || left === "";
            const rightMissing = right === undefined || right === null || right === "";
            if (leftMissing || rightMissing) {
                if (leftMissing && rightMissing) return 0;
                return leftMissing ? 1 : -1;
            }
            if (typeof left === "number" && typeof right === "number") {
                return (left - right) * factor;
            }
            return String(left).localeCompare(String(right)) * factor;
        });
    }, [rows, sort, value]);

    /**
     * Handles a header click: a new column starts descending (the interesting end of every column
     * here is the big end), and clicking the active column flips it.
     */
    function toggle(key: K) {
        setSort((current) =>
            current.key === key
                ? { key, dir: current.dir === "asc" ? "desc" : "asc" }
                : { key, dir: "desc" },
        );
    }

    return { sort, sorted, toggle };
}
