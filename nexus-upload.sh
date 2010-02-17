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
  md5sum $ARTIFACT_NAME | cut -f1 -d' ' >$ARTIFACT_NAME.md5
  md5sum $ARTIFACT_NAME.asc | cut -f1 -d' ' >$ARTIFACT_NAME.asc.md5
  sha1sum $ARTIFACT_NAME | cut -f1 -d' ' >$ARTIFACT_NAME.sha1
  sha1sum $ARTIFACT_NAME.asc | cut -f1 -d' ' >$ARTIFACT_NAME.asc.sha1
  curl -XPUT -d @$ARTIFACT_NAME http://$CREDS@oss.sonatype.org/service/local/staging/deploy/maven2/com/rabbitmq/amqp-client/$VERSION/$ARTIFACT_NAME

  for EXT in md5 sha1 asc asc.md5 asc.sha1; do
    curl -XPUT -d @$ARTIFACT_NAME.$EXT http://$CREDS@oss.sonatype.org/service/local/staging/deploy/maven2/com/rabbitmq/amqp-client/$VERSION/$ARTIFACT_NAME.$EXT
  done
done
