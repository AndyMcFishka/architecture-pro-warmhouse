using System.Text.Json.Nodes;

namespace Warmhouse.Scenarios;

public sealed class GatesClient(ServiceHttpClient http, string address)
{
    public Task<CommandResult> SetLocked(int deviceId, bool locked, Guid key) =>
        http.Command($"{address}/api/v1/gates/devices/{deviceId}/commands", new { locked }, key);

    public Task<CommandResult> Get(Guid id) =>
        http.Command($"{address}/api/v1/gates/commands/{id}");
}
