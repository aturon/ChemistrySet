require 'buildr/scala'
repositories.remote << 'http://www.ibiblio.org/maven2'
repositories.remote << 'http://repo.codahale.com'

nj_layout = Layout.new
nj_layout[:source, :main, :scala] = 'src/main'
nj_layout[:source, :spec, :scala] = 'src/spec'

define 'newJoins', :layout=>nj_layout do
  compile.with Dir[_("lib/*.jar")]
  compile.with 'com.codahale:simplespec_2.8.1::0.2.0'
  run.using :main => "Runner"
  project.version = '0.0.1'
  package :jar
end