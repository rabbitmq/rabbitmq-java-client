#!/bin/sh

run() {
    echo "=== running with '$2'"
    sh `dirname $0`/runjava.sh com.rabbitmq.examples.PerfTest -h $1 -z 10 -i 20 $2
    sleep 2
}

for sz in "" "-s 1000"; do
    for pers in "" "-f persistent"; do
        for args in \
            "" \
            "-a" \
            "-m 1" \
            "-m 1 -n 1" \
            "-m 10" \
            "-m 10 -n 10" \
            ; do
          run $1 "${args} ${pers} ${sz}"
        done
    done
done

for args in "-a -f mandatory" "-a -f mandatory -f immediate"; do
    run $1 "$args"
done
