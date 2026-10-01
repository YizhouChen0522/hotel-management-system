import "server-only";
import { getBackendBaseUrl } from "@/lib/config/server";
import type { ApiResult } from "@/types/cms";
export class ApiRequestError extends Error { constructor(message: string, readonly status: number) { super(message); } }
export async function serverApiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${getBackendBaseUrl()}${path}`, { cache: "no-store", headers: { Accept: "application/json" } });
  if (!response.ok) throw new ApiRequestError(`Backend request failed: ${path}`, response.status);
  const result = (await response.json()) as ApiResult<T>;
  if (result.code !== 200) throw new ApiRequestError(result.message || "Backend request failed", result.code);
  return result.data;
}
