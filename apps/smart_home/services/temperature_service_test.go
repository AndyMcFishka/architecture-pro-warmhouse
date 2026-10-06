package services

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestGetTemperatureEscapesLocation(t *testing.T) {
	const location = "Living Room & Kitchen+Балкон"
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if got := r.URL.Query().Get("location"); got != location {
			t.Errorf("location = %q, want %q", got, location)
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"value":22.5,"status":"active"}`))
	}))
	defer server.Close()
	got, err := NewTemperatureService(server.URL).GetTemperature(location)
	if err != nil {
		t.Fatal(err)
	}
	if got.Value != 22.5 || got.Status != "active" {
		t.Fatalf("unexpected response: %+v", got)
	}
}
