package omegadrive.vdp;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface VdpHLineProvider {

//        The counter is loaded with the contents of register #10 in the following
//        situations:
//
//        - Line zero of the frame.
//        - When the counter has expired.
//        - Lines 225 through 261. (note that line 224 is not included)

    /**
     * getHLinesCounter
     *
     * @return
     */
    int getHLinesCounter();
}
