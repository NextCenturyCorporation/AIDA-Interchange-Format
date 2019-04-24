#!/bin/bash

# we need to end the script with a single process that continues to run forever to keep the
# container alive.
while true; 
do 
   echo "AWS_BATCH_JOB_NODE_INDEX is set to ${AWS_BATCH_JOB_NODE_INDEX}"
   echo "This entrypointscript will sleep for 5 seconds"
   sleep 5; 
done
