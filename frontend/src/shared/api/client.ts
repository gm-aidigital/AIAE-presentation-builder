import createClient from "openapi-fetch";
import type { paths } from "./generated/schema";
import { runtimeConfig } from "../config/runtime";

// Auth-aware typed client. The Authorization header is added by middleware
// using the Bearer token from Clerk (useAuth().getToken(), bridged into
// runtime.ts by AuthProvider). Clerk SSO is the only auth mode.

// OpenAPI path keys already include `/api/v1/...`; apiBaseUrl is only a host
// or servlet context prefix. Never set it to `/api/v1` or calls become
// `/api/v1/api/v1/...`.
const baseUrl = runtimeConfig.apiBaseUrl;

export const apiClient = createClient<paths>({ baseUrl });

apiClient.use({
    async onRequest({ request }) {
        const token = await runtimeConfig.getAuthToken();
        if (token) request.headers.set("Authorization", `Bearer ${token}`);
        return request;
    },
    async onResponse({ response }) {
        if (response.status === 401) {
            await runtimeConfig.onUnauthorized();
        }
        return response;
    },
});
