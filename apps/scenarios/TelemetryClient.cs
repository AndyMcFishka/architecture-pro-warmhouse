using System.Text.Json.Nodes;

namespace Warmhouse.Scenarios;

public sealed class TelemetryClient(ServiceHttpClient http, string address)
{
    public async Task<Reading?> Latest(int sensorId, string metric)
    {
        try
        {
            var json = await http.Send(
                $"{address}/api/v1/telemetry/devices/{sensorId}/latest?metric={Uri.EscapeDataString(metric)}"
            );
            return new Reading(
                json["value"]!.GetValue<double>(),
                json["metric"]!.GetValue<string>(),
                json["unit"]!.GetValue<string>(),
                DateTimeOffset.Parse(json["measured_at"]!.GetValue<string>())
            );
        }
        catch (ApiError error) when (error.Status == 404)
        {
            return null;
        }
    }
}
