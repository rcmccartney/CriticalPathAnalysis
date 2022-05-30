#!/bin/bash

# Cleanup anything left from earlier runs.
kill $(ps aux | grep '[/]usr/bin/java'  | awk '{print $2}')
kill $(ps aux | grep '[h]ttp.server 8081'  | awk '{print $2}')
kill $(ps aux | grep '[m]ygrpc' | awk '{print $2}')
