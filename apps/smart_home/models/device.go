package models

import "time"

// ConnectionSettings describe an adapter, not credentials exposed to clients.
type ConnectionSettings struct {
	Model         string  `json:"model"`
	Address       string  `json:"address"`
	CredentialRef *string `json:"credential_ref,omitempty"`
}
type DeviceInput struct {
	Name           string              `json:"name"`
	Type           string              `json:"type"`
	Location       string              `json:"location"`
	ConnectionMode string              `json:"connection_mode"`
	TelemetryMode  string              `json:"telemetry_mode"`
	Settings       *ConnectionSettings `json:"connection_settings,omitempty"`
}
type Device struct {
	DeviceInput
	ID     int    `json:"id"`
	Kind   string `json:"kind"`
	Metric string `json:"metric,omitempty"`
	Unit   string `json:"unit,omitempty"`
}
type DeviceType struct{ Kind, Metric, Unit string }
type ConnectionCheck struct {
	Type           string              `json:"type"`
	ConnectionMode string              `json:"connection_mode"`
	Settings       *ConnectionSettings `json:"connection_settings"`
}
type LegacyCommand struct {
	Service    string `json:"command_service"`
	ID         string `json:"command_id"`
	Device     int    `json:"device_id"`
	Operation  string `json:"operation"`
	Parameters struct {
		Enabled *bool `json:"enabled"`
	} `json:"parameters"`
}
type DeviceState struct {
	DeviceID   int       `json:"device_id"`
	Kind       string    `json:"kind"`
	State      string    `json:"state"`
	ObservedAt time.Time `json:"observed_at"`
}
type Delivery struct {
	Service   string    `json:"command_service"`
	ID        string    `json:"command_id"`
	DeviceID  int       `json:"device_id"`
	Status    string    `json:"status"`
	UpdatedAt time.Time `json:"updated_at"`
}
type LegacyMeasurement struct {
	DeviceID   int       `json:"device_id"`
	Metric     string    `json:"metric"`
	Value      float64   `json:"value"`
	Unit       string    `json:"unit"`
	MeasuredAt time.Time `json:"measured_at"`
}
