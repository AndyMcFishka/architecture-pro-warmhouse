package services

import (
	"context"
	"errors"
	"github.com/jackc/pgx/v5"
	"net/url"
	"smarthome/db"
	"smarthome/models"
	"strings"
)

// DeviceService validates registration; the registry and HTTP client own their I/O.
type DeviceService struct {
	registry     *db.DeviceRegistry
	connectivity *ConnectivityClient
}

func NewDeviceService(registry *db.DeviceRegistry, connectivity *ConnectivityClient) *DeviceService {
	return &DeviceService{registry, connectivity}
}
func ValidSettings(settings *models.ConnectionSettings) bool {
	if settings == nil || settings.Model != "demo-v1" {
		return false
	}
	address, err := url.Parse(settings.Address)
	return err == nil && address.Scheme == "https" && address.Hostname() != "" && address.User == nil
}
func (s *DeviceService) Get(ctx context.Context, id int) (models.Device, error) {
	device, err := s.registry.Get(ctx, id)
	if errors.Is(err, pgx.ErrNoRows) {
		return device, &APIError{404, "Device not found"}
	}
	return device, err
}
func (s *DeviceService) List(ctx context.Context, filters map[string]string) ([]models.Device, error) {
	return s.registry.List(ctx, filters)
}
func (s *DeviceService) Save(ctx context.Context, id int, input models.DeviceInput) (models.Device, error) {
	for _, value := range []string{input.Name, input.Type, input.Location} {
		if strings.TrimSpace(value) == "" || len(value) > 100 {
			return models.Device{}, &APIError{400, "Invalid device fields"}
		}
	}
	deviceType, err := s.registry.Type(ctx, input.Type)
	if errors.Is(err, pgx.ErrNoRows) {
		return models.Device{}, &APIError{422, "Unsupported type"}
	}
	if err != nil {
		return models.Device{}, err
	}
	validMode := input.ConnectionMode == "LEGACY" || input.ConnectionMode == "HTTPS"
	validTelemetry := input.TelemetryMode == "NONE"
	if deviceType.Kind == "SENSOR" {
		validTelemetry = (input.ConnectionMode == "LEGACY" && input.TelemetryMode == "PULL") || (input.ConnectionMode == "HTTPS" && input.TelemetryMode == "PUSH")
	}
	if !validMode || !validTelemetry || !ValidSettings(input.Settings) {
		return models.Device{}, &APIError{400, "Invalid connection configuration"}
	}
	if id != 0 {
		old, err := s.Get(ctx, id)
		if err != nil {
			return models.Device{}, err
		}
		if input.Settings.CredentialRef == nil && old.Settings != nil {
			input.Settings.CredentialRef = old.Settings.CredentialRef
		}
	}
	if err := s.connectivity.Check(ctx, models.ConnectionCheck{Type: input.Type, ConnectionMode: input.ConnectionMode, Settings: input.Settings}); err != nil {
		return models.Device{}, err
	}
	return s.registry.Save(ctx, id, input, deviceType.Unit)
}
