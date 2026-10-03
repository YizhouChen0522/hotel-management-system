import { getBackendBaseUrl } from "@/lib/config/server";

type RouteContext = { params: Promise<{ path: string[] }> };

const requestHeaders = [
  "accept",
  "content-type",
  "authorization",
  "idempotency-key",
  "range",
] as const;

const responseHeaders = [
  "content-type",
  "content-disposition",
  "cache-control",
  "etag",
  "last-modified",
  "accept-ranges",
  "content-range",
] as const;

async function proxy(request: Request, context: RouteContext): Promise<Response> {
  const { path } = await context.params;
  const incomingUrl = new URL(request.url);
  const backendPath = path.map(encodeURIComponent).join("/");
  const backendUrl = `${getBackendBaseUrl()}/api/${backendPath}${incomingUrl.search}`;
  const headers = new Headers();

  for (const name of requestHeaders) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const backendResponse = await fetch(backendUrl, {
    method: request.method,
    headers,
    body: hasBody ? await request.arrayBuffer() : undefined,
    cache: "no-store",
    redirect: "manual",
  });
  const outgoingHeaders = new Headers();

  for (const name of responseHeaders) {
    const value = backendResponse.headers.get(name);
    if (value) outgoingHeaders.set(name, value);
  }

  return new Response(backendResponse.body, {
    status: backendResponse.status,
    statusText: backendResponse.statusText,
    headers: outgoingHeaders,
  });
}

export const dynamic = "force-dynamic";

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
export const HEAD = proxy;

export function OPTIONS(): Response {
  return new Response(null, {
    status: 204,
    headers: { Allow: "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS" },
  });
}
