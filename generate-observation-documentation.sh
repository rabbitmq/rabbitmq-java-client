#!/usr/bin/env bash

./mvnw -q -P '!setup-test-cluster' test-compile exec:java \
  -Dexec.mainClass=io.micrometer.docs.DocsGeneratorCommand \
  -Dexec.classpathScope="test" \
  -Dexec.args='src/main/java/com/rabbitmq/client/observation/micrometer .* target/micrometer-observation-docs'