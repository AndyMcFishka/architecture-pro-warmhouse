using System.Text.Json.Nodes;

namespace Warmhouse.Scenarios;

public sealed class CommandClient(HeatingClient heating, LightingClient lighting, GatesClient gates)
{
    public Task<CommandResult> Execute(Run run)
    {
        if (run.CommandId is Guid id)
            return run.Action.Service() switch
            {
                "heating" => heating.Get(id),
                "lighting" => lighting.Get(id),
                _ => gates.Get(id),
            };
        return run.Action switch
        {
            Action.HEATING_ON => heating.SetEnabled(run.TargetDeviceId, true, run.Id),
            Action.HEATING_OFF => heating.SetEnabled(run.TargetDeviceId, false, run.Id),
            Action.LIGHT_ON => lighting.SetEnabled(run.TargetDeviceId, true, run.Id),
            Action.LIGHT_OFF => lighting.SetEnabled(run.TargetDeviceId, false, run.Id),
            Action.GATE_LOCK => gates.SetLocked(run.TargetDeviceId, true, run.Id),
            Action.GATE_UNLOCK => gates.SetLocked(run.TargetDeviceId, false, run.Id),
            _ => throw new ApiError(400),
        };
    }
}
