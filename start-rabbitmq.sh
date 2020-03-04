#!/bin/bash
set -euo pipefail

pushd rabbitmq
docker build -t rabbitmq-with-jms:latest .
popd

docker rm rabbit-with-jms || true

docker run \
  --name rabbit-with-jms \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=user \
  -e RABBITMQ_DEFAULT_PASS=password \
  rabbitmq-with-jms:latest