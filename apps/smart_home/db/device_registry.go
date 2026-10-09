package db

import (
	"context"
	"encoding/json"
	"github.com/jackc/pgx/v5"
	"smarthome/models"
)

// DeviceRegistry is the single owner of registry SQL.
type DeviceRegistry struct{ database *DB }

func NewDeviceRegistry(database *DB) *DeviceRegistry { return &DeviceRegistry{database} }

const deviceColumns = `SELECT s.id,s.name,s.type,s.location,s.connection_mode,s.telemetry_mode,s.connection_settings,t.kind,COALESCE(t.metric,''),COALESCE(t.unit,'') FROM sensors s JOIN device_types t ON t.code=s.type`

func readDevice(row pgx.Row) (models.Device, error) {
	var device models.Device
	var settings []byte
	err := row.Scan(&device.ID, &device.Name, &device.Type, &device.Location, &device.ConnectionMode, &device.TelemetryMode, &settings, &device.Kind, &device.Metric, &device.Unit)
	if err == nil {
		err = json.Unmarshal(settings, &device.Settings)
	}
	return device, err
}
func (r *DeviceRegistry) Get(ctx context.Context, id int) (models.Device, error) {
	return readDevice(r.database.Pool.QueryRow(ctx, deviceColumns+" WHERE s.id=$1", id))
}
func (r *DeviceRegistry) List(ctx context.Context, filters map[string]string) ([]models.Device, error) {
	rows, err := r.database.Pool.Query(ctx, deviceColumns+" ORDER BY s.id")
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	devices := []models.Device{}
	for rows.Next() {
		device, err := readDevice(rows)
		if err != nil {
			return nil, err
		}
		fields := map[string]string{"kind": device.Kind, "type": device.Type, "location": device.Location, "connection_mode": device.ConnectionMode, "telemetry_mode": device.TelemetryMode}
		matches := true
		for key, value := range filters {
			if value != "" && fields[key] != value {
				matches = false
			}
		}
		if matches {
			device.Settings = nil
			devices = append(devices, device)
		}
	}
	return devices, rows.Err()
}
func (r *DeviceRegistry) Type(ctx context.Context, code string) (models.DeviceType, error) {
	var result models.DeviceType
	err := r.database.Pool.QueryRow(ctx, "SELECT kind,COALESCE(metric,''),COALESCE(unit,'') FROM device_types WHERE code=$1", code).Scan(&result.Kind, &result.Metric, &result.Unit)
	return result, err
}
func (r *DeviceRegistry) Save(ctx context.Context, id int, input models.DeviceInput, unit string) (models.Device, error) {
	settings, err := json.Marshal(input.Settings)
	if err != nil {
		return models.Device{}, err
	}
	if id == 0 {
		err = r.database.Pool.QueryRow(ctx, "INSERT INTO sensors(name,type,location,unit,connection_mode,telemetry_mode,connection_settings) VALUES($1,$2,$3,$4,$5,$6,$7) RETURNING id", input.Name, input.Type, input.Location, unit, input.ConnectionMode, input.TelemetryMode, settings).Scan(&id)
	} else {
		_, err = r.database.Pool.Exec(ctx, "UPDATE sensors SET name=$1,type=$2,location=$3,unit=$4,connection_mode=$5,telemetry_mode=$6,connection_settings=$7 WHERE id=$8", input.Name, input.Type, input.Location, unit, input.ConnectionMode, input.TelemetryMode, settings, id)
	}
	if err != nil {
		return models.Device{}, err
	}
	return r.Get(ctx, id)
}
