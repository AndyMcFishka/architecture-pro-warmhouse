import { ApiError, Command, CommandStatus, DeviceState } from "./models.js";

/** HTTP details of the device connectivity contract. */
export class ConnectivityClient {
  constructor(private readonly address: string) {}
  private async call<T>(path: string, body?: unknown): Promise<T> {
    const response = await fetch(this.address + path, {
      method: body ? "POST" : "GET",
      headers: { "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) throw new ApiError(response.status);
    return (await response.json()) as T;
  }
  state(device: number): Promise<DeviceState> {
    return this.call(`/internal/v1/devices/${device}/state`);
  }
  async send(command: Command): Promise<CommandStatus> {
    const result = await this.call<{ status: CommandStatus }>(
      "/internal/v1/commands",
      {
        command_service: "lighting",
        command_id: command.id,
        device_id: command.device_id,
        operation: "SET_LIGHT",
        parameters: { enabled: command.enabled },
      },
    );
    return result.status;
  }
}
