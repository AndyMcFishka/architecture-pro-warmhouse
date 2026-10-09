"""Choose a protocol adapter and enforce command idempotency."""

from threading import Lock
from errors import ServiceError


class OperationHandler:
    KINDS = {"heating": "HEATING", "lighting": "LIGHT", "gates": "GATE"}

    def __init__(self, store, monolith, equipment):
        self.store = store
        self.monolith = monolith
        self.equipment = equipment
        # one replica; use database claims before running multiple delivery workers.
        self.lock = Lock()

    def _adapter(self, device):
        return (
            self.monolith if device["connection_mode"] == "LEGACY" else self.equipment
        )

    def check(self, settings):
        if (
            settings["type"]
            not in ("temperature", "heating", "lighting", "gate", "camera")
            or settings["connection_settings"]["model"] != "demo-v1"
        ):
            raise ServiceError(422)
        return self._adapter(settings).check(settings)

    def deliver(self, command):
        with self.lock:
            old = self.store.find(command["command_service"], command["command_id"])
            if old:
                if any(
                    old[key] != command[key]
                    for key in ("device_id", "operation", "parameters")
                ):
                    raise ServiceError(409)
                return self.store.receipt(old)
            device = self.monolith.get_device(command["device_id"])
            if device["kind"] != self.KINDS[command["command_service"]]:
                raise ServiceError(422)
            self.store.create(command)
            try:
                status = self._adapter(device).send(device, command)
            except ServiceError:
                status = "UNKNOWN"
            return self.store.receipt(self.store.save_result(command, status))

    def receipt(self, service, command_id):
        row = self.store.find(service, command_id)
        if not row:
            raise ServiceError(404)
        return self.store.receipt(row)

    def state(self, device_id):
        device = self.monolith.get_device(device_id)
        if device["kind"] not in self.KINDS.values():
            raise ServiceError(422)
        with self.lock:
            return self._adapter(device).state(device)
