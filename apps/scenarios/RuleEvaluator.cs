namespace Warmhouse.Scenarios;

/// <summary>Evaluates fresh readings; persistence and HTTP are delegated to their owners.</summary>
public sealed class RuleEvaluator(
    ScenarioStore store,
    TelemetryClient telemetry,
    CommandClient commands,
    TimeSpan interval,
    ILogger<RuleEvaluator> logger
) : BackgroundService
{
    public async Task Tick()
    {
        foreach (var rule in await store.EnabledRules())
        {
            try
            {
                var reading = await telemetry.Latest(rule.SensorId, rule.Metric);
                if (reading is null || !rule.CanEvaluate(reading, DateTimeOffset.UtcNow))
                    continue;
                if (rule.Matches(reading))
                    await store.StartIfRising(rule);
                else
                    await store.MarkFalse(rule);
            }
            catch (Exception error)
            {
                logger.LogWarning(error, "Rule {RuleId} evaluation failed", rule.Id);
            }
        }
        foreach (var run in await store.PendingRuns())
        {
            try
            {
                await store.SaveResult(run.Id, await commands.Execute(run));
            }
            catch (Exception error)
            {
                logger.LogWarning(error, "Run {RunId} dispatch failed", run.Id);
            }
        }
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        // one worker replica; add database claims before scaling evaluators.
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await Tick();
            }
            catch (Exception error)
            {
                logger.LogWarning(error, "Scenario tick failed");
            }
            await Task.Delay(interval, stoppingToken);
        }
    }
}
