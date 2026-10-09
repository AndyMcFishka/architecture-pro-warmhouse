"""Registry/legacy HTTP client and the process-local new-equipment adapter."""

import json
from datetime import datetime, timezone
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from errors import ServiceError


class MonolithClient:
    def __init__(self, address, timeout=4):
        self.address = address
        self.timeout = timeout

    def _call(self, path, data=None):
        """Only the configured monolith is contacted, never an input URL."""
        request = Request(
            self.address + path,
            data=None if data is None else json.dumps(data).encode(),
            headers={"Content-Type": "application/json"},
        )
        try:
            with urlopen(request, timeout=self.timeout) as response:
                return json.load(response)
        except HTTPError as error:
            raise ServiceError(
                error.code if error.code in (400, 404, 409, 422) else 503
            ) from error
        except (URLError, TimeoutError, ValueError) as error:
            raise ServiceError(503) from error

    def get_device(self, device_id):
        return self._call(f"/internal/v1/devices/{device_id}")

    def check(self, settings):
        return self._call("/internal/v1/legacy/connections/check", settings)

    def send(self, device, command):
        return self._call("/internal/v1/legacy/commands", command)["status"]

    def state(self, device):
        return self._call(f"/internal/v1/legacy/devices/{device['id']}/state")


class MockDeviceAdapter:
    """Mock only equipment; registry and other services remain real HTTP calls."""

    def __init__(self):
        # state resets with this process; replace with a real protocol adapter later.
        self.states = {}

    def check(self, settings):
        address = urlparse(settings["connection_settings"]["address"])
        if (
            address.scheme != "https"
            or not address.hostname
            or address.username
            or address.password
        ):
            raise ServiceError(400)
        return {"reachable": True, "checked_at": datetime.now(timezone.utc).isoformat()}

    def send(self, device, command):
        if device["kind"] == "GATE":
            state = "LOCKED" if command["parameters"]["locked"] else "UNLOCKED"
        else:
            state = "ON" if command["parameters"]["enabled"] else "OFF"
        self.states[device["id"]] = state
        return "SUCCEEDED"

    def state(self, device):
        default = "UNLOCKED" if device["kind"] == "GATE" else "OFF"
        return {
            "device_id": device["id"],
            "kind": device["kind"],
            "state": self.states.get(device["id"], default),
            "observed_at": datetime.now(timezone.utc).isoformat(),
        }
