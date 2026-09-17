#!/usr/bin/env bash

LOCAL_SCRIPT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

RABBITMQ_IMAGE=${RABBITMQ_IMAGE:-rabbitmq:4.3}
ERLANG_VERSION=${ERLANG_VERSION:-27}

wait_for_message() {
  while ! docker logs "$1" | grep -q "$2";
  do
      sleep 5
      echo "Waiting 5 seconds for $1 to start..."
  done
}

rm -rf rabbitmq-configuration
mkdir -p rabbitmq-configuration/tls

make -C "${PWD}"/tls-gen/basic

rm -rf rabbitmq-configuration
mkdir -p rabbitmq-configuration/tls
cp -R "${PWD}"/tls-gen/basic/result/* rabbitmq-configuration/tls
chmod o+r rabbitmq-configuration/tls/*
chmod g+r rabbitmq-configuration/tls/*

if [ "$ERLANG_VERSION" -lt 28 ]; then

# Erlang < 28

echo "loopback_users = none

listeners.ssl.default = 5671

ssl_options.cacertfile = /etc/rabbitmq/tls/ca_certificate.pem
ssl_options.certfile   = /etc/rabbitmq/tls/server_$(hostname)_certificate.pem
ssl_options.keyfile    = /etc/rabbitmq/tls/server_$(hostname)_key.pem
ssl_options.verify     = verify_peer
ssl_options.fail_if_no_peer_cert = false
ssl_options.honor_cipher_order   = true

auth_mechanisms.1 = PLAIN
auth_mechanisms.2 = ANONYMOUS
auth_mechanisms.3 = AMQPLAIN
auth_mechanisms.4 = EXTERNAL
auth_mechanisms.5 = RABBIT-CR-DEMO" >> rabbitmq-configuration/rabbitmq.conf

else

# Erlang >= 28

echo "loopback_users = none

listeners.ssl.default = 5671

auth_mechanisms.1 = PLAIN
auth_mechanisms.2 = ANONYMOUS
auth_mechanisms.3 = AMQPLAIN
auth_mechanisms.4 = EXTERNAL
auth_mechanisms.5 = RABBIT-CR-DEMO" >> rabbitmq-configuration/rabbitmq.conf

echo "[
  {rabbit, [
    {ssl_options, [
      {cacertfile, \"/etc/rabbitmq/tls/ca_certificate.pem\"},
      {certfile,   \"/etc/rabbitmq/tls/server_$(hostname)_certificate.pem\"},
      {keyfile,    \"/etc/rabbitmq/tls/server_$(hostname)_key.pem\"},
      {verify,     verify_peer},
      {fail_if_no_peer_cert, false},
      {depth,      1},
      {versions,   ['tlsv1.3']},
      {supported_groups, [x25519mlkem768, x25519, secp256r1]},
      {honor_cipher_order, true},
      {ciphers,    [
                %% TLS 1.3 Ciphers
                \"TLS_AES_256_GCM_SHA384\",
                \"TLS_CHACHA20_POLY1305_SHA256\",
                \"TLS_AES_128_GCM_SHA256\",
                %% TLS 1.2 Ciphers
                \"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384\",
                \"TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384\",
                \"TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256\",
                \"TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256\"
                ]}
    ]}
  ]}
]." >> rabbitmq-configuration/advanced.config

fi

echo "Running RabbitMQ ${RABBITMQ_IMAGE}"

docker rm -f rabbitmq 2>/dev/null || echo "rabbitmq was not running"
docker run -d --name rabbitmq \
    -p 5671:5671 -p 5672:5672 \
    -v "${PWD}"/rabbitmq-configuration:/etc/rabbitmq \
    "${RABBITMQ_IMAGE}"

wait_for_message rabbitmq "completed with"

docker exec rabbitmq rabbitmqctl enable_feature_flag --opt-in khepri_db
docker exec rabbitmq rabbitmq-diagnostics erlang_version
docker exec rabbitmq rabbitmqctl version
