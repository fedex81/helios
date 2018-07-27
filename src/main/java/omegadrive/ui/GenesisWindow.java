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

    GenesisWindow HEADLESS_INSTANCE = new GenesisWindow() {
        @Override
        public void addKeyListener(KeyAdapter keyAdapter) {

        }

        @Override
        public void setTitle(String rom) {

        }

        @Override
        public void init() {

        }

        @Override
        public void renderScreen(int[][] data, String label, VideoMode videoMode) {

        }

        @Override
        public void resetScreen() {

        }

        @Override
        public void setFullScreen(boolean value) {

        }
    };
}
