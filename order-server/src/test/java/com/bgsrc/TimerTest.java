package com.bgsrc;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class TimerTest {

    public static void main(String[] args) throws InterruptedException {
        Timer timer = new Timer("test-timer", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("running.");
            }
        }, 0, 1000);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("close timer.");
            timer.cancel();
        }));

        TimeUnit.SECONDS.sleep(5);
    }
}
