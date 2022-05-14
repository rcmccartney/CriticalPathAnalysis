#!/bin/bash

echo "************************"
echo "* Build & test"
echo "************************"
./gradlew installDist -PskipAndroid=true || { echo 'Build failed!' ; exit 1; }
./gradlew test || { echo 'Tests failed!' ; exit 1; }

echo "************************"
echo "* Compile frontend"
echo "************************"
pushd src
pushd frontend
cp ../main/proto/kvprog.proto ./
protoc -I=. kvprog.proto \
  --js_out=import_style=commonjs:. \
  --grpc-web_out=import_style=commonjs,mode=grpcwebtext:.
rm kvprog.proto
npm install
npx webpack client.js
popd
popd

echo "************************"
echo "* Run top level server"
echo "************************"
./build/install/mygrpc/bin/top-level-server &
topServerPID=$!
sleep 1
read -p "Press enter to continue"

echo "************************"
echo "* Run B server"
echo "************************"
./build/install/mygrpc/bin/b-server &
bServerPID=$!
sleep 1
read -p "Press enter to continue"

echo "************************"
echo "* Run C server"
echo "************************"
./build/install/mygrpc/bin/c-server &
cServerPID=$!
sleep 1
read -p "Press enter to continue"

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

echo "*****************"
echo "* Running client"
echo "*****************"
./build/install/mygrpc/bin/client 100 &
./build/install/mygrpc/bin/client 100 "a" &
./build/install/mygrpc/bin/client 100 &
./build/install/mygrpc/bin/client -c &
./build/install/mygrpc/bin/client -p 30429 -c &
./build/install/mygrpc/bin/client -p 30430 -c &
sleep 5
read -p "Press enter to continue"

echo "*****************"
echo "* Ending servers"
echo "*****************"
kill $topServerPID
kill $bServerPID
kill $cServerPID
docker kill $docId
kill $httpPID
