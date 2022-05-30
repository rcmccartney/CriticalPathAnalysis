#!/bin/bash

./runCleanup.sh
./runBackendBuild.sh
./runFrontendBuild.sh
sleep 1
read -p "Press enter to continue"

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
read -p "Open http://localhost:8081. Press enter to continue"
popd
popd

./runClient.sh

echo "*****************"
echo "* Ending servers"
echo "*****************"
kill $topServerPID
kill $bServerPID
kill $cServerPID
docker kill $docId
kill $httpPID
sleep 1
