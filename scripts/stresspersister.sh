#!/bin/sh

commentText=$1
shift

if [ -z "$commentText" ]; then
    echo "Comment text must be supplied!"
    exit 1
fi

echo "Comment text: $commentText. Press enter to continue."
read dummy

function run1 {
    (while true; do (date +%s.%N; ps ax -o '%mem rss sz vsz args' | grep "beam.*-s rabbit" | grep -v grep) | tr '\n' ' ' | awk '{print $1,$2/100,$3,$4,$5}'; sleep 1; done) > memlog.txt &
    memlogger=$!
    echo "STARTED MEMLOGGER $memlogger"
    sleep 2
    sh ./runjava.sh com.rabbitmq.examples.StressPersister -B $1 -b $2 -C $commentText | tee stressoutput.txt
    logfile=$(head -1 stressoutput.txt)
    sleep 2
    kill $memlogger
    echo "STOPPED MEMLOGGER $memlogger"
    baselog=$(basename $logfile .out)
    mv memlog.txt $baselog.mem
    grep -v '^#' $logfile > stressoutput.txt
    mv stressoutput.txt $logfile
}

function run32b {
    run1 32b 5000
    run1 32b 10000
    run1 32b 20000
    run1 32b 40000
    run1 32b 80000
}

function run1m {
    run1 1m 125
    run1 1m 250
    run1 1m 500
    run1 1m 1000
    run1 1m 2000
    run1 1m 4000
}

function chartall {
    for logfile in *.out
    do
	echo $logfile
	baselog=$(basename $logfile .out)
	firsttimestamp=$(cat $baselog.mem | head -1 | awk '{print $1}')
	cat > $baselog.gnuplot <<EOF
set terminal png size 1024, 768
set logscale y
set xlabel "Time, seconds"
set ylabel "Round-trip time, microseconds"
set y2label "VSZ, megabytes"
set y2range [0 : 1048576]
set y2tics
set ytics nomirror
set autoscale y2
plot '$logfile' using ((\$1 / 1000) - $firsttimestamp):2 title 'RTT' axes x1y1 with lines, \
     '$baselog.mem' using (\$1 - $firsttimestamp):(\$5 / 1024) title 'VSZ' axes x1y2 with lines
EOF
	gnuplot $baselog.gnuplot > $baselog.png
    done
}

run32b
run1m
chartall
