#!/bin/bash

echo "************************"
echo "* Run top level server"
echo "************************"
./build/install/mygrpc/bin/top-level-server &
topServerPID=$!
sleep 1

echo "************************"
echo "* Run B server"
echo "************************"
./build/install/mygrpc/bin/b-server &
bServerPID=$!
sleep 1

echo "************************"
echo "* Run C server"
echo "************************"
./build/install/mygrpc/bin/c-server &
cServerPID=$!
sleep 1

echo "************************"
echo "* Run proxy & frontend"
echo "************************"
docId=$(docker run -d -v "$(pwd)"/envoy.yaml:/etc/envoy/envoy.yaml:ro -p 8080:8080 -p 9901:9901 envoyproxy/envoy:v1.22.0)
pushd src
pushd frontend
python3 -m http.server 8081 &
httpPID=$!
sleep 1
popd
popd


echo "**********************************"
echo "* Frontend & backend now running"
echo "**********************************"
echo "Open http://localhost:8081."
echo "You can now use runClient.sh to generate load, or send any request of your choosing."
read -p "Press enter to end servers."

echo "*****************"
echo "* Ending servers"
echo "*****************"
kill $topServerPID
kill $bServerPID
kill $cServerPID
docker kill $docId
kill $httpPID
sleep 1
