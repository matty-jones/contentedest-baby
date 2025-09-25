#!/bin/bash

# Start Server Script for The Contentedest Baby
# This script activates the virtual environment and starts the FastAPI server

set -e  # Exit on any error

# Default values
PORT=8005
HOST="0.0.0.0"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -p|--port)
      PORT="$2"
      shift 2
      ;;
    -h|--host)
      HOST="$2"
      shift 2
      ;;
    --help)
      echo "Usage: $0 [-p PORT] [-h HOST]"
      echo ""
      echo "Options:"
      echo "  -p, --port PORT    Port to run server on (default: 8005)"
      echo "  -h, --host HOST    Host to bind to (default: 0.0.0.0)"
      echo "  --help             Show this help message"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Check if we're in the right directory
if [[ ! -d "server" ]]; then
    echo "Error: server/ directory not found. Please run this script from the project root."
    exit 1
fi

# Check if virtual environment exists
if [[ ! -d "server/.venv" ]]; then
    echo "Error: Virtual environment not found at server/.venv"
    echo "Please create it with:"
    echo "  python3 -m venv server/.venv"
    echo "  cd server"
    echo "  source .venv/bin/activate"
    echo "  pip install -r requirements.txt"
    exit 1
fi

echo "Starting The Contentedest Baby server..."
echo "Host: $HOST"
echo "Port: $PORT"
echo "Press Ctrl+C to stop the server"
echo ""

# Activate virtual environment and start server
cd server
source .venv/bin/activate
exec uvicorn app.main:app --reload --host "$HOST" --port "$PORT"
