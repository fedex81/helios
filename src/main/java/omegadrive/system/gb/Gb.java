package omegadrive.system.gb;

import eu.rekawek.coffeegb.gui.Emulator;
import eu.rekawek.coffeegb.gui.SwingController;
import eu.rekawek.coffeegb.sound.SoundOutput;
import omegadrive.SystemLoader;
import omegadrive.bus.BaseBusProvider;
import omegadrive.input.InputProvider;
import omegadrive.input.KeyboardInputHelper;
import omegadrive.joypad.GbPad;
import omegadrive.joypad.JoypadProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.system.BaseSystem;
import omegadrive.system.SystemProvider;
import omegadrive.system.nes.NesHelper;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
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
public class Gb extends BaseSystem<BaseBusProvider, BaseStateHandler> {

    private static Logger LOG = LogManager.getLogger(Gb.class.getSimpleName());

    protected long startCycle = System.nanoTime();
    protected int elapsedNs;
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
        bus = NesHelper.NO_OP_BUS;
        vdp = NesHelper.VDP_PROVIDER;
        memory = MemoryProvider.NO_MEMORY;
        joypad = new GbPad(controller);
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
    protected RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOverride) {
        return RegionDetector.Region.USA;
    }

    @Override
    protected BaseStateHandler createStateHandler(Path file, BaseStateHandler.Type type) {
        return BaseStateHandler.EMPTY_STATE;
    }

    @Override
    protected void newFrame() {
        emuFrame.renderScreenLinear(display.getScreen(), getStats(System.nanoTime()), VideoMode.NTSCJ_H20_V18);
        elapsedNs = (int) (syncCycle(startCycle) - startCycle);
        processSaveState();
        pauseAndWait();
        startCycle = System.nanoTime();
    }

    @Override
    public SystemLoader.SystemType getSystemType() {
        return systemType;
    }
}
