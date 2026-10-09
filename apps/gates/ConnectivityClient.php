<?php
require_once __DIR__ . "/Command.php";

final class ConnectivityClient
{
    public function __construct(private string $address) {}
    private function call(string $path, ?array $body = null): array
    {
        $curl = curl_init($this->address . $path);
        curl_setopt_array($curl, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 5,
            CURLOPT_HTTPHEADER => ["Content-Type: application/json"],
        ]);
        if ($body !== null) {
            curl_setopt_array($curl, [
                CURLOPT_POST => true,
                CURLOPT_POSTFIELDS => json_encode($body, JSON_THROW_ON_ERROR),
            ]);
        }
        $raw = curl_exec($curl);
        $status = curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        curl_close($curl);
        ensure(
            $raw !== false && $status >= 200 && $status < 300,
            $status ?: 503
        );
        return json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
    }
    public function state(int $device): array
    {
        return $this->call("/internal/v1/devices/" . $device . "/state");
    }
    public function send(Command $command): string
    {
        return $this->call("/internal/v1/commands", [
            "command_service" => "gates",
            "command_id" => $command->id,
            "device_id" => $command->deviceId,
            "operation" => "SET_GATE_LOCK",
            "parameters" => ["locked" => $command->locked],
        ])["status"];
    }
}
