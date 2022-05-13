#!/bin/bash

echo "************************"
echo "* Build & test"
echo "************************"
./gradlew installDist -PskipAndroid=true
./gradlew test

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
