using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;

namespace Warmhouse.Scenarios;

public enum Action
{
    HEATING_ON,
    HEATING_OFF,
    LIGHT_ON,
    LIGHT_OFF,
    GATE_LOCK,
    GATE_UNLOCK,
}

public enum Comparison
{
    LESS_THAN,
    GREATER_THAN,
}

/// <summary>A single condition and action. HTTP names remain compatible with OpenAPI.</summary>
public sealed record Rule
{
    public Guid Id { get; init; }
    public required bool Enabled { get; init; }
    public required int SensorId { get; init; }
    public required string Metric { get; init; }

    [JsonConverter(typeof(ComparisonConverter))]
    public required Comparison Comparison { get; init; }
    public required double Threshold { get; init; }
    public required string Unit { get; init; }
    public required int MaxAgeSeconds { get; init; }
    public required Action Action { get; init; }
    public required int TargetDeviceId { get; init; }
    public DateTimeOffset CreatedAt { get; init; }
    public DateTimeOffset UpdatedAt { get; init; }

    [JsonIgnore]
    public bool PreviousMatch { get; init; }

    public bool Matches(Reading reading) =>
        Comparison == Comparison.LESS_THAN ? reading.Value < Threshold : reading.Value > Threshold;

    public bool CanEvaluate(Reading reading, DateTimeOffset now) =>
        reading.Metric == Metric
        && reading.Unit == Unit
        && reading.MeasuredAt <= now
        && now - reading.MeasuredAt <= TimeSpan.FromSeconds(MaxAgeSeconds);

    public JsonObject ToInput()
    {
        var json = JsonSerializer.SerializeToNode(this, WireJson.Options)!.AsObject();
        foreach (var key in new[] { "id", "created_at", "updated_at" })
            json.Remove(key);
        return json;
    }
}

public sealed record Reading(double Value, string Metric, string Unit, DateTimeOffset MeasuredAt);

public sealed record Run(
    Guid Id,
    Guid RuleId,
    Action Action,
    int TargetDeviceId,
    string CommandService,
    Guid? CommandId,
    string Status,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt
);

public sealed record CommandResult(Guid? CommandId, string Status, bool Retryable = false);

public sealed class ApiError(int status) : Exception
{
    public int Status { get; } = status;
}

public static class WireJson
{
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        Converters = { new JsonStringEnumConverter(allowIntegerValues: false) },
    };
}

/// <summary>Translate the existing LT/GT wire contract into domain comparison names.</summary>
public sealed class ComparisonConverter : JsonConverter<Comparison>
{
    public override Comparison Read(
        ref Utf8JsonReader reader,
        Type type,
        JsonSerializerOptions options
    ) =>
        reader.GetString() switch
        {
            "LT" => Comparison.LESS_THAN,
            "GT" => Comparison.GREATER_THAN,
            _ => throw new JsonException("Invalid comparison"),
        };

    public override void Write(
        Utf8JsonWriter writer,
        Comparison value,
        JsonSerializerOptions options
    ) => writer.WriteStringValue(value == Comparison.LESS_THAN ? "LT" : "GT");
}

public static class ActionExtensions
{
    public static string Service(this Action action) =>
        action switch
        {
            Action.HEATING_ON or Action.HEATING_OFF => "heating",
            Action.LIGHT_ON or Action.LIGHT_OFF => "lighting",
            Action.GATE_LOCK or Action.GATE_UNLOCK => "gates",
            _ => throw new ApiError(400),
        };
}
