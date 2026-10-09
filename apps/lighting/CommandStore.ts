import pg from "pg";
import { randomUUID } from "node:crypto";
import { Command, CommandStatus } from "./models.js";

type Row = Omit<Command, "enabled"> & { desired: boolean };
/** SQL and row mapping stay here, outside HTTP and lighting decisions. */
export class CommandStore {
  constructor(private readonly pool: pg.Pool) {}
  private view(row: Row): Command {
    const { desired, ...command } = row;
    return { ...command, enabled: desired };
  }
  async checkHealth() {
    await this.pool.query("SELECT 1");
  }
  async findByKey(key: string): Promise<Command | undefined> {
    const result = await this.pool.query<Row>(
      "SELECT * FROM commands WHERE idempotency_key=$1",
      [key],
    );
    return result.rows[0] && this.view(result.rows[0]);
  }
  async find(id: string): Promise<Command | undefined> {
    const result = await this.pool.query<Row>(
      "SELECT * FROM commands WHERE id=$1",
      [id],
    );
    return result.rows[0] && this.view(result.rows[0]);
  }
  async create(
    device: number,
    key: string,
    enabled: boolean,
  ): Promise<Command | undefined> {
    const result = await this.pool.query<Row>(
      "INSERT INTO commands(id,idempotency_key,device_id,desired,status) VALUES($1,$2,$3,$4,'UNKNOWN') ON CONFLICT DO NOTHING RETURNING *",
      [randomUUID(), key, device, enabled],
    );
    return result.rows[0] && this.view(result.rows[0]);
  }
  async saveResult(id: string, status: CommandStatus): Promise<Command> {
    const result = await this.pool.query<Row>(
      "UPDATE commands SET status=$1,updated_at=now() WHERE id=$2 RETURNING *",
      [status, id],
    );
    return this.view(result.rows[0]);
  }
}
