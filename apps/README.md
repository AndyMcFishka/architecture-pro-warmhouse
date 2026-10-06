# Smart Home Sensor Management API

## Prerequisites

- Docker and Docker Compose

## Getting Started

### Option 1: Using Docker Compose (Recommended)

The easiest way to start the application is to use Docker Compose:

```bash
./init.sh
```

This script will:

1. Build and start the PostgreSQL and application containers
2. Wait for the services to be ready
3. Display information about how to access the API

Alternatively, you can run Docker Compose directly:

```bash
docker compose up --build -d
```

The API will be available at http://localhost:8080

The Go temperature simulator is available at
`http://localhost:8081/temperature?location=Living%20Room` and
`http://localhost:8081/temperature/1`.
Run `Create Sensor` in the Postman collection, then repeat `Get All Sensors`
to see fresh random temperature readings.

### Run only the temperature simulator without Docker

Stop the Compose temperature service first if port 8081 is already in use.
From the `apps` directory:

```bash
cd temperature-api
go test -race ./...
go run .
```

The simulator uses only the Go standard library and requires Go 1.22 or newer.
PostgreSQL and the monolith can be started together using the Compose command above.

## API Testing

A Postman collection is provided for testing the API. Import the `smarthome-api.postman_collection.json` file into Postman to get started.

## API Endpoints

- `GET /health` - Health check
- `GET /api/v1/sensors` - Get all sensors
- `GET /api/v1/sensors/:id` - Get a specific sensor
- `POST /api/v1/sensors` - Create a new sensor
- `PUT /api/v1/sensors/:id` - Update a sensor
- `DELETE /api/v1/sensors/:id` - Delete a sensor
- `PATCH /api/v1/sensors/:id/value` - Update a sensor's value and status
