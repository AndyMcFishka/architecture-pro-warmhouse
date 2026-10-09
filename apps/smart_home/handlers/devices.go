package handlers

import (
	"encoding/json"
	"errors"
	"github.com/gin-gonic/gin"
	"io"
	"net/http"
	"smarthome/db"
	"smarthome/models"
	"smarthome/services"
	"strconv"
	"strings"
	"time"
)

// DeviceHandler is the HTTP boundary for registration and internal equipment operations.
type DeviceHandler struct {
	devices *services.DeviceService
	legacy  *services.LegacyAdapter
}

func RegisterDevices(router *gin.Engine, database *db.DB, legacyURL, connectivityURL string) {
	registry := db.NewDeviceRegistry(database)
	handler := &DeviceHandler{services.NewDeviceService(registry, services.NewConnectivityClient(connectivityURL)), services.NewLegacyAdapter(legacyURL)}
	router.GET("/api/v1/devices", handler.list)
	router.POST("/api/v1/devices", handler.save)
	router.PUT("/api/v1/devices/:deviceId", handler.save)
	router.GET("/api/v1/devices/:deviceId", handler.get)
	router.GET("/internal/v1/devices/:deviceId", handler.get)
	router.GET("/internal/v1/devices", handler.list)
	router.GET("/internal/v1/legacy/devices/:deviceId/measurement", handler.measure)
	router.POST("/internal/v1/legacy/connections/check", handler.check)
	router.POST("/internal/v1/legacy/commands", handler.command)
	router.GET("/internal/v1/legacy/devices/:deviceId/state", handler.state)
}
func deviceID(c *gin.Context) int {
	id, err := strconv.Atoi(c.Param("deviceId"))
	if err != nil || id < 1 {
		c.AbortWithStatusJSON(400, gin.H{"error": "Invalid device ID"})
		return 0
	}
	return id
}
func deviceError(c *gin.Context, err error) {
	status := 503
	message := "Dependency unavailable"
	var apiError *services.APIError
	if errors.As(err, &apiError) {
		status = apiError.Status
		message = apiError.Message
	}
	c.JSON(status, gin.H{"error": message})
}
func deviceBody(c *gin.Context, target any) bool {
	decoder := json.NewDecoder(http.MaxBytesReader(c.Writer, c.Request.Body, 65536))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		c.JSON(400, gin.H{"error": "Invalid JSON"})
		return false
	}
	if decoder.Decode(new(any)) != io.EOF {
		c.JSON(400, gin.H{"error": "Expected one JSON object"})
		return false
	}
	return true
}
func (h *DeviceHandler) get(c *gin.Context) {
	id := deviceID(c)
	if id == 0 {
		return
	}
	device, err := h.devices.Get(c.Request.Context(), id)
	if err != nil {
		deviceError(c, err)
		return
	}
	if !strings.HasPrefix(c.Request.URL.Path, "/internal/") && device.Settings != nil {
		device.Settings.CredentialRef = nil
	}
	c.JSON(200, device)
}
func (h *DeviceHandler) list(c *gin.Context) {
	filters := map[string]string{}
	for _, key := range []string{"kind", "type", "location", "connection_mode", "telemetry_mode"} {
		filters[key] = c.Query(key)
	}
	devices, err := h.devices.List(c.Request.Context(), filters)
	if err != nil {
		deviceError(c, err)
		return
	}
	c.JSON(200, devices)
}
func (h *DeviceHandler) save(c *gin.Context) {
	var input models.DeviceInput
	if !deviceBody(c, &input) {
		return
	}
	id := 0
	if c.Request.Method == http.MethodPut {
		id = deviceID(c)
		if id == 0 {
			return
		}
	}
	device, err := h.devices.Save(c.Request.Context(), id, input)
	if err != nil {
		deviceError(c, err)
		return
	}
	device.Settings = nil
	status := 200
	if id == 0 {
		status = 201
	}
	c.JSON(status, device)
}
func (h *DeviceHandler) check(c *gin.Context) {
	var input models.ConnectionCheck
	if !deviceBody(c, &input) {
		return
	}
	if err := h.legacy.Check(input); err != nil {
		deviceError(c, err)
		return
	}
	c.JSON(200, gin.H{"reachable": true, "checked_at": time.Now().UTC()})
}
func (h *DeviceHandler) measure(c *gin.Context) {
	id := deviceID(c)
	if id == 0 {
		return
	}
	device, err := h.devices.Get(c.Request.Context(), id)
	if err != nil {
		deviceError(c, err)
		return
	}
	reading, err := h.legacy.Measure(device)
	if err != nil {
		deviceError(c, err)
		return
	}
	c.JSON(200, reading)
}
func (h *DeviceHandler) state(c *gin.Context) {
	id := deviceID(c)
	if id == 0 {
		return
	}
	device, err := h.devices.Get(c.Request.Context(), id)
	if err != nil {
		deviceError(c, err)
		return
	}
	state, err := h.legacy.State(device)
	if err != nil {
		deviceError(c, err)
		return
	}
	c.JSON(200, state)
}
func (h *DeviceHandler) command(c *gin.Context) {
	var input models.LegacyCommand
	if !deviceBody(c, &input) {
		return
	}
	device, err := h.devices.Get(c.Request.Context(), input.Device)
	if err != nil {
		deviceError(c, err)
		return
	}
	result, err := h.legacy.Send(device, input)
	if err != nil {
		deviceError(c, err)
		return
	}
	c.JSON(200, result)
}
