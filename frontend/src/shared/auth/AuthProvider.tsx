import { ReactNode, useEffect } from "react";
import { ClerkProvider, useAuth } from "@clerk/clerk-react";
import { runtimeConfig, setSsoTokenGetter } from "../config/runtime";

// AuthProvider — wraps the app in Clerk (SSO is the only auth mode) and bridges
// useAuth().getToken() into runtime.ts so the plain apiClient.onRequest can
// attach the Bearer token.
//
// The token-getter bridge solves a hook-only problem: ClerkProvider's
// useAuth().getToken() can ONLY be called from a React component, but
// apiClient.onRequest runs from a plain function. AuthProvider mounts a tiny
// ClerkTokenBridge component that registers Clerk's getToken with runtime.ts
// via setSsoTokenGetter; runtime.ts exposes it as getAuthToken() to apiClient.

interface Props {
    children: ReactNode;
}

export function AuthProvider({ children }: Props) {
    return (
        <ClerkProvider publishableKey={runtimeConfig.clerkPublishableKey}>
            <ClerkTokenBridge />
            {children}
        </ClerkProvider>
    );
}

function ClerkTokenBridge() {
    const { getToken } = useAuth();
    useEffect(() => {
        setSsoTokenGetter(() => getToken({ template: runtimeConfig.clerkJwtTemplate }));
        return () => setSsoTokenGetter(null);
    }, [getToken]);
    return null;
}
