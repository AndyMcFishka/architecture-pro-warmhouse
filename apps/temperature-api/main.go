package main

import (
	"encoding/json"
	"log"
	"math/rand/v2"
	"net/http"
	"time"
)

type temperature struct {
	Value       float64   `json:"value"`
	Unit        string    `json:"unit"`
	Timestamp   time.Time `json:"timestamp"`
	Location    string    `json:"location"`
	Status      string    `json:"status"`
	SensorID    string    `json:"sensor_id"`
	SensorType  string    `json:"sensor_type"`
	Description string    `json:"description"`
}

func temperatureHandler(w http.ResponseWriter, r *http.Request) {
	rooms := map[string]string{"1": "Living Room", "2": "Bedroom", "3": "Kitchen"}
	sensorID := r.PathValue("id")
	location := r.URL.Query().Get("location")
	if location == "" {
		location = rooms[sensorID]
		if location == "" {
			location = "Unknown"
		}
	}
	if sensorID == "" {
		sensorID = "0"
		for id, room := range rooms {
			if room == location {
				sensorID = id
				break
			}
		}
	}
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	if err := json.NewEncoder(w).Encode(temperature{
		Value: 15 + rand.Float64()*15, Unit: "°C", Timestamp: time.Now().UTC(),
		Location: location, Status: "active", SensorID: sensorID,
		SensorType: "temperature", Description: "Simulated temperature sensor",
	}); err != nil {
		log.Printf("write temperature response: %v", err)
	}
}

func newHandler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /temperature", temperatureHandler)
	mux.HandleFunc("GET /temperature/{id}", temperatureHandler)
	return mux
}

func main() {
	server := &http.Server{
		Addr: ":8081", Handler: newHandler(),
		ReadHeaderTimeout: 5 * time.Second,
	}
	log.Println("Temperature API listening on :8081")
	log.Fatal(server.ListenAndServe())
}
