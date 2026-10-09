require 'net/http'
require 'json'
require_relative 'models'

# Reads camera configuration without exposing it to the caller.
class MonolithClient
  def initialize(address)
    @address = address
  end

  def get_camera(id)
    uri = URI("#{@address}/internal/v1/devices/#{id}")
    response = Net::HTTP.start(uri.host, uri.port, open_timeout: 3, read_timeout: 4) { |http| http.get(uri.request_uri) }
    raise ApiError.new(response.code.to_i == 404 ? 404 : 503) unless response.code == '200'
    device = JSON.parse(response.body)
    settings = device.fetch('connection_settings')
    CameraSettings.new(device.fetch('id'), device.fetch('kind'), settings.fetch('address'), settings['credential_ref'])
  end
end
