#!/bin/bash

# we need to end the script with a single process that continues to run forever to keep the
# container alive.
tail -f /dev/null
