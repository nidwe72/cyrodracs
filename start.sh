#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/../cyrodracs_frontend"
LOG_FILE="$SCRIPT_DIR/backend.log"

echo "Starting backend... (logs: backend.log)"
cd "$SCRIPT_DIR"
./mvnw spring-boot:run > "$LOG_FILE" 2>&1 &
BACKEND_PID=$!

echo "Waiting for backend on port 8080..."
until curl -s http://localhost:8080/api/hello > /dev/null 2>&1; do
  sleep 1
done
echo "Backend is up."

trap "echo 'Shutting down...'; kill $BACKEND_PID 2>/dev/null" EXIT

echo "Starting frontend..."
cd "$FRONTEND_DIR"
flutter pub get
flutter run -d chrome --web-browser-flag "--no-sandbox"
