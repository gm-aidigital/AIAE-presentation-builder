// runtime.ts — reads VITE_* env vars at boot, validates them, and exposes a
// single typed runtimeConfig object the rest of the app uses. NEVER read
// import.meta.env directly outside this file.
//
// Auth is Clerk SSO ONLY. The Bearer token comes from Clerk's
// useAuth().getToken(), registered via setSsoTokenGetter() in AuthProvider.
// Only VITE_* vars are exposed to the browser by Vite; anything else is
// either a build-time secret (forbidden) or a server-side env (forbidden in
// the SPA).

interface RuntimeConfig {
    apiBaseUrl: string;
    clerkPublishableKey: string;
    clerkJwtTemplate: string;
    validate(): void;
    getAuthToken(): Promise<string | null>;
    onUnauthorized(): Promise<void>;
}

const env = import.meta.env;

const cfg = {
    // EMPTY default — OpenAPI paths in the spec carry the full `/api/v1/`
    // prefix (see openapi-rules → "/api/v1 prefix"). VITE_API_BASE_URL is only
    // for a different host or servlet context prefix. It must NEVER be `/api/v1`.
    apiBaseUrl: resolveApiBaseUrl(
        (env.VITE_API_BASE_URL ?? "").toString(),
        (env.VITE_API_CONTEXT_PATH ?? "").toString()
    ),
    clerkPublishableKey: (env.VITE_CLERK_PUBLISHABLE_KEY ?? "").toString(),
    clerkJwtTemplate: (env.VITE_CLERK_JWT_TEMPLATE ?? "aidigital-api").toString(),
};

let ssoTokenGetter: (() => Promise<string | null>) | null = null;

export const runtimeConfig: RuntimeConfig = {
    ...cfg,

    validate() {
        if (!cfg.clerkPublishableKey) {
            throw new Error(
                "Clerk SSO is required: set VITE_CLERK_PUBLISHABLE_KEY"
            );
        }
        if (!cfg.clerkJwtTemplate) {
            throw new Error(
                "Clerk JWT template is required: set VITE_CLERK_JWT_TEMPLATE"
            );
        }
    },

    async getAuthToken() {
        // Clerk's useAuth().getToken(), registered via setSsoTokenGetter()
        // inside AuthProvider's ClerkTokenBridge.
        return ssoTokenGetter ? await ssoTokenGetter() : null;
    },

    async onUnauthorized() {
        // Hook for the router to redirect to the sign-in screen. Clerk owns
        // session state, so there is no local token to clear.
    },
};

/** Register Clerk's getToken function from AuthProvider's bridge. */
export function setSsoTokenGetter(g: (() => Promise<string | null>) | null) {
    ssoTokenGetter = g;
}

/**
 * Resolves the same-origin API base without duplicating OpenAPI path prefixes.
 *
 * @param explicitBaseUrl optional host/context prefix, never `/api/v1`.
 * @param contextPath optional Spring servlet context path.
 * @return normalized base URL used by openapi-fetch.
 */
export function resolveApiBaseUrl(
    explicitBaseUrl?: string,
    contextPath?: string
): string {
    const explicit = stripTrailingSlash(explicitBaseUrl?.trim() ?? "");
    if (explicit) return rejectApiVersionPrefix(explicit);

    const context = stripTrailingSlash(contextPath?.trim() ?? "");
    if (context) return rejectApiVersionPrefix(context);

    return "";
}

function rejectApiVersionPrefix(value: string): string {
    if (value === "/api/v1" || value.endsWith("/api/v1")) {
        throw new Error(
            "VITE_API_BASE_URL must not include /api/v1 because OpenAPI paths already include it"
        );
    }
    return value;
}

function stripTrailingSlash(value: string): string {
    return value.endsWith("/") ? value.slice(0, -1) : value;
}
