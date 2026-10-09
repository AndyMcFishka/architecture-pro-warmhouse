using System.Text.Json.Nodes;

namespace Warmhouse.Scenarios;

public sealed class MonolithClient(ServiceHttpClient http, string address)
{
    public async Task Validate(Rule rule)
    {
        var sensor = await http.Send($"{address}/internal/v1/devices/{rule.SensorId}");
        var target = await http.Send($"{address}/internal/v1/devices/{rule.TargetDeviceId}");
        var kind = rule.Action.Service() switch
        {
            "heating" => "HEATING",
            "lighting" => "LIGHT",
            _ => "GATE",
        };
        if (
            sensor["kind"]!.GetValue<string>() != "SENSOR"
            || sensor["metric"]!.GetValue<string>() != rule.Metric
            || sensor["unit"]!.GetValue<string>() != rule.Unit
            || target["kind"]!.GetValue<string>() != kind
        )
            throw new ApiError(422);
    }
}
