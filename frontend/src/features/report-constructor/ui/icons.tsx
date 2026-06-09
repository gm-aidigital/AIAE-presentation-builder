// Inline SVG icons (stroke = currentColor) matching the original index.php markup.
interface P {
    size?: number;
    className?: string;
}

function stroke(size: number) {
    return {
        width: size,
        height: size,
        viewBox: "0 0 24 24",
        fill: "none",
        stroke: "currentColor",
        strokeWidth: 2,
        strokeLinecap: "round" as const,
        strokeLinejoin: "round" as const,
    };
}

export function IconLogo() {
    return (
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M8 1L14 4.5V11.5L8 15L2 11.5V4.5L8 1Z" stroke="currentColor" strokeWidth="1.5" />
            <circle cx="8" cy="8" r="2" fill="currentColor" />
        </svg>
    );
}
export function IconRefresh({ size = 13 }: P) {
    return (
        <svg {...stroke(size)}>
            <polyline points="1 4 1 10 7 10" />
            <path d="M3.51 15a9 9 0 1 0 .49-3.33" />
        </svg>
    );
}
export function IconSpinner({ size = 13 }: P) {
    return (
        <svg
            width={size}
            height={size}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            style={{ animation: "spin 0.85s linear infinite" }}
        >
            <path d="M21 12a9 9 0 1 1-6.219-8.56" />
        </svg>
    );
}
export function IconCheck({ size = 14 }: P) {
    return (
        <svg {...stroke(size)}>
            <polyline points="20 6 9 17 4 12" />
        </svg>
    );
}
export function IconX({ size = 11 }: P) {
    return (
        <svg {...stroke(size)}>
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
    );
}
export function IconSheet({ size = 14 }: P) {
    return (
        <svg {...stroke(size)}>
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <line x1="3" y1="9" x2="21" y2="9" />
            <line x1="9" y1="21" x2="9" y2="9" />
        </svg>
    );
}
export function IconChevron({ size = 13, className }: P) {
    return (
        <svg {...stroke(size)} className={className}>
            <polyline points="6 9 12 15 18 9" />
        </svg>
    );
}
export function IconEye({ size = 14 }: P) {
    return (
        <svg {...stroke(size)}>
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
            <circle cx="12" cy="12" r="3" />
        </svg>
    );
}
export function IconBolt({ size = 15 }: P) {
    return (
        <svg {...stroke(size)}>
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
        </svg>
    );
}
export function IconLink2({ size = 16 }: P) {
    return (
        <svg {...stroke(size)}>
            <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
            <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
    );
}
export function IconMonitor({ size = 14 }: P) {
    return (
        <svg {...stroke(size)}>
            <rect x="2" y="3" width="20" height="14" rx="2" />
            <line x1="8" y1="21" x2="16" y2="21" />
            <line x1="12" y1="17" x2="12" y2="21" />
        </svg>
    );
}
export function IconInfo({ size = 15 }: P) {
    return (
        <svg {...stroke(size)}>
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
    );
}
export function IconChecklist({ size = 13 }: P) {
    return (
        <svg {...stroke(size)}>
            <polyline points="9 11 12 14 22 4" />
            <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
        </svg>
    );
}
export function IconClock({ size = 11 }: P) {
    return (
        <svg {...stroke(size)}>
            <circle cx="12" cy="12" r="10" />
            <path d="M12 8v4l3 3" />
        </svg>
    );
}
