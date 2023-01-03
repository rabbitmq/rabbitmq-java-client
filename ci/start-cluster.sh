#!/usr/bin/env bash

LOCAL_SCRIPT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

RABBITMQ_IMAGE_TAG=${RABBITMQ_IMAGE_TAG:-3.11}
RABBITMQ_IMAGE=${RABBITMQ_IMAGE:-rabbitmq}

wait_for_message() {
  while ! docker logs "$1" | grep -q "$2";
  do
      sleep 5
      echo "Waiting 5 seconds for $1 to start..."
  done
}

make -C "${PWD}"/tls-gen/basic

mv tls-gen/basic/result/server_$(hostname -s)_certificate.pem tls-gen/basic/result/server_certificate.pem
mv tls-gen/basic/result/server_$(hostname -s)_key.pem tls-gen/basic/result/server_key.pem
mv tls-gen/basic/server_$(hostname -s) tls-gen/basic/server
mv tls-gen/basic/client_$(hostname -s) tls-gen/basic/client

mkdir -p rabbitmq-configuration/tls

cp -R "${PWD}"/tls-gen/basic/* rabbitmq-configuration/tls
chmod -R o+r rabbitmq-configuration/tls/*
./mvnw -q clean resources:testResources -Dtest-tls-certs.dir=/etc/rabbitmq/tls
cp target/test-classes/rabbit@localhost.config rabbitmq-configuration/rabbit@localhost.config
cp target/test-classes/hare@localhost.config rabbitmq-configuration/hare@localhost.config

echo "Running RabbitMQ ${RABBITMQ_IMAGE}:${RABBITMQ_IMAGE_TAG}"

docker rm -f rabbitmq 2>/dev/null || echo "rabbitmq was not running"
docker run -d --name rabbitmq \
    --network host \
    -v "${PWD}"/rabbitmq-configuration:/etc/rabbitmq \
    --env RABBITMQ_CONFIG_FILE=/etc/rabbitmq/rabbit@localhost.config \
    --env RABBITMQ_NODENAME=rabbit@$(hostname) \
    --env RABBITMQ_NODE_PORT=5672 \
    --env RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS="-setcookie do-not-do-this-in-production" \
    "${RABBITMQ_IMAGE}":"${RABBITMQ_IMAGE_TAG}"

# for CLI commands to share the same cookie
docker exec rabbitmq bash -c "echo 'do-not-do-this-in-production' > /var/lib/rabbitmq/.erlang.cookie"
docker exec rabbitmq chmod 0600 /var/lib/rabbitmq/.erlang.cookie

wait_for_message rabbitmq "completed with"

docker run -d --name hare \
    --network host \
    -v "${PWD}"/rabbitmq-configuration:/etc/rabbitmq \
    --env RABBITMQ_CONFIG_FILE=/etc/rabbitmq/hare@localhost.config \
    --env RABBITMQ_NODENAME=hare@$(hostname) \
    --env RABBITMQ_NODE_PORT=5673 \
    --env RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS="-setcookie do-not-do-this-in-production" \
    "${RABBITMQ_IMAGE}":"${RABBITMQ_IMAGE_TAG}"

# for CLI commands to share the same cookie
docker exec hare bash -c "echo 'do-not-do-this-in-production' > /var/lib/rabbitmq/.erlang.cookie"
docker exec hare chmod 0600 /var/lib/rabbitmq/.erlang.cookie

wait_for_message hare "completed with"

docker exec hare rabbitmqctl --node hare@$(hostname) status

docker exec rabbitmq rabbitmq-diagnostics --node rabbit@$(hostname) is_running
docker exec hare rabbitmq-diagnostics --node hare@$(hostname) is_running

docker exec hare rabbitmqctl --node hare@$(hostname) stop_app
docker exec hare rabbitmqctl --node hare@$(hostname) join_cluster rabbit@$(hostname)
docker exec hare rabbitmqctl --node hare@$(hostname) start_app

sleep 10

docker exec hare rabbitmqctl --node hare@$(hostname) await_startup

docker exec rabbitmq rabbitmq-diagnostics --node rabbit@$(hostname) erlang_version
docker exec rabbitmq rabbitmqctl --node rabbit@$(hostname) version
docker exec rabbitmq rabbitmqctl --node rabbit@$(hostname) status
docker exec rabbitmq rabbitmqctl --node rabbit@$(hostname) cluster_status