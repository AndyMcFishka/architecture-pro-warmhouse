"""Construct the device service and start its HTTP API."""

import json
import os
from pathlib import Path
from flask import Flask
from api import ConnectivityApi
from clients import MonolithClient, MockDeviceAdapter
from operations import OperationHandler
from store import DeliveryStore


def create_app():
    """Compose collaborators explicitly; mocks are limited to the equipment adapter."""
    app = Flask(__name__)
    app.config["MAX_CONTENT_LENGTH"] = 65536
    store = DeliveryStore(os.environ["DATABASE_URL"])
    registry = MonolithClient(os.environ.get("MONOLITH_URL", "http://app:8080"))
    handler = OperationHandler(store, registry, MockDeviceAdapter())
    schemas = json.loads(Path(__file__).with_name("schemas.json").read_text())
    ConnectivityApi(handler, store, schemas).register(app)
    return app


if __name__ == "__main__":
    create_app().run(host="0.0.0.0", port=8080)
