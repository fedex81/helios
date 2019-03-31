package omegadrive.ui;

import omegadrive.system.SystemProvider;
import omegadrive.util.FileLoader;
import omegadrive.util.VideoMode;

import java.awt.event.KeyAdapter;
import java.time.LocalDate;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface GenesisWindow {

    String APP_NAME = "Helios";
    String VERSION = FileLoader.loadVersionFromManifest();
    String FRAME_TITLE_HEAD = APP_NAME + " " + VERSION;


    void addKeyListener(KeyAdapter keyAdapter);

    void setTitle(String rom);

    void init();

    void renderScreen(int[][] data, String label, VideoMode videoMode);

    void resetScreen();

    void setFullScreen(boolean value);

    String getRegionOverride();

    void reloadSystem(SystemProvider systemProvider);

    default String getAboutString() {
        int year = LocalDate.now().getYear();
        String yrString = year == 2018 ? "2018" : "2018-" + year;
        String res = FRAME_TITLE_HEAD + "\nA Sega Megadrive (Genesis) emulator, written in Java";
        res += "\n\nCopyright " + yrString + ", Federico Berti";
        res += "\n\nSee CREDITS.TXT for more information";
        res += "\n\nReleased under GPL v.3.0 license.";
        return res;
    }

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

        @Override
        public String getRegionOverride() {
            return null;
        }

        @Override
        public void reloadSystem(SystemProvider systemProvider) {

        }
    };


}
