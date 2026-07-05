import { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { LoadingBlock } from "../ui/LoadingBlock";
import { useCurrentUser } from "./useCurrentUser";

interface Props {
    children: ReactNode;
}

/**
 * Gates admin-only routes on the server-computed `is_admin` flag from /auth/me.
 * Non-admins (and failed lookups) are redirected to My reports; the backend
 * independently returns 403 for the /admin API, so this is defense in depth.
 */
export function AdminRoute({ children }: Props) {
    const { data, isLoading, isError } = useCurrentUser();

    if (isLoading) {
        return <LoadingBlock label="Checking access…" />;
    }
    if (isError || !data?.is_admin) {
        return <Navigate to="/reports" replace />;
    }
    return <>{children}</>;
}
