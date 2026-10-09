import { createServer } from "node:http";
import pg from "pg";
import { CommandStore } from "./CommandStore.js";
import { ConnectivityClient } from "./ConnectivityClient.js";
import { LightingService } from "./LightingService.js";
import { LightingApi } from "./LightingApi.js";

const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL });
pool.on("error", (error) =>
  console.error("Idle database connection failed", error.message),
);
const store = new CommandStore(pool);
const devices = new ConnectivityClient(
  process.env.CONNECTIVITY_URL ?? "http://connectivity:8080",
);
const api = new LightingApi(new LightingService(store, devices), store);
createServer((request, response) => api.handle(request, response)).listen(
  8080,
  "0.0.0.0",
);
