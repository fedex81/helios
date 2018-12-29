package omegadrive;

import omegadrive.util.RegionDetector;

import java.nio.file.Path;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface GenesisProvider {

    int NTSC_MCLOCK_MHZ = 53693175;
    int PAL_MCLOCK_MHZ = 53203424;

    RegionDetector.Region getRegion();

    default long getRegionCode() {
        return getRegion().getVersionCode();
    }

    void renderScreen(int[][] screenData);

    void handleNewRom(Path file);

    void handleCloseRom();

    void handleCloseApp();

    void handleLoadState(Path file);

    boolean isRomRunning();

    boolean isSoundWorking();

    void toggleMute();

    void toggleSoundRecord();

    void setFullScreen(boolean value);

    void init();

    void setPlayers(int i);

    void setDebug(boolean value);

    String getRomName();

    void handleSaveState(Path file);
}
