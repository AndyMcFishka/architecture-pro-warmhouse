<?php
require_once __DIR__ . "/GateApi.php";

header("Content-Type: application/json");
header("Cache-Control: no-store");
try {
    $database = new PDO(getenv("DATABASE_DSN"), "gates", "gates", [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    ]);
    $store = new CommandStore($database);
    $devices = new ConnectivityClient(
        getenv("CONNECTIVITY_URL") ?: "http://connectivity:8080"
    );
    $api = new GateApi(new GateService($store, $devices), $store);
    echo json_encode($api->handle(), JSON_THROW_ON_ERROR);
} catch (ApiError $error) {
    http_response_code($error->status);
    echo json_encode(["error" => $error->getMessage()]);
} catch (JsonException $error) {
    http_response_code(400);
    echo json_encode(["error" => "Invalid JSON"]);
} catch (Throwable $error) {
    http_response_code(503);
    echo json_encode(["error" => "Dependency unavailable"]);
}
