#!/bin/bash
# Kill processes listening on a specified port
# Usage: kill_port.sh <port_number>

PORT=$1

if [ -z "$PORT" ]; then
    echo "Error: Port number not specified"
    echo "Usage: kill_port.sh <port_number>"
    exit 1
fi

echo "Checking for processes on port $PORT..."

# Find processes listening on the port
PIDS=$(lsof -ti :$PORT 2>/dev/null)

if [ -z "$PIDS" ]; then
    echo "Port $PORT is available."
else
    echo "Found process(es) using port $PORT. Terminating..."
    for PID in $PIDS; do
        kill -9 $PID 2>/dev/null
        if [ $? -eq 0 ]; then
            echo "  Terminated process $PID"
        else
            echo "  Warning: Could not terminate process $PID"
        fi
    done
    sleep 0.5
    echo "Port $PORT cleared."
fi
