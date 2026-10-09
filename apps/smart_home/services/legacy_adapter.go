package services

import (
	"smarthome/models"
	"strconv"
	"sync"
	"time"
)

type legacyReceipt struct {
	device  int
	enabled bool
	result  models.Delivery
}

// LegacyAdapter never calls connectivity or telemetry; it owns the old-equipment mock.
type LegacyAdapter struct {
	temperature *TemperatureService
	mu          sync.Mutex
	states      map[int]string
	deliveries  map[string]legacyReceipt
}

func NewLegacyAdapter(address string) *LegacyAdapter {
	return &LegacyAdapter{temperature: NewTemperatureService(address), states: map[int]string{}, deliveries: map[string]legacyReceipt{}}
}
func (a *LegacyAdapter) Check(input models.ConnectionCheck) error {
	if input.ConnectionMode != "LEGACY" || !ValidSettings(input.Settings) {
		return &APIError{400, "Invalid legacy settings"}
	}
	if input.Type != "temperature" && input.Type != "heating" {
		return &APIError{422, "Unsupported legacy type"}
	}
	return nil
}
func (a *LegacyAdapter) Measure(device models.Device) (models.LegacyMeasurement, error) {
	if device.Kind != "SENSOR" || device.ConnectionMode != "LEGACY" {
		return models.LegacyMeasurement{}, &APIError{422, "Not a legacy sensor"}
	}
	reading, err := a.temperature.GetTemperatureByID(strconv.Itoa(device.ID))
	if err != nil {
		return models.LegacyMeasurement{}, err
	}
	return models.LegacyMeasurement{DeviceID: device.ID, Metric: device.Metric, Value: reading.Value, Unit: device.Unit, MeasuredAt: reading.Timestamp}, nil
}
func (a *LegacyAdapter) State(device models.Device) (models.DeviceState, error) {
	if device.Kind != "HEATING" || device.ConnectionMode != "LEGACY" {
		return models.DeviceState{}, &APIError{422, "Not legacy heating"}
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	state := a.states[device.ID]
	if state == "" {
		state = "OFF"
	}
	return models.DeviceState{DeviceID: device.ID, Kind: device.Kind, State: state, ObservedAt: time.Now().UTC()}, nil
}
func (a *LegacyAdapter) Send(device models.Device, command models.LegacyCommand) (models.Delivery, error) {
	if command.Service != "heating" || command.Operation != "SET_HEATING" || command.Parameters.Enabled == nil || len(command.ID) != 36 {
		return models.Delivery{}, &APIError{400, "Invalid command"}
	}
	if device.Kind != "HEATING" || device.ConnectionMode != "LEGACY" {
		return models.Delivery{}, &APIError{422, "Not legacy heating"}
	}
	// one process stores mock state; real equipment supplies state after restart.
	a.mu.Lock()
	defer a.mu.Unlock()
	key := command.Service + ":" + command.ID
	enabled := *command.Parameters.Enabled
	if old, ok := a.deliveries[key]; ok {
		if old.device != device.ID || old.enabled != enabled {
			return models.Delivery{}, &APIError{409, "Command conflict"}
		}
		return old.result, nil
	}
	state := "OFF"
	if enabled {
		state = "ON"
	}
	a.states[device.ID] = state
	result := models.Delivery{Service: command.Service, ID: command.ID, DeviceID: device.ID, Status: "SUCCEEDED", UpdatedAt: time.Now().UTC()}
	a.deliveries[key] = legacyReceipt{device.ID, enabled, result}
	return result, nil
}
