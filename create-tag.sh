#!/usr/bin/env bash

set -x

source "release-versions.txt"

mvn release:clean release:prepare -Darguments="-DskipTests" \
				  --batch-mode -Dtag="v$RELEASE_VERSION" \
					       -DreleaseVersion=$RELEASE_VERSION \
					       -DdevelopmentVersion=$DEVELOPMENT_VERSION
