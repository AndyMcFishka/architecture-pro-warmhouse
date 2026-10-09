using System.Text.Json.Nodes;

namespace Warmhouse.Scenarios;

public sealed class ServiceHttpClient(HttpClient http)
{
    public async Task<JsonObject> Send(string url, object? body = null, Guid? key = null)
    {
        using var request = new HttpRequestMessage(
            body is null ? HttpMethod.Get : HttpMethod.Post,
            url
        );
        if (body is not null)
            request.Content = JsonContent.Create(body);
        if (key.HasValue)
            request.Headers.Add("Idempotency-Key", key.Value.ToString());
        using var response = await http.SendAsync(request);
        if (!response.IsSuccessStatusCode)
            throw new ApiError((int)response.StatusCode);
        return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
    }

    public async Task<CommandResult> Command(string url, object? body = null, Guid? key = null)
    {
        try
        {
            var json = await Send(url, body, key);
            return new CommandResult(
                Guid.Parse(json["id"]!.GetValue<string>()),
                json["status"]!.GetValue<string>()
            );
        }
        catch (ApiError error) when (error.Status is 400 or 404 or 409 or 422)
        {
            return new CommandResult(null, "FAILED");
        }
        catch (Exception error)
            when (error is HttpRequestException or TaskCanceledException or ApiError)
        {
            return new CommandResult(null, "UNKNOWN", Retryable: true);
        }
    }
}
