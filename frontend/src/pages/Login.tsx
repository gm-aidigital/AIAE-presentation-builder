import { SignIn } from "@clerk/clerk-react";

// Login — Clerk SSO sign-in (the only auth mode). Clerk renders and manages the
// full sign-in flow; on success it redirects per
// VITE_CLERK_SIGN_IN_FORCE_REDIRECT_URL. There is no mock-login form.

export default function Login() {
    return (
        <main className="login">
            <h1 className="login__title">Sign in</h1>
            <SignIn />
        </main>
    );
}
