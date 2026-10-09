using System.Text.Json;
using Warmhouse.Scenarios;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.ConfigureKestrel(options => options.Limits.MaxRequestBodySize = 65536);
builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.PropertyNamingPolicy = WireJson.Options.PropertyNamingPolicy;
    foreach (var converter in WireJson.Options.Converters)
        options.SerializerOptions.Converters.Add(converter);
});
var http = new ServiceHttpClient(new HttpClient { Timeout = TimeSpan.FromSeconds(5) });
string Address(string service) =>
    builder.Configuration[$"{service.ToUpperInvariant()}_URL"] ?? $"http://{service}:8080";
var store = new ScenarioStore(builder.Configuration["DATABASE_URL"]!);
var telemetry = new TelemetryClient(http, Address("telemetry"));
var commands = new CommandClient(
    new HeatingClient(http, Address("heating")),
    new LightingClient(http, Address("lighting")),
    new GatesClient(http, Address("gates"))
);
var api = new ScenarioApi(
    store,
    new MonolithClient(http, builder.Configuration["MONOLITH_URL"] ?? "http://app:8080")
);
var interval = TimeSpan.FromSeconds(builder.Configuration.GetValue("POLL_INTERVAL_SECONDS", 5));
builder.Services.AddHostedService(provider => new RuleEvaluator(
    store,
    telemetry,
    commands,
    interval,
    provider.GetRequiredService<ILogger<RuleEvaluator>>()
));
var app = builder.Build();
app.Use(
    async (context, next) =>
    {
        try
        {
            await next();
        }
        catch (Exception error)
        {
            context.Response.StatusCode = error switch
            {
                ApiError apiError => apiError.Status,
                JsonException or FormatException or InvalidOperationException => 400,
                _ => 503,
            };
            await context.Response.WriteAsJsonAsync(new { error = "Request failed" });
        }
    }
);
api.Map(app);
app.Run("http://0.0.0.0:8080");
