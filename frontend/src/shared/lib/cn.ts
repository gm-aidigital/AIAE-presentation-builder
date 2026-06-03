// cn — tiny conditional className builder for BEM modifiers.
// Usage:
//   cn("user-card", featured && "user-card--featured", size && `user-card--size-${size}`)

export function cn(...parts: Array<string | false | null | undefined>): string {
    return parts.filter(Boolean).join(" ");
}
