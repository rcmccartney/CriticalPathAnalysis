#!/bin/bash

echo "************************"
echo "* Compile frontend"
echo "************************"
pushd src
pushd frontend
cp ../main/proto/kvprog.proto ./
protoc -I=. kvprog.proto \
  --js_out=import_style=commonjs:. \
  --grpc-web_out=import_style=commonjs,mode=grpcwebtext:. || { echo 'Build failed!' ; exit 1; }
rm kvprog.proto
npm install || { echo 'Build failed!' ; exit 1; }
npx webpack client.js || { echo 'Build failed!' ; exit 1; }
popd
popd
