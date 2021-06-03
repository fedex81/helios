package omegadrive.system.nes;

import com.grapeshot.halfnes.NES;
import com.grapeshot.halfnes.audio.AudioOutInterface;
import com.grapeshot.halfnes.ui.ControllerImpl;
import com.grapeshot.halfnes.ui.GUIInterface;
import com.grapeshot.halfnes.video.RGBRenderer;
import omegadrive.system.SystemProvider;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.BaseVdpAdapter;
import omegadrive.vdp.model.BaseVdpProvider;

import java.nio.file.Path;

import static omegadrive.input.InputProvider.PlayerNumber;
import static omegadrive.input.KeyboardInputHelper.keyboardStringBindings;

/**
 * NesHelper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class NesHelper {

    public static final ControllerImpl cnt1 = new ControllerImpl(0, keyboardStringBindings.row(PlayerNumber.P1));
    public static final ControllerImpl cnt2 = new ControllerImpl(1, keyboardStringBindings.row(PlayerNumber.P2));


    public static NesHelper.NesGUIInterface createNes(Path romFile, Nes nesSys, AudioOutInterface audio) {
        NES nes = new NES(audio);
        NesHelper.NesGUIInterface gui = NesHelper.createGuiWrapper(nes, nesSys);
        nes.setControllers(cnt1, cnt2);
        nes.setGui(gui);
        nes.setRom(romFile.toAbsolutePath().toString());
        return gui;
    }

    public static NesGUIInterface createGuiWrapper(NES instance1,
                                                   SystemProvider.NewFrameListener nesSystem) {
        return new NesGUIInterface() {

            private NES localInstance = instance1;
            private RGBRenderer renderer = new RGBRenderer();
            private int[] screen;
            private BaseVdpProvider vdpProvider = BaseVdpAdapter.getVdpProviderWrapper(VideoMode.NTSCU_H32_V30, this);

            @Override
            public BaseVdpProvider getVdpProvider() {
                return vdpProvider;
            }

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
                renderer.renderData(frame, bgcolor, dotcrawl);
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

    public interface NesGUIInterface extends GUIInterface, BaseVdpAdapter.ScreenDataSupplier {
        int[] getScreen();

        BaseVdpProvider getVdpProvider();
    }
}
