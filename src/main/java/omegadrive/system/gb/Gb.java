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
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.system.BaseSystem;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.BaseVdpAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private static final Logger LOG = LogManager.getLogger(Gb.class.getSimpleName());

    private SystemLoader.SystemType systemType;
    private Emulator emulator;
    private HeliosDisplay display;
    private Properties properties;
    private SwingController controller;

    protected Gb(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        super(emuFrame);
        this.systemType = systemType;
        properties = addKeyboardBindings(new Properties());
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
        targetNs = (long) (region.getFrameIntervalMs() * Util.MILLI_IN_NS);
        String[] args = new String[]{romFile.toAbsolutePath().toString()};
        emulator = createEmulator(args);
        emulator.runOnCurrentThread();
        LOG.info("Exiting rom thread loop");
    }

    private Emulator createEmulator(String[] args) {
        try {
            display = new HeliosDisplay(this, emuFrame);
            emulator = new Emulator(args, display, (SoundOutput) sound.getFm(), controller);
            vdp = BaseVdpAdapter.getVdpProviderWrapper(VideoMode.NTSCJ_H20_V18, display);
        } catch (Exception e) {
            LOG.error("Unable to start emulation: {}", romFile, e);
        }
        return emulator;
    }

    private Properties addKeyboardBindings(Properties properties) {
        Map<JoypadProvider.JoypadButton, Integer> map = KeyboardInputHelper.keyboardBindings.row(InputProvider.PlayerNumber.P1);
        map.forEach((k, v) -> {
            properties.put("btn_" + k.getMnemonic(), v);
        });
        return properties;
    }

    @Override
    protected void initAfterRomLoad() {
        sound = AbstractSoundManager.createSoundProvider(systemType, region);
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
    protected RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOverride) {
        return RegionDetector.Region.USA;
    }

    @Override
    public SystemLoader.SystemType getSystemType() {
        return systemType;
    }
}
