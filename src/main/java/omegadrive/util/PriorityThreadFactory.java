package omegadrive.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class PriorityThreadFactory implements ThreadFactory {

    private AtomicInteger threadNumber = new AtomicInteger();
    private String namePrefix;
    private int threadPriority;

    public PriorityThreadFactory(int priority, String namePrefix) {
        this.namePrefix = namePrefix;
        this.threadPriority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
        t.setPriority(threadPriority);
        return t;
    }
}
