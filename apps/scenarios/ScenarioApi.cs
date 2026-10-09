using System.Text.Json;
using System.Text.Json.Nodes;

namespace Warmhouse.Scenarios;

/// <summary>Validates HTTP input and delegates rule storage to ScenarioStore.</summary>
public sealed class ScenarioApi(ScenarioStore store, MonolithClient registry)
{
    public void Map(WebApplication app)
    {
        app.MapGet(
            "/health",
            async () =>
            {
                await using var db = await store.Open();
                return new { status = "ok" };
            }
        );
        app.MapGet("/api/v1/scenarios/rules", () => store.ListRules());
        app.MapGet("/api/v1/scenarios/rules/{id:guid}", (Guid id) => store.GetRule(id));
        app.MapPost(
            "/api/v1/scenarios/rules",
            async (HttpRequest request) =>
                Results.Json(await Save(null, request), WireJson.Options, statusCode: 201)
        );
        app.MapPut(
            "/api/v1/scenarios/rules/{id:guid}",
            (Guid id, HttpRequest request) => Save(id, request)
        );
        app.MapPatch(
            "/api/v1/scenarios/rules/{id:guid}/enabled",
            async (Guid id, HttpRequest request) =>
            {
                var json = await Read(request);
                if (json.Count != 1 || json["enabled"] is null)
                    throw new ApiError(400);
                return await SetEnabled(id, json["enabled"]!.GetValue<bool>());
            }
        );
        app.MapGet(
            "/api/v1/scenarios/rules/{id:guid}/runs",
            (Guid id, HttpRequest request) =>
                ListRuns(id, QueryInt(request, "limit", 100), QueryInt(request, "offset", 0))
        );
    }

    private async Task<Rule> Save(Guid? id, HttpRequest request)
    {
        var json = await Read(request);
        string[] fields =
        [
            "enabled",
            "sensor_id",
            "metric",
            "comparison",
            "threshold",
            "unit",
            "max_age_seconds",
            "action",
            "target_device_id",
        ];
        if (json.Count != fields.Length || fields.Any(key => json[key] is null))
            throw new ApiError(400);
        var rule = json.Deserialize<Rule>(WireJson.Options)! with { Id = id ?? Guid.NewGuid() };
        if (
            rule.SensorId < 1
            || rule.TargetDeviceId < 1
            || rule.MaxAgeSeconds < 1
            || !double.IsFinite(rule.Threshold)
            || !Enum.IsDefined(rule.Action)
        )
            throw new ApiError(400);
        return id.HasValue ? await Update(rule) : await Create(rule);
    }

    public async Task<Rule> Create(Rule rule)
    {
        await registry.Validate(rule);
        return await store.SaveRule(rule, create: true);
    }

    public async Task<Rule> Update(Rule rule)
    {
        await registry.Validate(rule);
        return await store.SaveRule(rule, create: false);
    }

    public Task<Rule> SetEnabled(Guid id, bool enabled) => store.SetEnabled(id, enabled);

    public Task<List<Run>> ListRuns(Guid id, int limit, int offset) =>
        store.ListRuns(id, limit, offset);

    private static async Task<JsonObject> Read(HttpRequest request) =>
        (await JsonNode.ParseAsync(request.Body)) as JsonObject ?? throw new ApiError(400);

    private static int QueryInt(HttpRequest request, string key, int fallback)
    {
        if (!request.Query.ContainsKey(key))
            return fallback;
        return int.TryParse(request.Query[key], out var value) ? value : throw new ApiError(400);
    }
}
