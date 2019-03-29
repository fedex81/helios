package omegadrive;

/**
 * ${FILE}
 * Marker Interface
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public interface Device {

    default void init() {
        //DO NOTHING
    }

    default void reset() {
        //DO NOTHING
    }
}
