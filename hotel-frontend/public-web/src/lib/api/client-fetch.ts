import type { ApiResult } from "@/types/booking";

export class ClientApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: number,
  ) {
    super(message);
  }
}

export async function clientApi<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(path, { ...options, headers, cache: "no-store" });
  let result: ApiResult<T> | null = null;
  try {
    result = (await response.json()) as ApiResult<T>;
  } catch {
    // A gateway/network error may not use the backend Result envelope.
  }
  if (!response.ok || !result || result.code !== 200) {
    throw new ClientApiError(result?.message || `请求失败 (${response.status})`, response.status, result?.code || response.status);
  }
  return result.data;
}

export function jsonBody(value: unknown): Pick<RequestInit, "body" | "headers"> {
  return { body: JSON.stringify(value), headers: { "Content-Type": "application/json" } };
}

export function requestKey(prefix: string): string {
  return `${prefix}_${crypto.randomUUID().replaceAll("-", "")}`.slice(0, 64);
}
