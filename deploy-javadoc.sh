#!/usr/bin/env bash

DEPLOY_DIRECTORY=api/current
TAG=$(git describe --exact-match --tags $(git log -n1 --pretty='%h'))

make deps
./mvnw -q clean javadoc:javadoc -Dmaven.javadoc.failOnError=false

if [ -e target/javadoc-bundle-options/element-list ]
  then cp target/javadoc-bundle-options/element-list target/reports/apidocs/package-list
fi

git co gh-pages
rm -rf $DEPLOY_DIRECTORY/*
cp -r target/reports/apidocs/* $DEPLOY_DIRECTORY
git add $DEPLOY_DIRECTORY
git commit -m "Add Javadoc for $TAG"
git push origin gh-pages


