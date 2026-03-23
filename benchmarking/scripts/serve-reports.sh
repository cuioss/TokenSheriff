#!/bin/bash
# HTTP server for viewing generated benchmark reports locally
# Usage:
#   ./serve-reports.sh [module] [port]    - Start server for specified module (default: library, port: 8080)
#   ./serve-reports.sh stop               - Stop running server
# Modules:
#   library  - benchmark-core results (default)
#   wrk      - benchmark-integration-wrk results

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEFAULT_PORT=8080
DEFAULT_MODULE="library"

# Function to stop the server
stop_server() {
    echo "Looking for running benchmark report servers..."
    
    # Find Python HTTP server processes
    PIDS=$(ps aux | grep -E "python.*http.server|python.*SimpleHTTPServer" | grep -v grep | awk '{print $2}')
    
    if [ -z "$PIDS" ]; then
        echo "No HTTP server found running."
        exit 0
    fi
    
    echo "Stopping HTTP server(s)..."
    for PID in $PIDS; do
        # Get process info for confirmation
        PROCESS_INFO=$(ps -p $PID -o command= 2>/dev/null)
        if [ -n "$PROCESS_INFO" ]; then
            echo "   Stopping PID $PID: $PROCESS_INFO"
            kill $PID 2>/dev/null
            if [ $? -eq 0 ]; then
                echo "   Stopped PID $PID"
            else
                echo "   Could not stop PID $PID (may require sudo)"
            fi
        fi
    done
    
    echo "Done!"
    exit 0
}

# Handle "stop" parameter
if [ "$1" = "stop" ]; then
    stop_server
fi

# Parse parameters
MODULE="$DEFAULT_MODULE"
PORT="$DEFAULT_PORT"

# Check if first parameter is a module name
if [[ "$1" =~ ^(library|wrk)$ ]]; then
    MODULE="$1"
    PORT="${2:-$DEFAULT_PORT}"
elif [[ "$1" =~ ^[0-9]+$ ]]; then
    # First parameter is a port number
    PORT="$1"
else
    # Show usage if invalid parameter
    if [ -n "$1" ]; then
        echo "Error: Invalid parameter: $1"
        echo ""
        echo "Usage:"
        echo "  ./serve-reports.sh [module] [port]"
        echo "  ./serve-reports.sh stop"
        echo ""
        echo "Modules: library (default), wrk"
        echo "Port: default is 8080"
        exit 1
    fi
fi

# Set reports directory based on module
case "$MODULE" in
    library)
        REPORTS_DIR="$PROJECT_ROOT/benchmarking/benchmark-core/target/benchmark-results/gh-pages-ready"
        MODULE_DESC="Library Benchmark Results"
        ;;
    wrk)
        REPORTS_DIR="$PROJECT_ROOT/benchmarking/benchmark-integration-wrk/target/benchmark-results/gh-pages-ready"
        MODULE_DESC="WRK Load Testing Results"
        ;;
esac

# Check if reports directory exists
if [ ! -d "$REPORTS_DIR" ]; then
    echo "Error: Reports directory not found: $REPORTS_DIR"
    echo ""
    case "$MODULE" in
        library)
            echo "Please run library benchmarks first:"
            echo "  ./mvnw verify -pl benchmarking/benchmark-core -Pbenchmark"
            echo ""
            echo "This will generate HTML reports in:"
            echo "  benchmarking/benchmark-core/target/benchmark-results/gh-pages-ready/"
            ;;
        wrk)
            echo "Please run WRK load testing benchmarks first:"
            echo "  ./mvnw verify -pl benchmarking/benchmark-integration-wrk -Pbenchmark"
            echo ""
            echo "This will generate HTML reports in:"
            echo "  benchmarking/benchmark-integration-wrk/target/benchmark-results/gh-pages-ready/"
            ;;
    esac
    exit 1
fi

# Check if server is already running on the specified port
EXISTING_PID=$(lsof -ti:$PORT 2>/dev/null)
if [ -n "$EXISTING_PID" ]; then
    echo "Port $PORT is already in use by process $EXISTING_PID"
    echo ""
    echo "You can:"
    echo "  1. Stop the existing server: ./serve-reports.sh stop"
    echo "  2. Use a different port: ./serve-reports.sh 8081"
    echo ""
    exit 1
fi

echo "Starting local HTTP server for $MODULE_DESC"
echo "Module: $MODULE"
echo "Serving from: $REPORTS_DIR"
echo "URL: http://localhost:$PORT"
echo ""

# Show available content based on module
case "$MODULE" in
    library)
        echo "Available reports:"
        echo "  - http://localhost:$PORT                          (Main benchmark overview)"
        echo "  - http://localhost:$PORT/index.html               (Performance dashboard)"
        echo "  - http://localhost:$PORT/trends.html              (Historical trends)"
        echo "  - http://localhost:$PORT/detailed.html            (Detailed analysis)"
        echo ""
        echo "Badges:"
        echo "  - http://localhost:$PORT/badges/performance-badge.json"
        echo "  - http://localhost:$PORT/badges/trend-badge.json"
        echo ""
        echo "Data:"
        echo "  - http://localhost:$PORT/data/benchmark-data.json"
        echo "  - http://localhost:$PORT/data/original-jmh-result.json"
        ;;
    wrk)
        echo "Available reports:"
        echo "  - http://localhost:$PORT                          (Main benchmark overview)"
        echo "  - http://localhost:$PORT/index.html               (Performance dashboard)"
        echo "  - http://localhost:$PORT/trends.html              (Historical trends)"
        echo "  - http://localhost:$PORT/detailed.html            (Detailed analysis)"
        echo ""
        echo "Badges:"
        echo "  - http://localhost:$PORT/badges/integration-performance-badge.json"
        echo "  - http://localhost:$PORT/badges/integration-trend-badge.json"
        echo ""
        echo "Data:"
        echo "  - http://localhost:$PORT/data/benchmark-data.json"
        ;;
esac

echo ""
echo "To stop the server:"
echo "   Press Ctrl+C or run: ./serve-reports.sh stop"
echo ""

# Change to reports directory
cd "$REPORTS_DIR"

# Try Python 3 first, then Python 2
if command -v python3 &> /dev/null; then
    python3 -m http.server "$PORT"
elif command -v python &> /dev/null; then
    python -m SimpleHTTPServer "$PORT"
else
    echo "Error: Python is not installed. Please install Python to use this script."
    echo "Alternative: Use any other HTTP server like 'npx http-server' or 'php -S localhost:$PORT'"
    exit 1
fi