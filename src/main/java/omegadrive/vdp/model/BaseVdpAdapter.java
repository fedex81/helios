package omegadrive.vdp.model;

import omegadrive.util.RegionDetector;
import omegadrive.util.VideoMode;

/**
 * BaseVdpAdapter
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class BaseVdpAdapter implements BaseVdpProvider {
    public static BaseVdpProvider getVdpProviderWrapper(VideoMode videoMode, ScreenDataSupplier screenDataSupplier) {
        return new BaseVdpAdapter() {
            @Override
            public VideoMode getVideoMode() {
                return videoMode;
            }

            @Override
            public int[] getScreenDataLinear() {
                return screenDataSupplier.getScreen();
            }
        };
    }

    @Override
    public void init() {

    }

    @Override
    public int runSlot() {
        return 0;
    }

    @Override
    public int getRegisterData(int reg) {
        return 0;
    }

    @Override
    public void updateRegisterData(int reg, int data) {

    }

    @Override
    public VdpMemory getVdpMemory() {
        return null;
    }

    @Override
    public VideoMode getVideoMode() {
        return null;
    }

    @Override
    public int[] getScreenDataLinear() {
        return new int[0];
    }

    @Override
    public void setRegion(RegionDetector.Region region) {

    }

    public interface ScreenDataSupplier {
        int[] getScreen();
    }
};
