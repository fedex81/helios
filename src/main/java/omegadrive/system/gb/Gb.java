package omegadrive.system.gb;

import eu.rekawek.coffeegb.gui.Emulator;
import eu.rekawek.coffeegb.gui.SwingController;
import eu.rekawek.coffeegb.sound.SoundOutput;
import omegadrive.SystemLoader;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.input.InputProvider;
import omegadrive.input.KeyboardInputHelper;
import omegadrive.joypad.ExternalPad;
import omegadrive.joypad.JoypadProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.system.BaseSystem;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.BaseVdpAdapter;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Gb
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class Gb extends BaseSystem<BaseBusProvider> {

    private static final Logger LOG = LogHelper.getLogger(Gb.class.getSimpleName());

    private Emulator emulator;
    private final SwingController controller;

    protected Gb(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        super(emuFrame);
        this.systemType = systemType;
        Properties properties = addKeyboardBindings(new Properties());
        controller = SwingController.createIntController(properties);
    }

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        return new Gb(systemType, emuFrame);
    }

    @Override
    public void init() {
        bus = SysUtil.NO_OP_BUS;
        vdp = SysUtil.NO_OP_VDP_PROVIDER;
        memory = MemoryProvider.NO_MEMORY;
        joypad = ExternalPad.createTwoButtonsPad(controller);
        initCommon();
    }

    private void initCommon() {
        inputProvider = InputProvider.createInstance(joypad);
        reloadWindowState();
    }

    @Override
    protected void loop() {
        LOG.info("Starting game loop");
        targetNs = (long) (getRegion().getFrameIntervalMs() * Util.MILLI_IN_NS);
        String[] args = new String[]{getRomPath().toAbsolutePath().toString()};
        emulator = createEmulator(args);
        emulator.runOnCurrentThread();
        LOG.info("Exiting rom thread loop");
    }

    private Emulator createEmulator(String[] args) {
        try {
            HeliosDisplay display = new HeliosDisplay(this, emuFrame);
            emulator = new Emulator(args, display, (SoundOutput) sound.getFm(), controller);
            vdp = BaseVdpAdapter.getVdpProviderWrapper(VideoMode.NTSCJ_H20_V18, display);
        } catch (Exception e) {
            LOG.error("Unable to start emulation: {}", getRomPath(), e);
        }
        return emulator;
    }

    private Properties addKeyboardBindings(Properties properties) {
        Map<JoypadProvider.JoypadButton, Integer> map = KeyboardInputHelper.keyboardBindings.row(InputProvider.PlayerNumber.P1);
        map.forEach((k, v) -> properties.put("btn_" + k.getMnemonic(), v));
        return properties;
    }

    @Override
    protected void initAfterRomLoad() {
        super.initAfterRomLoad();
        resetAfterRomLoad();
    }

    @Override
    protected void handleCloseRom() {
        Optional.ofNullable(emulator).ifPresent(Emulator::stop);
        super.handleCloseRom();
    }

    @Override
    protected void processSaveState() {
        //DO NOTHING
    }

    @Override
    protected void resetCycleCounters(int counter) {
        //DO NOTHING
    }

    @Override
    protected void updateVideoMode(boolean force) {
        videoMode = vdp.getVideoMode();
    }

    @Override
    protected RegionDetector.Region getRomRegion() {
        return RegionDetector.Region.USA;
    }
}
