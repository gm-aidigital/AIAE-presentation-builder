import React from "react";
import ReactDOM from "react-dom/client";

// Elevate base first (used by the Login/Clerk page). The Report Constructor
// stylesheet is imported via AppRoot AFTER these, so its verbatim original
// styling wins on the authenticated app surface.
import "./shared/ui/base/tokens.css";
import "./shared/ui/base/reset.css";

import { AppRoot } from "./app/AppRoot";
import { runtimeConfig } from "./shared/config/runtime";

runtimeConfig.validate();

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <AppRoot />
    </React.StrictMode>,
);
