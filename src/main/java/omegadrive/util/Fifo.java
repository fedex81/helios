package omegadrive.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.Arrays;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface Fifo<T> {

    void push(T data);

    T pop();

    T peek();

    boolean isEmpty();

    boolean isFull();

    default int isEmptyBit() {
        return isEmpty() ? 1 : 0;
    }

    default int isFullBit() {
        return isFull() ? 1 : 0;
    }

    default void clear() {
        while (!isEmpty()) {
            pop();
        }
    }

    static Fifo<Integer> createIntegerFixedSizeFifo(int fifoSize) {
        FixedSizeFifo<Integer> f = new FixedSizeFifo<Integer>(fifoSize);
        f.fifo = new Integer[fifoSize];
        Arrays.fill(f.fifo, 0);
        return f;
    }

    class FixedSizeFifo<T> implements Fifo<T> {

        protected final static Logger LOG = LogManager.getLogger(FixedSizeFifo.class.getSimpleName());
        public static final boolean logEnable = false;

        public static final boolean printToSysOut = false;
        private int popPointer;
        protected int pushPointer;
        private final int fifoSize;
        private int currentSize;

        protected T[] fifo;

        public FixedSizeFifo(int size) {
            this.fifoSize = size;
        }

        @Override
        public void push(T data) {
            if (isFull()) {
                LOG.info("FIFO full");
                return;
            }
            fifo[pushPointer] = data;
            pushPointer = (pushPointer + 1) % fifoSize;
            currentSize++;
            logState(data, "push");
        }

        @Override
        public T pop() {
            if (isEmpty()) {
                LOG.info("FIFO empty");
                return null;
            }
            T entry = fifo[popPointer];
            popPointer = (popPointer + 1) % fifoSize;
            currentSize--;
            logState(entry, "pop");
            return entry;
        }


        public T peek() {
            return fifo[popPointer];
        }

        @Override
        public boolean isEmpty() {
            return currentSize == 0;
        }

        @Override
        public boolean isFull() {
            return currentSize >= fifoSize;
        }

        protected void logState(T entry, String type) {
            if (logEnable) {
                ParameterizedMessage pm = new ParameterizedMessage(
                        "Fifo {}: {}, push: {}, pop: {}, size: {}\nstate: {}", type,
                        entry, pushPointer, popPointer, currentSize, Arrays.toString(fifo));
                String str = pm.getFormattedMessage();
                LOG.info(str);
                if (printToSysOut) {
                    System.out.println(str);
                }

            }
        }
    }
}
