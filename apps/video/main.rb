require 'webrick'
require_relative 'monolith_client'
require_relative 'camera_adapter'
require_relative 'stream_link_service'
require_relative 'video_api'

registry = MonolithClient.new(ENV.fetch('MONOLITH_URL', 'http://app:8080'))
api = VideoApi.new(StreamLinkService.new(registry, CameraAdapter.new))
server = WEBrick::HTTPServer.new(Port: 8080, BindAddress: '0.0.0.0', AccessLog: [])
server.mount_proc('/') { |request, response| api.handle(request, response) }
trap('TERM') { server.shutdown }
server.start
