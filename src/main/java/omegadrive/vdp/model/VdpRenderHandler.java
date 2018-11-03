package omegadrive.vdp.model;

import omegadrive.util.VideoMode;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface VdpRenderHandler {

    void dumpScreenData();

    int[][] renderFrame();

    void setVideoMode(VideoMode videoMode);

    void renderLine(int line);
}
