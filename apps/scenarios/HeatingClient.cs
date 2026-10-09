using System.Text.Json.Nodes;

namespace Warmhouse.Scenarios;

public sealed class HeatingClient(ServiceHttpClient http, string address)
{
    public Task<CommandResult> SetEnabled(int deviceId, bool enabled, Guid key) =>
        http.Command($"{address}/api/v1/heating/devices/{deviceId}/commands", new { enabled }, key);

    public Task<CommandResult> Get(Guid id) =>
        http.Command($"{address}/api/v1/heating/commands/{id}");
}
