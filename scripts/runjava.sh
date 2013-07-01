#!/bin/sh
RABBIT_JARS=
for d in `dirname $0`/*.jar
do
    RABBIT_JARS="$d:$RABBIT_JARS"
done
exec java -cp "$RABBIT_JARS" "$@"
