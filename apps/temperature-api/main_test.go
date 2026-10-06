package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestTemperatureAPI(t *testing.T) {
	handler := newHandler()
	for _, tc := range []struct{ path, id, location string }{
		{"/temperature?location=Living%20Room", "1", "Living Room"},
		{"/temperature?location=Bedroom", "2", "Bedroom"},
		{"/temperature?location=Kitchen", "3", "Kitchen"},
		{"/temperature?location=Office", "0", "Office"},
		{"/temperature", "0", "Unknown"},
		{"/temperature/1", "1", "Living Room"},
		{"/temperature/2", "2", "Bedroom"},
		{"/temperature/3", "3", "Kitchen"},
		{"/temperature/42", "42", "Unknown"},
	} {
		t.Run(tc.path, func(t *testing.T) {
			values := make(map[float64]bool)
			for i := 0; i < 10; i++ {
				start := time.Now()
				w := httptest.NewRecorder()
				handler.ServeHTTP(w, httptest.NewRequest(http.MethodGet, tc.path, nil))
				var got temperature
				if w.Code != http.StatusOK || w.Header().Get("Content-Type") != "application/json" {
					t.Fatalf("unexpected response: %v", w.Result())
				}
				if err := json.Unmarshal(w.Body.Bytes(), &got); err != nil {
					t.Fatal(err)
				}
				if got.SensorID != tc.id || got.Location != tc.location || got.Value < 15 || got.Value >= 30 ||
					got.Unit != "°C" || got.Status != "active" || got.SensorType != "temperature" ||
					got.Timestamp.Before(start) || got.Timestamp.After(time.Now()) {
					t.Fatalf("unexpected temperature: %+v", got)
				}
				values[got.Value] = true
			}
			if len(values) == 1 {
				t.Fatal("temperature did not change across requests")
			}
		})
	}
	for _, tc := range []struct {
		method, path string
		status       int
	}{
		{http.MethodPost, "/temperature", http.StatusMethodNotAllowed},
		{http.MethodGet, "/missing", http.StatusNotFound},
	} {
		w := httptest.NewRecorder()
		handler.ServeHTTP(w, httptest.NewRequest(tc.method, tc.path, nil))
		if w.Code != tc.status {
			t.Fatalf("%s %s: got %d, want %d", tc.method, tc.path, w.Code, tc.status)
		}
	}
}
