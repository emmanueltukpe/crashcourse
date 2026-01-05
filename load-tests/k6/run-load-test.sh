#!/bin/bash

# k6 Load Test Runner Script
# Usage: ./run-load-test.sh [test-name] [environment]

set -e

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
TEST_NAME="${1:-smoke-test}"
ENVIRONMENT="${2:-local}"

echo "==========================================
"
echo "k6 Load Test Runner"
echo "==========================================="
echo "Test: $TEST_NAME"
echo "Environment: $ENVIRONMENT"
echo "Base URL: $BASE_URL"
echo ""

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo "ERROR: k6 is not installed!"
    echo "Install with: brew install k6  (macOS)"
    echo "Or visit: https://k6.io/docs/getting-started/installation/"
    exit 1
fi

# Run the test
echo "Running test..."
k6 run \
  --out json=load-tests/k6/results/${TEST_NAME}-$(date +%Y%m%d-%H%M%S).json \
  -e BASE_URL="$BASE_URL" \
  "load-tests/k6/scenarios/${TEST_NAME}.js"

echo ""
echo "Test complete! Results saved to load-tests/k6/results/"
