import { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "@clerk/clerk-react";
import { LoadingBlock } from "../ui/LoadingBlock";

interface Props {
    children: ReactNode;
}

/** Redirects unauthenticated users to {@code /login}; shows a loading state while Clerk boots. */
export function ProtectedRoute({ children }: Props) {
    const { isLoaded, isSignedIn } = useAuth();

    if (!isLoaded) {
        return <LoadingBlock label="Checking session…" />;
    }
    if (!isSignedIn) {
        return <Navigate to="/login" replace />;
    }
    return <>{children}</>;
}
