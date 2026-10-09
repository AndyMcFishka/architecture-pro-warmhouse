require 'securerandom'
require 'time'
require_relative 'models'

class CameraAdapter
  def request_stream_link(settings)
    # demonstration only; replace this adapter when real cameras are available.
    StreamLink.new("https://camera.example.test/live?camera=#{settings.device_id}&token=#{SecureRandom.hex(16)}", Time.now.utc + 300)
  end
end
