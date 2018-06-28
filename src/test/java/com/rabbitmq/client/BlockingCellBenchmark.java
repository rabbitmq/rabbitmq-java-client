package com.rabbitmq.client;

import com.rabbitmq.utility.BlockingCell;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 */
public class BlockingCellBenchmark {

    public static void main(String[] args) {

    }

    public void legacyBlockingCell() {
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 1000; i++) {
            final BlockingCell<String> cell = new BlockingCell<String>();
            executorService.submit(new Callable<Void>() {

                @Override
                public Void call() throws Exception {
                    cell.set("whatever");
                    return null;
                }
            });

            cell.uninterruptibleGet();

        }

    }

}
