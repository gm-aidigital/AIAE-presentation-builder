import { useClerk, useUser } from "@clerk/clerk-react";
import { IconAiDigitalLogo } from "./icons";

/** Shared app header — AI Digital logo, product name, user pill, sign out. */
export function TopBar() {
    const { user } = useUser();
    const { signOut } = useClerk();
    const name = user?.fullName ?? user?.primaryEmailAddress?.emailAddress ?? "";

    return (
        <header className="rc-topbar">
            <div className="rc-topbar__brand">
                <IconAiDigitalLogo height={22} />
                <span className="rc-topbar__product">Report Constructor</span>
            </div>
            <div className="rc-topbar__right">
                <span className="rc-topbar__user">
                    <span className="rc-topbar__dot" />
                    {name}
                </span>
                <button type="button" className="rc-topbar__signout" onClick={() => signOut()}>
                    Sign out
                </button>
            </div>
        </header>
    );
}
