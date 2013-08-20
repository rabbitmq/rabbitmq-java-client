#!/bin/sh

set -e

# Required flags:
#   CREDS  -- basic auth credentials, in form username:password
#   VERSION -- the version of the bundle
#   SIGNING_KEY -- the signing key to use
#   GNUPG_PATH -- the path to the home directory for gnupg

NEXUS_ROOT="http://$CREDS@oss.sonatype.org/service/local/staging/deploy/maven2/com/rabbitmq/amqp-client/$VERSION"
unset http_proxy
unset https_proxy
unset no_proxy

for artifact in $@; do
  echo "Uploading $artifact"

  rm -f $artifact.asc
  gpg --homedir $GNUPG_PATH/.gnupg --local-user $SIGNING_KEY --no-tty --armor --detach-sign --output $artifact.asc $artifact
  for ext in '' .asc ; do
    curl --upload-file $artifact$ext $NEXUS_ROOT/$artifact$ext
    for sum in md5 sha1 ; do
      ${sum}sum $artifact$ext | (read a rest ; echo -n "$a") >$artifact$ext.$sum
      curl --upload-file $artifact$ext.$sum $NEXUS_ROOT/$artifact$ext.$sum
    done
  done
done
