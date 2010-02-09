#!/bin/sh

set -e

# Required flags:
#   CREDS  -- basic auth credentials, in form username:password
#   VERSION -- the version of the bundle
#   SIGNING_KEY -- the signing key to use
#   GNUPG_PATH -- the path to the home directory for gnupg

for ARTIFACT_NAME in $@; do
  echo "Uploading $ARTIFACT_NAME"

  rm -f $ARTIFACT_NAME.asc
  gpg --homedir $GNUPG_PATH/.gnupg --local-user $SIGNING_KEY --no-tty --armor --detach-sign --output $ARTIFACT_NAME.asc $ARTIFACT_NAME
  curl -XPUT -d @$ARTIFACT_NAME http://$CREDS@oss.sonatype.org/service/local/staging/deploy/maven2/com/rabbitmq/amqp-client/$VERSION/$ARTIFACT_NAME
  curl -XPUT -d @$ARTIFACT_NAME.asc http://$CREDS@oss.sonatype.org/service/local/staging/deploy/maven2/com/rabbitmq/amqp-client/$VERSION/$ARTIFACT_NAME.asc
done
