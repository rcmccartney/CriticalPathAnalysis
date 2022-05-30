#!/bin/bash

echo "*****************"
echo "* Running client"
echo "*****************"
./build/install/mygrpc/bin/client 100 &
sleep 2
read -p "Press enter to continue"
./build/install/mygrpc/bin/client 100 "a" &
sleep 2
read -p "Press enter to continue"
./build/install/mygrpc/bin/client 100 &
sleep 2
read -p "Press enter to continue"
./build/install/mygrpc/bin/client "queryOfDeath" 100 &
sleep 2
read -p "Press enter to quit"
