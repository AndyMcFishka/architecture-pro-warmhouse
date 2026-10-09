"""HTTP routes, schema validation and error translation."""

from uuid import UUID
import psycopg
from flask import request
from jsonschema import Draft4Validator, FormatChecker
from werkzeug.exceptions import HTTPException
from errors import ServiceError


class ConnectivityApi:
    def __init__(self, handler, store, schemas):
        self.handler = handler
        self.store = store
        self.validators = {
            name: Draft4Validator(
                {"$ref": "#/components/schemas/" + name, **schemas},
                format_checker=FormatChecker(),
            )
            for name in ("ConnectionCheck", "DeliveryInput")
        }

    def _body(self, name):
        data = request.get_json()
        if not self.validators[name].is_valid(data):
            raise ServiceError(400, "Invalid request")
        return data

    def register(self, app):
        app.add_url_rule("/health", view_func=self.health)
        app.add_url_rule(
            "/internal/v1/connections/check", view_func=self.check, methods=["POST"]
        )
        app.add_url_rule(
            "/internal/v1/commands", view_func=self.command, methods=["POST"]
        )
        app.add_url_rule(
            "/internal/v1/commands/<name>/<command_id>", view_func=self.receipt
        )
        app.add_url_rule(
            "/internal/v1/devices/<int:device_id>/state", view_func=self.state
        )
        app.register_error_handler(
            ServiceError, lambda error: ({"error": str(error)}, error.status)
        )
        app.register_error_handler(
            HTTPException, lambda error: ({"error": error.description}, error.code)
        )
        app.register_error_handler(
            psycopg.Error, lambda error: ({"error": "Database unavailable"}, 503)
        )

    def health(self):
        self.store.check_health()
        return {"status": "ok"}

    def check(self):
        return self.handler.check(self._body("ConnectionCheck"))

    def command(self):
        return self.handler.deliver(self._body("DeliveryInput"))

    def receipt(self, name, command_id):
        try:
            UUID(command_id)
        except ValueError as error:
            raise ServiceError(400) from error
        return self.handler.receipt(name, command_id)

    def state(self, device_id):
        return self.handler.state(device_id)
