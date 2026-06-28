#!/bin/bash
# Collect accurate Docker container CPU metrics during benchmark

CONTAINER_NAME=${1:-"token-sheriff-integration-tests"}
DURATION=${2:-90}
OUTPUT_FILE=${3:-"docker-cpu-metrics.json"}

echo "Collecting Docker CPU metrics for $CONTAINER_NAME for $DURATION seconds..."

# Initialize JSON output
echo "{" > $OUTPUT_FILE
echo "  \"container\": \"$CONTAINER_NAME\"," >> $OUTPUT_FILE
echo "  \"start_time\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"," >> $OUTPUT_FILE
echo "  \"metrics\": [" >> $OUTPUT_FILE

# Collect metrics every second
for ((i=1; i<=DURATION; i++)); do
    STATS=$(docker stats --no-stream --format "{{json .}}" $CONTAINER_NAME 2>/dev/null)
    if [ ! -z "$STATS" ]; then
        CPU=$(echo $STATS | jq -r '.CPUPerc' | sed 's/%//')
        MEM=$(echo $STATS | jq -r '.MemUsage' | cut -d'/' -f1)
        echo "    {\"time\": $i, \"cpu_percent\": $CPU, \"memory\": \"$MEM\"}," >> $OUTPUT_FILE
    fi
    sleep 1
done

# Close JSON
echo "  ]," >> $OUTPUT_FILE
echo "  \"end_time\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"" >> $OUTPUT_FILE
echo "}" >> $OUTPUT_FILE

# Calculate averages
AVG_CPU=$(cat $OUTPUT_FILE | jq '[.metrics[].cpu_percent] | add/length')
echo "Average CPU: ${AVG_CPU}%"