import { useEffect, useState } from "react";

/** Canonical debounce hook — updates via useEffect with cleanup (required by structure-lint). */
export function useDebounce<T>(value: T, delayMs: number): T {
    const [debounced, setDebounced] = useState(value);

    useEffect(() => {
        const id = window.setTimeout(() => setDebounced(value), delayMs);
        return () => window.clearTimeout(id);
    }, [value, delayMs]);

    return debounced;
}
