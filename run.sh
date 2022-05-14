#!/bin/bash

echo "************************"
echo "* Build & test"
echo "************************"
./gradlew installDist -PskipAndroid=true || { echo 'Build failed!' ; exit 1; }
./gradlew test || { echo 'Tests failed!' ; exit 1; }

echo "************************"
echo "* Run server"
echo "************************"
./build/install/mygrpc/bin/top-level-server &
serverPID=$!
sleep 2
read -p "Press enter to continue"

echo "*****************"
echo "* Running client"
echo "*****************"
./build/install/mygrpc/bin/client 100 &
./build/install/mygrpc/bin/client 100 "a" &
./build/install/mygrpc/bin/client 100 &
./build/install/mygrpc/bin/client -c &
sleep 5

echo "*****************"
echo "* Ending server"
echo "*****************"
kill $serverPID
