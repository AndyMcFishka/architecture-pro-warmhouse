"""Persistence of delivery attempts, independent of Flask and device protocols."""

import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb


class DeliveryStore:
    def __init__(self, connection_string):
        self.connection_string = connection_string

    def _open(self):
        return psycopg.connect(self.connection_string, row_factory=dict_row)

    def check_health(self):
        with self._open() as database:
            database.execute("SELECT 1")

    def find(self, service, command_id):
        with self._open() as database:
            return database.execute(
                "SELECT * FROM deliveries WHERE command_service=%s AND command_id=%s",
                (service, command_id),
            ).fetchone()

    def create(self, command):
        """Commit UNKNOWN before the side effect, so a crash cannot cause a resend."""
        with self._open() as database:
            return database.execute(
                "INSERT INTO deliveries(command_service,command_id,device_id,operation,parameters,status) VALUES(%s,%s,%s,%s,%s,'UNKNOWN') RETURNING *",
                (
                    command["command_service"],
                    command["command_id"],
                    command["device_id"],
                    command["operation"],
                    Jsonb(command["parameters"]),
                ),
            ).fetchone()

    def save_result(self, command, status):
        with self._open() as database:
            return database.execute(
                "UPDATE deliveries SET status=%s,updated_at=now() WHERE command_service=%s AND command_id=%s RETURNING *",
                (status, command["command_service"], command["command_id"]),
            ).fetchone()

    @staticmethod
    def receipt(row):
        """Return the public receipt without the stored operation payload."""
        return {
            "command_service": row["command_service"],
            "command_id": str(row["command_id"]),
            "device_id": row["device_id"],
            "status": row["status"],
            "updated_at": row["updated_at"].isoformat(),
        }
