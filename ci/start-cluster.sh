#!/usr/bin/env bash

RABBITMQ_IMAGE=${RABBITMQ_IMAGE:-rabbitmq:4.3}
ERLANG_VERSION=${ERLANG_VERSION:-27}

wait_for_message() {
  while ! docker logs "$1" | grep -q "$2";
  do
      sleep 2
      echo "Waiting 2 seconds for $1 to start..."
  done
}

rm -rf rabbitmq-configuration
mkdir -p rabbitmq-configuration/tls

make -C "${PWD}"/tls-gen/basic

rm -rf rabbitmq-configuration
mkdir -p rabbitmq-configuration/tls
cp -R "${PWD}"/tls-gen/basic/result/* rabbitmq-configuration/tls
mv rabbitmq-configuration/tls/server_$(hostname)_certificate.pem rabbitmq-configuration/tls/server_certificate.pem
mv rabbitmq-configuration/tls/server_$(hostname)_key.pem rabbitmq-configuration/tls/server_key.pem
chmod o+r rabbitmq-configuration/tls/*
chmod g+r rabbitmq-configuration/tls/*

rm -rf "${PWD}"/ci/cluster/configuration
mkdir "${PWD}"/ci/cluster/configuration

if [ "$ERLANG_VERSION" -lt 28 ]; then

# Erlang < 28
cp "${PWD}"/ci/cluster/rabbitmq_pre_erlang_28.conf "${PWD}"/ci/cluster/configuration/rabbitmq.conf

else

# Erlang >= 28
cp "${PWD}"/ci/cluster/rabbitmq_post_erlang_28.conf "${PWD}"/ci/cluster/configuration/rabbitmq.conf
cp "${PWD}"/ci/cluster/advanced.config "${PWD}"/ci/cluster/configuration/advanced.config

fi

docker compose --file ci/cluster/docker-compose.yml down
docker compose --file ci/cluster/docker-compose.yml up --detach

wait_for_message rabbitmq0 "completed with"

docker exec rabbitmq0 rabbitmqctl await_online_nodes 3

docker exec rabbitmq0 rabbitmqctl enable_feature_flag --opt-in khepri_db
docker exec rabbitmq1 rabbitmqctl enable_feature_flag --opt-in khepri_db
docker exec rabbitmq2 rabbitmqctl enable_feature_flag --opt-in khepri_db

docker exec rabbitmq0 rabbitmqctl cluster_status

docker compose --file ci/cluster/docker-compose.yml ps
