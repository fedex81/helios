package omegadrive.vdp.util;

/**
 * UpdatableViewer
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public interface UpdatableViewer {
    UpdatableViewer NO_OP_VIEWER = () -> {
    };

    void update();

    default void updateLine(int line) {
        //NO OP
    }

    default void reset() {
        //NO_OP
    }
}
