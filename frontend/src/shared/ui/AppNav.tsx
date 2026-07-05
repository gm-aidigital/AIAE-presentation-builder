import { useEffect, useRef, useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { useClerk, useUser } from "@clerk/clerk-react";
import { useCurrentUser } from "@/shared/auth/useCurrentUser";
import { useVersionQuery } from "@/shared/api/useVersionQuery";
import "./app-nav.css";

/** AI Digital wordmark (blue), from the design-system logo asset. */
function BrandLogo() {
    return (
        <svg height={22} viewBox="0 0 188 30" fill="none" xmlns="http://www.w3.org/2000/svg"
            style={{ display: "block", width: "auto" }} aria-label="AI Digital">
            <path d="M98.0936 29.3466C106.076 29.3466 111.588 23.0615 111.588 15.4018C111.588 15.0719 111.588 14.7764 111.585 14.498C111.578 13.9551 111.131 13.5393 110.588 13.5393H99.4063C98.8393 13.5393 98.3754 13.9998 98.3754 14.5702V16.1131C98.3754 16.6836 98.8393 17.144 99.4063 17.144H106.492C107.083 17.144 107.557 17.6423 107.461 18.2231C106.757 22.4292 103.265 25.7384 98.2551 25.7384C92.2964 25.7384 87.8395 20.8725 87.8395 14.5908C87.8395 8.30914 92.4992 3.60819 98.1726 3.60819C101.513 3.60819 104.849 5.52225 106.427 8.32632C106.623 8.67683 106.987 8.91738 107.389 8.91738H109.612C110.31 8.91738 110.805 8.23698 110.516 7.60125C108.609 3.37108 104.252 0 98.2551 0C90.1899 0 83.867 6.28512 83.867 14.6733C83.867 23.0615 90.1109 29.3466 98.0936 29.3466Z" fill="#0009DC" />
            <path d="M24.1945 28.8587H18.5726C17.8234 28.8587 17.1636 28.3741 16.9368 27.6628L13.4008 16.5461C13.095 15.5805 11.7307 15.5874 11.4352 16.5564L8.04352 27.6422C7.82359 28.3638 7.15693 28.8587 6.40093 28.8587H1.02989C0.328866 28.8587 -0.169407 28.1679 0.0539569 27.5013L8.80983 1.19244C8.95072 0.769763 9.3459 0.487981 9.78919 0.487981H15.4351C15.8784 0.487981 16.2736 0.769763 16.4145 1.19244L25.1704 27.5013C25.3937 28.1679 24.8955 28.8587 24.1945 28.8587Z" fill="#0009DC" />
            <path d="M35.9296 0.487981H30.2906C29.7212 0.487981 29.2597 0.949536 29.2597 1.51889V27.8277C29.2597 28.3971 29.7212 28.8587 30.2906 28.8587H35.9296C36.499 28.8587 36.9606 28.3971 36.9606 27.8277V1.51889C36.9606 0.949536 36.499 0.487981 35.9296 0.487981Z" fill="#0009DC" />
            <path fillRule="evenodd" clipRule="evenodd" d="M44.878 0.487981H52.8847C60.9499 0.487981 66.9086 6.36074 66.9086 14.5496C66.9086 22.7385 60.9499 28.8587 52.8847 28.8587H44.878C44.3075 28.8587 43.8471 28.3982 43.8471 27.8277V1.51889C43.8471 0.948455 44.3075 0.487981 44.878 0.487981ZM48.8092 25.1714H53.1287C58.7231 25.1714 62.9361 20.467 62.9361 14.5496C62.9361 8.63218 58.8022 4.17521 53.1287 4.17521H48.8092C48.2387 4.17521 47.7783 4.63568 47.7783 5.20612V24.1405C47.7783 24.7075 48.2387 25.1714 48.8092 25.1714Z" fill="#0009DC" />
            <path d="M77.3826 1.51889V27.8277C77.3826 28.3982 76.9221 28.8587 76.3517 28.8587H74.4823C73.9153 28.8587 73.4514 28.3982 73.4514 27.8277V1.51889C73.4514 0.948455 73.9153 0.487981 74.4823 0.487981H76.3517C76.9221 0.487981 77.3826 0.948455 77.3826 1.51889Z" fill="#0009DC" />
            <path d="M120.918 0.487981H119.049C118.479 0.487981 118.018 0.949536 118.018 1.51889V27.8277C118.018 28.3971 118.479 28.8587 119.049 28.8587H120.918C121.487 28.8587 121.949 28.3971 121.949 27.8277V1.51889C121.949 0.949536 121.487 0.487981 120.918 0.487981Z" fill="#0009DC" />
            <path d="M145.213 3.1443V1.51889C145.213 0.948455 144.749 0.487981 144.182 0.487981H128.492C127.921 0.487981 127.461 0.948455 127.461 1.51889V3.1443C127.461 3.71473 127.921 4.17521 128.492 4.17521H133.361C133.928 4.17521 134.392 4.63568 134.392 5.20612V27.8277C134.392 28.3982 134.852 28.8587 135.423 28.8587H137.251C137.818 28.8587 138.282 28.3982 138.282 27.8277V5.20612C138.282 4.63568 138.742 4.17521 139.313 4.17521H144.182C144.749 4.17521 145.213 3.71473 145.213 3.1443Z" fill="#0009DC" />
            <path d="M166.886 28.8587H164.811C164.364 28.8587 163.969 28.5734 163.831 28.1508L157.708 9.51189C157.395 8.56689 156.058 8.56689 155.749 9.51533L149.667 28.1473C149.529 28.57 149.134 28.8587 148.687 28.8587H146.784C146.079 28.8587 145.581 28.1645 145.808 27.4944L154.749 1.18556C154.893 0.766327 155.285 0.487981 155.725 0.487981H157.976C158.419 0.487981 158.811 0.769763 158.952 1.18556L167.862 27.4979C168.089 28.1645 167.591 28.8587 166.886 28.8587Z" fill="#0009DC" />
            <path d="M187.611 27.8277V26.2023C187.611 25.6319 187.147 25.1714 186.58 25.1714H178.508C177.938 25.1714 177.477 24.7075 177.477 24.1405V1.51889C177.477 0.948455 177.013 0.487981 176.446 0.487981H174.577C174.006 0.487981 173.546 0.948455 173.546 1.51889V27.8277C173.546 28.3982 174.006 28.8587 174.577 28.8587H186.58C187.147 28.8587 187.611 28.3982 187.611 27.8277Z" fill="#0009DC" />
        </svg>
    );
}

interface NavItem {
    label: string;
    to: string;
    end?: boolean;
}

/**
 * Shared top header for every area. The user dropdown is the primary navigation
 * between Report Constructor, My reports, and (admins only) the Dashboard, plus
 * sign out. Matches the AI Digital handoff header.
 */
export function AppNav() {
    const { user } = useUser();
    const { signOut } = useClerk();
    const { data: me } = useCurrentUser();
    const { data: version } = useVersionQuery();
    const navigate = useNavigate();

    const [open, setOpen] = useState(false);
    const wrapRef = useRef<HTMLDivElement>(null);

    // Close on outside click / Esc.
    useEffect(() => {
        if (!open) return;
        function onClick(e: MouseEvent) {
            if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
        }
        function onKey(e: KeyboardEvent) {
            if (e.key === "Escape") setOpen(false);
        }
        document.addEventListener("mousedown", onClick);
        document.addEventListener("keydown", onKey);
        return () => {
            document.removeEventListener("mousedown", onClick);
            document.removeEventListener("keydown", onKey);
        };
    }, [open]);

    const name = user?.fullName ?? user?.primaryEmailAddress?.emailAddress ?? "Account";
    const email = user?.primaryEmailAddress?.emailAddress ?? "";

    const items: NavItem[] = [
        { label: "Report Constructor", to: "/reports/new" },
        { label: "My reports", to: "/reports", end: true },
    ];
    if (me?.is_admin) items.push({ label: "Dashboard", to: "/admin" });

    return (
        <header className="app-nav">
            <button type="button" className="app-nav__brand" onClick={() => navigate("/reports/new")}>
                <BrandLogo />
                <span className="app-nav__product">Report Constructor</span>
            </button>

            <div className="app-nav__right" ref={wrapRef}>
                {version && (
                    <span className="app-nav__build" title={`Deployed commit ${version.commitId}`}>
                        build {version.commitId}
                    </span>
                )}
                <button
                    type="button"
                    className={`app-nav__trigger${open ? " app-nav__trigger--open" : ""}`}
                    onClick={() => setOpen((v) => !v)}
                    aria-haspopup="menu"
                    aria-expanded={open}
                >
                    <span className="app-nav__dot" />
                    <span className="app-nav__name">{name}</span>
                    <span className={`app-nav__caret${open ? " app-nav__caret--open" : ""}`}>▾</span>
                </button>

                {open && (
                    <div className="app-nav__menu" role="menu">
                        <div className="app-nav__identity">
                            <div className="app-nav__identity-name">{name}</div>
                            {email && <div className="app-nav__identity-email">{email}</div>}
                        </div>
                        <nav className="app-nav__list">
                            {items.map((it) => (
                                <NavLink
                                    key={it.to}
                                    to={it.to}
                                    end={it.end}
                                    className={({ isActive }) =>
                                        `app-nav__link${isActive ? " app-nav__link--active" : ""}`
                                    }
                                    onClick={() => setOpen(false)}
                                >
                                    {it.label}
                                </NavLink>
                            ))}
                        </nav>
                        <div className="app-nav__footer">
                            <button
                                type="button"
                                className="app-nav__signout"
                                onClick={() => {
                                    setOpen(false);
                                    void signOut();
                                }}
                            >
                                Sign out
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </header>
    );
}
