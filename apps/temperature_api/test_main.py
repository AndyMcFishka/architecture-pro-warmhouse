"""Check the temperature API contract and inclusive random bounds."""

import unittest
from datetime import datetime
from unittest.mock import patch

import main
from main import LOCATIONS, app


class TemperatureTest(unittest.TestCase):
    """Exercise Flask routes without starting a server."""

    def setUp(self):
        """Start each test with an empty reading history."""
        main.last_value = None

    def test_repeated_readings(self):
        """Skip repeated readings across lookup routes and different sensors."""
        client = app.test_client()
        with patch("main.random.uniform", side_effect=[21.5, 21.5, -273.0, -273.0, 5500.0]) as rng:
            first = client.get("/temperature/1").get_json()["value"]
            self.assertEqual(client.get("/health").get_json(), {"status": "ok"})
            rng.assert_called_once()
            second = client.get("/temperature?location=Living%20Room").get_json()["value"]
            other = client.get("/temperature/2").get_json()["value"]
        self.assertEqual((first, second, other), (21.5, -273.0, 5500.0))
        self.assertNotEqual(first, second)
        self.assertEqual(rng.call_count, 5)

    def test_http_contract(self):
        """Cover both lookup routes, defaults, errors and both random bounds."""
        app.testing = True
        client = app.test_client()
        for sensor_id, location in LOCATIONS.items():
            paths = [f"/temperature/{sensor_id}",
                     "/temperature?location=" + location.replace(" ", "%20")]
            for path in paths:
                for bound in [-273.0, 5500.0]:
                    with patch("main.random.uniform", return_value=bound) as random_value:
                        response = client.get(path)
                    random_value.assert_called_once_with(-273, 5500)
                    self.assertEqual(response.status_code, 200)
                    self.assertEqual(response.headers["Cache-Control"], "no-store")
                    data = response.get_json()
                    self.assertEqual((data["value"], data["sensor_id"], data["location"]),
                                     (bound, sensor_id, location))
                    self.assertEqual((data["unit"], data["status"], data["sensor_type"]),
                                     ("°C", "active", "temperature"))
                    self.assertIsNotNone(datetime.fromisoformat(data["timestamp"]).tzinfo)
        for path, sensor_id, location in [
            ("/temperature", "0", "Unknown"),
            ("/temperature?location=Office", "0", "Office"),
            ("/temperature/99", "99", "Unknown"),
            ("/temperature?sensorId=2", "2", "Bedroom"),
        ]:
            response = client.get(path)
            self.assertEqual(response.status_code, 200)
            data = response.get_json()
            self.assertEqual((data["sensor_id"], data["location"]), (sensor_id, location))
            self.assertIsInstance(data["value"], float)
            self.assertTrue(-273 <= data["value"] <= 5500)
        for path, status in [("/missing", 404), ("/temperature/abc", 400),
                             ("/temperature/", 404), ("/temperature?sensorId=-1", 400)]:
            response = client.get(path)
            self.assertEqual(response.status_code, status)
            self.assertIn("error", response.get_json())


if __name__ == "__main__":
    unittest.main()
