#!/bin/bash

echo "*****************"
echo "* Running client"
echo "*****************"
read -p "Press enter to send Put:{100,a}"
./build/install/mygrpc/bin/client 100 "a" &
sleep 2
read -p "Press enter to send Put:{queryOfDeath, 100}"
./build/install/mygrpc/bin/client "queryOfDeath" 100 &
sleep 2
read -p "Press enter to send Get:{100}"
./build/install/mygrpc/bin/client 100 &
sleep 2
read -p "Press enter to send Get:{CallC2InSeries}"
./build/install/mygrpc/bin/client "CallC2InSeries" &
sleep 3
