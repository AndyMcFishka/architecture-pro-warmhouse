# Values shared by the registry client, camera adapter and HTTP API.
CameraSettings = Data.define(:device_id, :type, :api_url, :credentials)
StreamLink = Data.define(:url, :expires_at) do
  def to_h
    { url: url, expires_at: expires_at.iso8601 }
  end
end
class ApiError < StandardError
  attr_reader :status
  def initialize(status)
    @status = status
    super('Request failed')
  end
end
