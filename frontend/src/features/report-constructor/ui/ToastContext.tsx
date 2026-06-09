import {
    createContext,
    useCallback,
    useContext,
    useRef,
    useState,
    type ReactNode,
} from "react";

interface ToastApi {
    showToast(msg: string, error?: boolean): void;
}

const Ctx = createContext<ToastApi | null>(null);

/** Bottom-right toast, matching the original .toast element + showToast(). */
export function ToastProvider({ children }: { children: ReactNode }) {
    const [toast, setToast] = useState({ msg: "", error: false, show: false });
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const showToast = useCallback((msg: string, error = false) => {
        setToast({ msg, error, show: true });
        if (timer.current) clearTimeout(timer.current);
        timer.current = setTimeout(() => setToast((t) => ({ ...t, show: false })), 3200);
    }, []);

    return (
        <Ctx.Provider value={{ showToast }}>
            {children}
            <div className={`toast${toast.show ? " show" : ""}${toast.error ? " error" : ""}`}>
                <div className="toast-dot" />
                <span>{toast.msg}</span>
            </div>
        </Ctx.Provider>
    );
}

export function useToast(): ToastApi {
    const c = useContext(Ctx);
    if (!c) throw new Error("useToast must be used within a ToastProvider");
    return c;
}
