import { LoadingBlock } from "@/shared/ui/LoadingBlock";
import { ErrorAlert } from "@/shared/ui/ErrorAlert";
import { EmptyState } from "@/shared/ui/EmptyState";
import { useAuthMeQuery } from "../api/useAuthMeQuery";
import { useRefreshSessionMutation } from "../api/useRefreshSessionMutation";
import "./template-profile.css";

/** Example feature panel — query + mutation + UI states. Copy to features/<name>/ui/ */
export function TemplateProfilePanel() {
    const { data, isLoading, isError, error } = useAuthMeQuery();
    const refresh = useRefreshSessionMutation();

    if (isLoading) return <LoadingBlock label="Loading profile…" />;
    if (isError) return <ErrorAlert message={`Request failed (${String(error)})`} />;
    if (!data) return <EmptyState message="No profile returned." />;

    return (
        <div className="template-profile">
            <p className="template-profile__text">
                Signed in as <strong>{data.email}</strong>
            </p>
            <button
                type="button"
                className="template-profile__refresh"
                disabled={refresh.isPending}
                onClick={() => refresh.mutate()}
            >
                {refresh.isPending ? "Refreshing…" : "Refresh profile"}
            </button>
        </div>
    );
}
