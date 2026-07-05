import { Outlet } from "react-router-dom";
import { AppNav } from "@/shared/ui/AppNav";
import "@/shared/ui/aidigital-tokens.css";
import "./app-layout.css";

/** Shared chrome for every authenticated area: the top nav header + routed page. */
export function AppLayout() {
    return (
        <div className="app-layout">
            <AppNav />
            <main className="app-layout__main">
                <Outlet />
            </main>
        </div>
    );
}
