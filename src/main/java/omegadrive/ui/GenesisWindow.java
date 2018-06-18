package omegadrive.ui;

import omegadrive.util.VideoMode;

import java.awt.event.KeyAdapter;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface GenesisWindow {

    void addKeyListener(KeyAdapter keyAdapter);

    void setTitle(String rom);

    void init();

    void renderScreen(int[][] data, String label, VideoMode videoMode);

    void resetScreen();

    void setFullScreen(boolean value);
}
