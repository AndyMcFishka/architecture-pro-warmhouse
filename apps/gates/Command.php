<?php
/** A command receipt is not the current hardware state. */
final readonly class Command implements JsonSerializable
{
    public function __construct(
        public string $id,
        public string $idempotencyKey,
        public int $deviceId,
        public bool $locked,
        public string $status,
        public string $createdAt,
        public string $updatedAt
    ) {}
    public function jsonSerialize(): array
    {
        return [
            "id" => $this->id,
            "idempotency_key" => $this->idempotencyKey,
            "device_id" => $this->deviceId,
            "locked" => $this->locked,
            "status" => $this->status,
            "created_at" => $this->createdAt,
            "updated_at" => $this->updatedAt,
        ];
    }
}
class ApiError extends Exception
{
    public function __construct(public readonly int $status)
    {
        parent::__construct("Request failed");
    }
}
function ensure(bool $ok, int $status = 400): void
{
    if (!$ok) {
        throw new ApiError($status);
    }
}
