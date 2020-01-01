package omegadrive.system.nes;

import com.grapeshot.halfnes.NES;
import com.grapeshot.halfnes.ui.ControllerImpl;
import com.grapeshot.halfnes.ui.GUIInterface;
import com.grapeshot.halfnes.video.RGBRenderer;
import omegadrive.Device;
import omegadrive.bus.BaseBusProvider;
import omegadrive.util.RegionDetector;
import omegadrive.util.Size;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.VdpMemory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * NesHelper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class NesHelper {

    public static final ControllerImpl cnt1 = new ControllerImpl(0);
    public static final ControllerImpl cnt2 = new ControllerImpl(1);
    public static final BaseBusProvider NO_OP_BUS = new BaseBusProvider() {
        @Override
        public long read(long address, Size size) {
            return 0;
        }

        @Override
        public void write(long address, long data, Size size) {

        }

        @Override
        public void writeIoPort(int port, int value) {

        }

        @Override
        public int readIoPort(int port) {
            return 0;
        }

        @Override
        public BaseBusProvider attachDevice(Device device) {
            return null;
        }

        @Override
        public <T extends Device> Optional<T> getDeviceIfAny(Class<T> clazz) {
            return Optional.empty();
        }
    };
    public static final BaseVdpProvider VDP_PROVIDER = new BaseVdpProvider() {
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
        public boolean isDisplayEnabled() {
            return false;
        }

        @Override
        public VideoMode getVideoMode() {
            return VideoMode.NTSCU_H32_V30;
        }

        @Override
        public int[] getScreenDataLinear() {
            return new int[0];
        }

        @Override
        public void setRegion(RegionDetector.Region region) {

        }
    };

    //blocking
    public static NesHelper.NesGUIInterface createNes(Path romFile, Nes nesSys) {
        NES nes = new NES();
        NesHelper.NesGUIInterface gui = NesHelper.createGuiWrapper(nes, nesSys);
        nes.setControllers(cnt1, cnt2);
        nes.setGui(gui);
        nes.setRom(romFile.toAbsolutePath().toString());
        return gui;
    }

    public static NesGUIInterface createGuiWrapper(NES instance1, Nes nesSystem) {
        return new NesGUIInterface() {

            private NES localInstance = instance1;
            private RGBRenderer renderer = new RGBRenderer();
            private int[] screen;

            @Override
            public NES getNes() {
                return localInstance;
            }

            @Override
            public void setNES(NES nes) {
                localInstance = nes;
            }

            @Override
            public void setFrame(int[] frame, int[] bgcolor, boolean dotcrawl) {
                renderer.render(frame, bgcolor, dotcrawl);
                screen = frame;
                nesSystem.newFrame();
            }

            @Override
            public int[] getScreen() {
                return screen;
            }

            @Override
            public void messageBox(String message) {

            }

            @Override
            public void run() {
                localInstance.run();
            }

            @Override
            public void render() {
                System.out.println("render");
            }

            @Override
            public void loadROMs(String path) {
                localInstance.loadROM(path);
            }

            @Override
            public void close() {
                localInstance.quit();
            }
        };

    }

    interface NesGUIInterface extends GUIInterface {
        int[] getScreen();
    }
}
