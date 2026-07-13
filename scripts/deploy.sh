#!/bin/bash
set -e

IMAGE="$1"

set -a
# shellcheck disable=SC1090
source ~/insighton-config/gateway.env
set +a

deploy_replica() {
  local name=$1
  local port=$2

  echo "----- Deploying $name (port $port) -----"

  docker stop -t 15 "$name" > /dev/null 2>&1 || true
  docker rm "$name" > /dev/null 2>&1 || true

  docker run -d \
    --name "$name" \
    --network host \
    -e SERVER_PORT="$port" \
    -e EUREKA_USERNAME="$EUREKA_USERNAME" \
    -e EUREKA_PASSWORD="$EUREKA_PASSWORD" \
    --restart unless-stopped \
    "$IMAGE"

  echo "Waiting for $name to become healthy..."
  for i in $(seq 1 30); do
    if curl -sf "http://127.0.0.1:${port}/actuator/health" > /dev/null 2>&1; then
      echo "$name is healthy (attempt $i)"
      return 0
    fi
    sleep 2
  done

  echo "$name failed health check!"
  return 1
}

echo "Pulling image $IMAGE"
docker pull "$IMAGE"

deploy_replica "insighton-gateway-1" "10442"
deploy_replica "insighton-gateway-2" "10443"

echo "Rolling deployment complete!"