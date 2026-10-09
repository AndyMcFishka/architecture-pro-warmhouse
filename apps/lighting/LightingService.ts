import { CommandStore } from "./CommandStore.js";
import { ConnectivityClient } from "./ConnectivityClient.js";
import { ApiError, Command, CommandStatus, requireValue } from "./models.js";

/** Lighting commands and idempotency, without SQL or HTTP mechanics. */
export class LightingService {
  constructor(
    private readonly store: CommandStore,
    private readonly devices: ConnectivityClient,
  ) {}
  private repeat(command: Command, device: number, enabled: boolean): Command {
    requireValue(
      command.device_id === device && command.enabled === enabled,
      409,
    );
    return command;
  }
  async state(device: number) {
    const state = await this.devices.state(device);
    requireValue(state.kind === "LIGHT", 422);
    return state;
  }
  async getCommand(id: string) {
    const command = await this.store.find(id);
    requireValue(!!command, 404);
    return command;
  }
  async command(device: number, key: string, enabled: boolean) {
    const old = await this.store.findByKey(key);
    if (old) return this.repeat(old, device, enabled);
    await this.state(device);
    const command = await this.store.create(device, key, enabled);
    if (!command)
      return this.repeat((await this.store.findByKey(key))!, device, enabled);
    let status: CommandStatus = "UNKNOWN";
    try {
      status = await this.devices.send(command);
    } catch (error) {
      if (
        error instanceof ApiError &&
        [400, 404, 409, 422].includes(error.status)
      )
        status = "FAILED";
    }
    return this.store.saveResult(command.id, status);
  }
}
