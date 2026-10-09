import { IncomingMessage, ServerResponse } from "node:http";
import { ApiError, requireValue, uuid } from "./models.js";
import { CommandStore } from "./CommandStore.js";
import { LightingService } from "./LightingService.js";
async function body(req: IncomingMessage) {
  let text = "";
  for await (const c of req) {
    text += c;
    requireValue(text.length <= 65536, 413);
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new ApiError(400);
  }
}

export class LightingApi {
  constructor(
    private readonly service: LightingService,
    private readonly store: CommandStore,
  ) {}
  async handle(req: IncomingMessage, res: ServerResponse) {
    let status = 200;
    let result: unknown;
    try {
      const p = new URL(req.url!, "http://local").pathname;
      let m;
      if (p === "/health" && req.method === "GET") {
        await this.store.checkHealth();
        result = { status: "ok" };
      } else if (
        (m = p.match(/^\/api\/v1\/lighting\/devices\/(\d+)\/(commands|state)$/))
      ) {
        const id = Number(m[1]);
        requireValue(Number.isSafeInteger(id) && id > 0);
        if (m[2] === "state" && req.method === "GET") {
          result = await this.service.state(id);
        } else if (m[2] === "commands" && req.method === "POST") {
          const key = req.headers["idempotency-key"];
          requireValue(typeof key === "string" && uuid.test(key));
          const d = await body(req);
          requireValue(
            d !== null &&
              typeof d === "object" &&
              Object.keys(d).length === 1 &&
              typeof d.enabled === "boolean",
          );
          result = await this.service.command(id, key, d.enabled);
        } else throw new ApiError(405);
      } else if (
        (m = p.match(/^\/api\/v1\/lighting\/commands\/([^/]+)$/)) &&
        req.method === "GET"
      ) {
        requireValue(uuid.test(m[1]));
        result = await this.service.getCommand(m[1]);
      } else throw new ApiError(404);
    } catch (e) {
      status = e instanceof ApiError ? e.status : 503;
      result = {
        error: status === 503 ? "Dependency unavailable" : "Invalid request",
      };
    }
    res.writeHead(status, {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    });
    res.end(JSON.stringify(result));
  }
}
