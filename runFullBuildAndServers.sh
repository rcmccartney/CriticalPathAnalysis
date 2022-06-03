#!/bin/bash

./runBackendBuild.sh
./runFrontendBuild.sh

./runCleanup.sh
./runServers.sh

