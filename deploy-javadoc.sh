#!/usr/bin/env bash

DEPLOY_DIRECTORY=api/current
TAG=$(git describe --exact-match --tags $(git log -n1 --pretty='%h'))

./mvnw -q clean javadoc:javadoc -Dmaven.javadoc.failOnError=false
git co gh-pages
rm -rf $DEPLOY_DIRECTORY/*
cp -r target/site/apidocs/* $DEPLOY_DIRECTORY
git add $DEPLOY_DIRECTORY
git commit -m "Add Javadoc for $TAG"
git push origin gh-pages


