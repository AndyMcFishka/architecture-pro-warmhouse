"""Errors are translated to HTTP only at the API boundary."""


class ServiceError(Exception):
    def __init__(self, status, message="Request failed"):
        super().__init__(message)
        self.status = status
