import { NextRequest } from "next/server";

type Context = { params: Promise<{ path: string[] }> };

async function forward(request: NextRequest, context: Context) {
  const { path } = await context.params;
  if (path[0] !== "api" && !(path[0] === "actuator" && path[1] === "health")) {
    return new Response("Not found", { status: 404 });
  }

  const base = process.env.BACKEND_URL ?? "http://localhost:8080";
  const target = new URL(`/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`, base);
  const headers = new Headers();
  const authorization = request.headers.get("authorization");
  if (authorization) headers.set("authorization", authorization);
  if (request.method === "POST") headers.set("content-type", "application/json");

  try {
    const response = await fetch(target, {
      method: request.method,
      headers,
      body: request.method === "POST" ? await request.text() : undefined,
      cache: "no-store",
    });
    return new Response(await response.text(), {
      status: response.status,
      headers: { "content-type": response.headers.get("content-type") ?? "application/json" },
    });
  } catch {
    return Response.json({ code: 0, msg: "后端服务不可用，请先启动 VenuePass 后端。", data: null }, { status: 502 });
  }
}

export async function GET(request: NextRequest, context: Context) {
  return forward(request, context);
}

export async function POST(request: NextRequest, context: Context) {
  return forward(request, context);
}
