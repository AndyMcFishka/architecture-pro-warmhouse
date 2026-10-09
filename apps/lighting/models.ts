export class ApiError extends Error {
  constructor(public readonly status: number) {
    super("Request failed");
  }
}
export function requireValue(ok: boolean, status = 400): asserts ok {
  if (!ok) throw new ApiError(status);
}
export type CommandStatus =
  | "PENDING"
  | "ACCEPTED"
  | "SUCCEEDED"
  | "FAILED"
  | "UNKNOWN";
export type Command = {
  id: string;
  idempotency_key: string;
  device_id: number;
  enabled: boolean;
  status: CommandStatus;
  created_at: Date;
  updated_at: Date;
};
export type DeviceState = {
  device_id: number;
  kind: string;
  state: string;
  observed_at: string;
};
export const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
