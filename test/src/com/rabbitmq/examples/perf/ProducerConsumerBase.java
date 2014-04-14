package com.rabbitmq.examples.perf;

/**
 *
 */
public class ProducerConsumerBase {
    protected float rateLimit;
    protected long  lastStatsTime;
    protected int   msgCount;

    protected void delay(long now) {

        long elapsed = now - lastStatsTime;
        //example: rateLimit is 5000 msg/s,
        //10 ms have elapsed, we have sent 200 messages
        //the 200 msgs we have actually sent should have taken us
        //200 * 1000 / 5000 = 40 ms. So we pause for 40ms - 10ms
        long pause = (long) (rateLimit == 0.0f ?
            0.0f : (msgCount * 1000.0 / rateLimit - elapsed));
        if (pause > 0) {
            try {
                Thread.sleep(pause);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
