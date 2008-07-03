#!/bin/sh
RABBIT_JARS=
for d in *.jar
do
    RABBIT_JARS="$d:$RABBIT_JARS"
done
exec java -cp "$RABBIT_JARS" "$@"
