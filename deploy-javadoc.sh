#!/usr/bin/env bash

DEPLOY_PATH=/home/rabbitmq/extras/releases/rabbitmq-java-client/current-javadoc

# RSync user/host to deploy to.  Mandatory.
DEPLOY_USERHOST=


# Imitate make-style variable settings as arguments
while [[ $# -gt 0 ]] ; do
  declare "$1"
  shift
done

mandatory_vars="DEPLOY_USERHOST"
optional_vars="DEPLOY_PATH"

function die () {
  echo "$@" 2>&1
  exit 1
}

# Check mandatory settings
for v in $mandatory_vars ; do
    [[ -n "${!v}" ]] || die "$v not set"
done

echo "Settings:"
for v in $mandatory_vars $optional_vars ; do
    echo "${v}=${!v}"
done

set -e -x

mvn -q clean javadoc:javadoc -Dmaven.javadoc.failOnError=false

ssh $DEPLOY_USERHOST \
		"rm -rf $DEPLOY_PATH; \
		 mkdir -p $DEPLOY_PATH"

rsync -rpl target/site/apidocs/ $DEPLOY_USERHOST:$DEPLOY_PATH


