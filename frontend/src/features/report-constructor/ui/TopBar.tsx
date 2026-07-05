import { useClerk, useUser } from "@clerk/clerk-react";
import { useState } from "react";
import { IconAiDigitalLogo, IconInfo } from "./icons";

/** Shared app header — AI Digital logo, product name, user pill, sign out. */
export function TopBar() {
    const { user } = useUser();
    const { signOut } = useClerk();
    const name = user?.fullName ?? user?.primaryEmailAddress?.emailAddress ?? "";

    // Clicking the brand resets the whole flow, so gate it behind a confirm.
    const [confirmReset, setConfirmReset] = useState(false);

    return (
        <header className="rc-topbar">
            <button
                type="button"
                className="rc-topbar__brand"
                title="Start over — reset and go home"
                aria-label="Start over — reset and go home"
                onClick={() => setConfirmReset(true)}
            >
                <IconAiDigitalLogo height={22} />
                <span className="rc-topbar__product">Report Constructor</span>
            </button>
            <div className="rc-topbar__right">
                <span className="rc-topbar__user">
                    <span className="rc-topbar__dot" />
                    {name}
                </span>
                <button type="button" className="rc-topbar__signout" onClick={() => signOut()}>
                    Sign out
                </button>
            </div>

            {confirmReset && (
                <div
                    className="rc-overlay"
                    onClick={(e) => {
                        if (e.target === e.currentTarget) setConfirmReset(false);
                    }}
                >
                    <div className="rc-overlay__card">
                        <div className="rc-overlay__warn">
                            <IconInfo size={20} />
                        </div>
                        <div className="rc-overlay__title">Start over?</div>
                        <div className="rc-overlay__sub">
                            This <b>resets the whole flow</b> and returns to the start — your inputs, mapping and
                            assembled sheet will be cleared. This can't be undone.
                        </div>
                        <div className="rc-overlay__actions">
                            <button
                                type="button"
                                className="rc-btn rc-btn--outline rc-btn--sm"
                                onClick={() => setConfirmReset(false)}
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="rc-btn rc-btn--primary rc-btn--sm"
                                onClick={() => window.location.assign("/")}
                            >
                                Start over
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </header>
    );
}
