import "server-only";
const DEFAULT_BACKEND_URL = "http://localhost:8080";
export function getBackendBaseUrl(): string { return (process.env.BACKEND_BASE_URL?.trim() || DEFAULT_BACKEND_URL).replace(/\/$/, ""); }
