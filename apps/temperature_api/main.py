"""Minimal Flask API that simulates temperature sensors for the monolith."""

import random
from datetime import datetime, timezone
from threading import Lock

from flask import Flask, abort, request
from werkzeug.exceptions import HTTPException

app = Flask(__name__)
LOCATIONS = {"1": "Living Room", "2": "Bedroom", "3": "Kitchen"}
last_value = None
values_lock = Lock()


def next_temperature():
    """Generate a float in [-273, 1000] different from the previous reading."""
    # ponytail: one process and an in-memory history; share state if adding workers.
    global last_value
    with values_lock:
        value = random.uniform(-273, 1000)
        while value == last_value:
            value = random.uniform(-273, 1000)
        last_value = value
        return value


@app.get("/health")
def health():
    """Check availability without generating a reading or changing its history."""
    return {"status": "ok"}


@app.get("/temperature")
@app.get("/temperature/<sensor_id>")
def temperature(sensor_id=None):
    """Return a fresh random reading for a sensor ID or location."""
    sensor_id = sensor_id or request.args.get("sensorId", "")
    if sensor_id and (not sensor_id.isascii() or not sensor_id.isdecimal()):
        abort(400, description="Sensor ID must contain only digits")
    location = request.args.get("location") or LOCATIONS.get(sensor_id, "Unknown")
    sensor_id = sensor_id or next(
        (key for key, value in LOCATIONS.items() if value == location), "0"
    )
    return {
        "value": next_temperature(),
        "unit": "°C",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "location": location,
        "status": "active",
        "sensor_id": sensor_id,
        "sensor_type": "temperature",
        "description": "Simulated temperature reading",
    }


@app.errorhandler(HTTPException)
def http_error(error):
    """Return HTTP errors as JSON instead of an HTML page."""
    response = error.get_response()
    response.data = app.json.dumps({"error": error.description})
    response.content_type = "application/json"
    return response


@app.after_request
def disable_cache(response):
    """Prevent clients from caching simulated readings or errors."""
    response.headers["Cache-Control"] = "no-store"
    return response


if __name__ == "__main__":
    # Flask's demo server; use a production WSGI server for public deployment.
    app.run(host="0.0.0.0", port=8081)
