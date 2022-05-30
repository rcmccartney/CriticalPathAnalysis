#!/bin/bash

./runCleanup.sh
./runBackendBuild.sh

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

./runClient.sh

echo "*****************"
echo "* Ending servers"
echo "*****************"
kill $topServerPID
kill $bServerPID
kill $cServerPID
sleep 1
