<?php
require_once __DIR__ . "/GateService.php";

final class GateApi
{
    public function __construct(
        private GateService $service,
        private CommandStore $store
    ) {}
    private function validUuid($value): bool
    {
        return is_string($value) &&
            (bool) preg_match(
                '/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i',
                $value
            );
    }
    public function handle(): array|Command
    {
        $path = parse_url($_SERVER["REQUEST_URI"], PHP_URL_PATH);
        $method = $_SERVER["REQUEST_METHOD"];
        if ($path === "/health" && $method === "GET") {
            $this->store->checkHealth();
            $out = ["status" => "ok"];
        } elseif (
            preg_match(
                '#^/api/v1/gates/devices/(\d+)/(commands|state)$#',
                $path,
                $m
            )
        ) {
            $id = (int) $m[1];
            ensure($id > 0);
            if ($m[2] === "state" && $method === "GET") {
                $out = $this->service->state($id);
            } elseif ($m[2] === "commands" && $method === "POST") {
                $key = $_SERVER["HTTP_IDEMPOTENCY_KEY"] ?? "";
                ensure($this->validUuid($key));
                $raw = file_get_contents("php://input", false, null, 0, 65537);
                ensure(strlen($raw) <= 65536, 413);
                $d = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
                ensure(
                    is_array($d) &&
                        count($d) === 1 &&
                        array_key_exists("locked", $d) &&
                        is_bool($d["locked"])
                );
                $out = $this->service->command($id, $key, $d["locked"]);
            } else {
                throw new ApiError(405);
            }
        } elseif (
            preg_match('#^/api/v1/gates/commands/([^/]+)$#', $path, $m) &&
            $method === "GET"
        ) {
            ensure($this->validUuid($m[1]));
            $out = $this->service->getCommand($m[1]);
        } else {
            throw new ApiError(404);
        }
        return $out;
    }
}
