PORT=9111
echo starting the server on port $PORT in directory $PWD
nohup bin/semantic_forms_play -J-Xmx100M -J-server -Dhttp.port=$PORT &
