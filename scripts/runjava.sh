#!/bin/sh
RABBIT_JARS=
SCRIPT_DIR=`dirname $0`
for d in *.jar $SCRIPT_DIR/../build/lib/*.jar $SCIRPT_DIR/../lib/*.jar
do
    RABBIT_JARS="$d:$RABBIT_JARS"
done
exec java -cp "$RABBIT_JARS" "$@"