#!/bin/bash

# Cleanup anything left from earlier runs.
kill $(ps aux | grep '[/]usr/bin/java'  | awk '{print $2}')
kill $(ps aux | grep '[m]ygrpc' | awk '{print $2}')

echo "************************"
echo "* Build & test"
echo "************************"
./gradlew installDist -PskipAndroid=true || { echo 'Build failed!' ; exit 1; }
./gradlew test || { echo 'Tests failed!' ; exit 1; }

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
