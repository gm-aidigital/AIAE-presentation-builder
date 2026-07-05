import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Login from "../pages/Login";
import { AuthProvider } from "../shared/auth/AuthProvider";
import { ProtectedRoute } from "../shared/auth/ProtectedRoute";
import { AdminRoute } from "../shared/auth/AdminRoute";
import { ReportConstructorPage } from "../features/report-constructor";
import { MyReportsPage } from "../features/my-reports";
import { AdminDashboardPage } from "../features/admin-dashboard";
import { AppLayout } from "./AppLayout";

const queryClient = new QueryClient({
    defaultOptions: {
        queries: { staleTime: 30_000, refetchOnWindowFocus: false },
    },
});

/** Router + providers — main.tsx mounts only this component. */
export function AppRoot() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <QueryClientProvider client={queryClient}>
                    <Routes>
                        <Route path="/login" element={<Login />} />
                        <Route
                            element={
                                <ProtectedRoute>
                                    <AppLayout />
                                </ProtectedRoute>
                            }
                        >
                            <Route index element={<ReportConstructorPage />} />
                            <Route path="reports/new" element={<ReportConstructorPage />} />
                            <Route path="reports" element={<MyReportsPage />} />
                            <Route
                                path="admin"
                                element={
                                    <AdminRoute>
                                        <AdminDashboardPage />
                                    </AdminRoute>
                                }
                            />
                            <Route path="*" element={<Navigate to="/" replace />} />
                        </Route>
                    </Routes>
                </QueryClientProvider>
            </AuthProvider>
        </BrowserRouter>
    );
}
