import React from "react";
import ReactDOM from "react-dom/client";
import { AppRoot } from "./app/AppRoot";
import { runtimeConfig } from "./shared/config/runtime";

import "./shared/ui/base/tokens.css";
import "./shared/ui/base/reset.css";

runtimeConfig.validate();

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <AppRoot />
    </React.StrictMode>,
);
