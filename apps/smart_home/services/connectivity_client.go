package services

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"smarthome/models"
	"time"
)

type APIError struct {
	Status  int
	Message string
}

func (e *APIError) Error() string { return e.Message }

// ConnectivityClient checks equipment through the configured internal service.
type ConnectivityClient struct {
	address string
	http    *http.Client
}

func NewConnectivityClient(address string) *ConnectivityClient {
	return &ConnectivityClient{address, &http.Client{Timeout: 5 * time.Second}}
}
func (client *ConnectivityClient) Check(ctx context.Context, settings models.ConnectionCheck) error {
	payload, err := json.Marshal(settings)
	if err != nil {
		return err
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, client.address+"/internal/v1/connections/check", bytes.NewReader(payload))
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", "application/json")
	response, err := client.http.Do(request)
	if err != nil {
		return &APIError{503, "Connectivity unavailable"}
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return &APIError{response.StatusCode, "Connection check failed"}
	}
	return nil
}
