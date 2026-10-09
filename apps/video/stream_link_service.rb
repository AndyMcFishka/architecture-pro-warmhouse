require_relative 'models'

# Coordinates registry lookup and the camera adapter; no HTTP routing here.
class StreamLinkService
  def initialize(monolith, camera)
    @monolith = monolith
    @camera = camera
  end

  def get_stream_link(camera_id)
    settings = @monolith.get_camera(camera_id)
    raise ApiError.new(422) unless settings.type == 'CAMERA'
    @camera.request_stream_link(settings)
  end
end
