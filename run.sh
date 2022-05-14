#!/bin/bash

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
sleep 2
read -p "Press enter to continue"

echo "************************"
echo "* Run B server"
echo "************************"
./build/install/mygrpc/bin/b-server &
bServerPID=$!
sleep 2
read -p "Press enter to continue"

echo "************************"
echo "* Run C server"
echo "************************"
./build/install/mygrpc/bin/c-server &
cServerPID=$!
sleep 2
read -p "Press enter to continue"

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

echo "*****************"
echo "* Ending servers"
echo "*****************"
kill $topServerPID
kill $bServerPID
kill $cServerPID
