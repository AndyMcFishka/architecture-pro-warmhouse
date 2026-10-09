<?php
require_once __DIR__ . "/CommandStore.php";
require_once __DIR__ . "/ConnectivityClient.php";

/** Gate rules and idempotency without SQL or HTTP details. */
final class GateService
{
    public function __construct(
        private CommandStore $store,
        private ConnectivityClient $devices
    ) {}
    private function repeat(
        Command $command,
        int $device,
        bool $locked
    ): Command {
        ensure(
            $command->deviceId === $device && $command->locked === $locked,
            409
        );
        return $command;
    }
    public function state(int $device): array
    {
        $state = $this->devices->state($device);
        ensure($state["kind"] === "GATE", 422);
        return $state;
    }
    public function getCommand(string $id): Command
    {
        return $this->store->find($id) ?? throw new ApiError(404);
    }
    public function command(int $device, string $key, bool $locked): Command
    {
        $old = $this->store->findByKey($key);
        if ($old) {
            return $this->repeat($old, $device, $locked);
        }
        $this->state($device);
        $command = $this->store->create($device, $key, $locked);
        if (!$command) {
            return $this->repeat(
                $this->store->findByKey($key),
                $device,
                $locked
            );
        }
        $status = "UNKNOWN";
        try {
            $status = $this->devices->send($command);
        } catch (ApiError $error) {
            if (in_array($error->status, [400, 404, 409, 422])) {
                $status = "FAILED";
            }
        }
        return $this->store->saveResult($command->id, $status);
    }
}
