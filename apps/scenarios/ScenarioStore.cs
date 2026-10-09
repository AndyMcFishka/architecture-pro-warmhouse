using System.Text.Json;
using Npgsql;

namespace Warmhouse.Scenarios;

/// <summary>Owns rule persistence and atomic rising-edge/run creation.</summary>
public sealed class ScenarioStore(string connectionString)
{
    public async Task<NpgsqlConnection> Open()
    {
        var connection = new NpgsqlConnection(connectionString);
        try
        {
            await connection.OpenAsync();
            return connection;
        }
        catch
        {
            await connection.DisposeAsync();
            throw;
        }
    }

    private static Rule ReadRule(NpgsqlDataReader reader) =>
        JsonSerializer.Deserialize<Rule>(reader.GetString(1), WireJson.Options)! with
        {
            Id = reader.GetGuid(0),
            PreviousMatch = reader.GetBoolean(2),
            CreatedAt = reader.GetDateTime(3),
            UpdatedAt = reader.GetDateTime(4),
        };

    public async Task<List<Rule>> ListRules(bool enabledOnly = false)
    {
        await using var connection = await Open();
        await using var query = new NpgsqlCommand(
            "SELECT id,body,previous_match,created_at,updated_at FROM rules "
                + (enabledOnly ? "WHERE (body->>'enabled')::boolean " : "")
                + "ORDER BY created_at,id",
            connection
        );
        await using var reader = await query.ExecuteReaderAsync();
        var rules = new List<Rule>();
        while (await reader.ReadAsync())
            rules.Add(ReadRule(reader));
        return rules;
    }

    public Task<List<Rule>> EnabledRules() => ListRules(true);

    public async Task<Rule> GetRule(Guid id)
    {
        await using var connection = await Open();
        await using var query = new NpgsqlCommand(
            "SELECT id,body,previous_match,created_at,updated_at FROM rules WHERE id=@id",
            connection
        );
        query.Parameters.AddWithValue("id", id);
        await using var reader = await query.ExecuteReaderAsync();
        if (!await reader.ReadAsync())
            throw new ApiError(404);
        return ReadRule(reader);
    }

    public async Task<Rule> SaveRule(Rule rule, bool create)
    {
        await using var connection = await Open();
        var sql = create
            ? "INSERT INTO rules(id,body) VALUES(@id,@body::jsonb)"
            : "UPDATE rules SET previous_match=CASE WHEN body=@body::jsonb THEN previous_match ELSE false END, updated_at=CASE WHEN body=@body::jsonb THEN updated_at ELSE now() END, body=@body::jsonb WHERE id=@id";
        await using var query = new NpgsqlCommand(sql, connection);
        query.Parameters.AddWithValue("id", rule.Id);
        query.Parameters.AddWithValue("body", rule.ToInput().ToJsonString());
        if (await query.ExecuteNonQueryAsync() != 1)
            throw new ApiError(404);
        return await GetRule(rule.Id);
    }

    public async Task<Rule> SetEnabled(Guid id, bool enabled)
    {
        await using var connection = await Open();
        await using var query = new NpgsqlCommand(
            "UPDATE rules SET previous_match=CASE WHEN (body->>'enabled')::boolean=false AND @enabled THEN false ELSE previous_match END,body=jsonb_set(body,'{enabled}',to_jsonb(@enabled)),updated_at=now() WHERE id=@id",
            connection
        );
        query.Parameters.AddWithValue("id", id);
        query.Parameters.AddWithValue("enabled", enabled);
        if (await query.ExecuteNonQueryAsync() != 1)
            throw new ApiError(404);
        return await GetRule(id);
    }

    public Task<Run?> StartIfRising(Rule rule) => ApplyCondition(rule, true);

    public async Task MarkFalse(Rule rule) => await ApplyCondition(rule, false);

    private async Task<Run?> ApplyCondition(Rule rule, bool matches)
    {
        await using var connection = await Open();
        await using var transaction = await connection.BeginTransactionAsync();
        await using var select = new NpgsqlCommand(
            "SELECT previous_match FROM rules WHERE id=@id AND body=@body::jsonb FOR UPDATE",
            connection,
            transaction
        );
        select.Parameters.AddWithValue("id", rule.Id);
        select.Parameters.AddWithValue("body", rule.ToInput().ToJsonString());
        // An edit during the HTTP read invalidates this evaluation snapshot.
        var previous = await select.ExecuteScalarAsync();
        if (previous is not bool previousMatch)
            return null;
        Run? run = null;
        if (matches && !previousMatch)
        {
            var now = DateTimeOffset.UtcNow;
            run = new Run(
                Guid.NewGuid(),
                rule.Id,
                rule.Action,
                rule.TargetDeviceId,
                rule.Action.Service(),
                null,
                "PENDING",
                now,
                now
            );
            await using var insert = new NpgsqlCommand(
                "INSERT INTO runs(id,rule_id,action,target_device_id,command_service) VALUES(@run,@id,@action,@target,@service)",
                connection,
                transaction
            );
            insert.Parameters.AddWithValue("run", run.Id);
            insert.Parameters.AddWithValue("id", rule.Id);
            insert.Parameters.AddWithValue("action", rule.Action.ToString());
            insert.Parameters.AddWithValue("target", rule.TargetDeviceId);
            insert.Parameters.AddWithValue("service", run.CommandService);
            await insert.ExecuteNonQueryAsync();
        }
        await using var update = new NpgsqlCommand(
            "UPDATE rules SET previous_match=@matches WHERE id=@id",
            connection,
            transaction
        );
        update.Parameters.AddWithValue("id", rule.Id);
        update.Parameters.AddWithValue("matches", matches);
        await update.ExecuteNonQueryAsync();
        await transaction.CommitAsync();
        return run;
    }

    public Task<List<Run>> PendingRuns() => ReadRuns(null, 100, 0);

    public async Task<List<Run>> ListRuns(Guid id, int limit, int offset)
    {
        if (limit < 1 || limit > 1000 || offset < 0)
            throw new ApiError(400);
        await GetRule(id);
        return await ReadRuns(id, limit, offset);
    }

    private async Task<List<Run>> ReadRuns(Guid? id, int limit, int offset)
    {
        await using var connection = await Open();
        var where = id.HasValue
            ? "WHERE rule_id=@id ORDER BY created_at DESC,id DESC"
            : "WHERE status IN ('PENDING','ACCEPTED') ORDER BY created_at,id";
        await using var query = new NpgsqlCommand(
            $"SELECT row_to_json(r)::text FROM (SELECT * FROM runs {where} LIMIT @limit OFFSET @offset) r",
            connection
        );
        if (id.HasValue)
            query.Parameters.AddWithValue("id", id.Value);
        query.Parameters.AddWithValue("limit", limit);
        query.Parameters.AddWithValue("offset", offset);
        await using var reader = await query.ExecuteReaderAsync();
        var runs = new List<Run>();
        while (await reader.ReadAsync())
            runs.Add(JsonSerializer.Deserialize<Run>(reader.GetString(0), WireJson.Options)!);
        return runs;
    }

    public async Task SaveResult(Guid runId, CommandResult result)
    {
        if (result.Retryable)
            return; // Keep the original pending run and its idempotency key.
        await using var connection = await Open();
        await using var query = new NpgsqlCommand(
            "UPDATE runs SET command_id=@command,status=@status,updated_at=now() WHERE id=@id",
            connection
        );
        query.Parameters.AddWithValue("id", runId);
        query.Parameters.AddWithValue(
            "command",
            NpgsqlTypes.NpgsqlDbType.Uuid,
            (object?)result.CommandId ?? DBNull.Value
        );
        query.Parameters.AddWithValue("status", result.Status);
        await query.ExecuteNonQueryAsync();
    }
}
