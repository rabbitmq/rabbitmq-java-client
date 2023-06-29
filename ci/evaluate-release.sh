#!/usr/bin/env bash

source ./release-versions.txt

if [[ $RELEASE_VERSION == *[RCM]* ]]
then
  echo "prerelease=true" >> $GITHUB_ENV
  echo "ga_release=false" >> $GITHUB_ENV
  echo "maven_server_id=packagecloud-rabbitmq-maven-milestones" >> $GITHUB_ENV
else
  echo "prerelease=false" >> $GITHUB_ENV
  echo "ga_release=true" >> $GITHUB_ENV
  echo "maven_server_id=ossrh" >> $GITHUB_ENV
fi