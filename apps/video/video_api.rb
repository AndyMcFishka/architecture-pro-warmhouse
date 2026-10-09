require 'json'
require_relative 'models'

class VideoApi
  def initialize(service)
    @service = service
  end

  def get_stream_link(camera_id)
    @service.get_stream_link(camera_id).to_h
  end

  def handle(request, response)
    response['Content-Type'] = 'application/json'
    response['Cache-Control'] = 'no-store'
    begin
      if request.request_method == 'GET' && request.path == '/health'
        body = { status: 'ok' }
      elsif request.request_method == 'GET' && (match = request.path.match(%r{\A/api/v1/video/cameras/([1-9]\d*)/stream-link\z}))
        body = get_stream_link(match[1].to_i)
      else
        raise ApiError.new(404)
      end
      response.status = 200
    rescue ApiError => error
      response.status = error.status
      body = { error: error.message }
    rescue StandardError
      response.status = 503
      body = { error: 'Dependency unavailable' }
    end
    response.body = JSON.generate(body)
  end
end
