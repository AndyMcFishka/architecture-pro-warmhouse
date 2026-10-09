<?php
require_once __DIR__ . "/Command.php";

/** Database access and conversion of rows to commands. */
final class CommandStore
{
    public function __construct(private PDO $database) {}
    private function query(string $sql, array $parameters = []): array|false
    {
        $query = $this->database->prepare($sql);
        $query->execute($parameters);
        return $query->fetch(PDO::FETCH_ASSOC);
    }
    private function read(array $row): Command
    {
        return new Command(
            $row["id"],
            $row["idempotency_key"],
            (int) $row["device_id"],
            in_array($row["desired"], [true, "t", 1], true),
            $row["status"],
            date(DATE_ATOM, strtotime($row["created_at"])),
            date(DATE_ATOM, strtotime($row["updated_at"]))
        );
    }
    public function checkHealth(): void
    {
        $this->query("SELECT 1");
    }
    public function find(string $id): ?Command
    {
        $row = $this->query("SELECT * FROM commands WHERE id=?", [$id]);
        return $row ? $this->read($row) : null;
    }
    public function findByKey(string $key): ?Command
    {
        $row = $this->query("SELECT * FROM commands WHERE idempotency_key=?", [
            $key,
        ]);
        return $row ? $this->read($row) : null;
    }
    public function create(int $device, string $key, bool $locked): ?Command
    {
        $id = vsprintf(
            "%s%s-%s-%s-%s-%s%s%s",
            str_split(bin2hex(random_bytes(16)), 4)
        );
        $row = $this->query(
            "INSERT INTO commands(id,idempotency_key,device_id,desired,status) VALUES(?,?,?,?,'UNKNOWN') ON CONFLICT DO NOTHING RETURNING *",
            [$id, $key, $device, $locked ? "true" : "false"]
        );
        return $row ? $this->read($row) : null;
    }
    public function saveResult(string $id, string $status): Command
    {
        return $this->read(
            $this->query(
                "UPDATE commands SET status=?,updated_at=now() WHERE id=? RETURNING *",
                [$status, $id]
            )
        );
    }
}
