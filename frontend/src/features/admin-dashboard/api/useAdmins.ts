import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

const ADMINS_KEY = ["admin", "admins"];

/** GET /api/v1/admin/admins — the current admins (config + UI-managed). Admin only. */
export function useAdmins(enabled = true) {
    return useQuery({
        queryKey: ADMINS_KEY,
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/admin/admins");
            if (error) throw error;
            return data;
        },
        enabled,
        staleTime: 30_000,
        retry: false,
    });
}

/** POST /api/v1/admin/admins — grant admin access by email; refreshes the list. */
export function useAddAdmin() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: async (email: string) => {
            const { data, error } = await apiClient.POST("/api/v1/admin/admins", { body: { email } });
            if (error) throw error;
            return data;
        },
        onSuccess: (data) => {
            if (data) qc.setQueryData(ADMINS_KEY, data);
            void qc.invalidateQueries({ queryKey: ADMINS_KEY });
        },
    });
}

/** DELETE /api/v1/admin/admins/{email} — revoke a managed admin; refreshes the list. */
export function useRemoveAdmin() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: async (email: string) => {
            const { data, error } = await apiClient.DELETE("/api/v1/admin/admins/{email}", {
                params: { path: { email } },
            });
            if (error) throw error;
            return data;
        },
        onSuccess: (data) => {
            if (data) qc.setQueryData(ADMINS_KEY, data);
            void qc.invalidateQueries({ queryKey: ADMINS_KEY });
        },
    });
}
