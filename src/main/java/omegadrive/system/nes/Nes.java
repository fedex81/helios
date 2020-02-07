/*
 * Sms
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 27/10/19 13:09
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.system.nes;

import com.grapeshot.halfnes.audio.AudioOutInterface;
import com.grapeshot.halfnes.ui.ControllerImpl;
import omegadrive.SystemLoader;
import omegadrive.bus.BaseBusProvider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.NesPad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.savestate.NesStateHandler;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.system.BaseSystem;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Nes extends BaseSystem<BaseBusProvider, NesStateHandler> {

    private static Logger LOG = LogManager.getLogger(Nes.class.getSimpleName());

    static {
        ControllerImpl.JINPUT_ENABLE = false;
        LOG.info("Disabling halfNes jinput");
    }

    public static boolean verbose = false;

    protected long startCycle = System.nanoTime();
    protected int elapsedNs;
    private SystemLoader.SystemType systemType;
    private NesHelper.NesGUIInterface gui;

    protected Nes(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        super(emuFrame);
        this.systemType = systemType;
    }

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, DisplayWindow emuFrame, boolean debugPerf) {
        return new Nes(systemType, emuFrame);
    }

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        return createNewInstance(systemType, emuFrame, false);
    }

    private void initCommon() {
        inputProvider = InputProvider.createInstance(joypad);
        reloadWindowState();
    }

    @Override
    protected RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOvr) {
        RegionDetector.Region romRegion = RegionDetector.Region.JAPAN;
        RegionDetector.Region ovrRegion = RegionDetector.getRegion(regionOvr);
        if (ovrRegion != null && ovrRegion != romRegion) {
            LOG.info("Setting region override from: " + romRegion + " to " + ovrRegion);
            romRegion = ovrRegion;
        }
        return romRegion;
    }

    @Override
    protected NesStateHandler createStateHandler(Path file, BaseStateHandler.Type type) {
        String fileName = file.toAbsolutePath().toString();
        return type == BaseStateHandler.Type.LOAD ?
                NesStateHandler.createLoadInstance(fileName) :
                NesStateHandler.createSaveInstance(fileName);
    }

    @Override
    public void init() {
        stateHandler = NesStateHandler.EMPTY_STATE;
        bus = NesHelper.NO_OP_BUS;
        vdp = NesHelper.VDP_PROVIDER;
        memory = MemoryProvider.NO_MEMORY;
        joypad = new NesPad(NesHelper.cnt1, NesHelper.cnt2);
        initCommon();
    }

    @Override
    protected void loop() {
        LOG.info("Starting game loop");
        targetNs = (long) (region.getFrameIntervalMs() * Util.MILLI_IN_NS);
        videoMode = vdp.getVideoMode();
        gui = NesHelper.createNes(romFile, this, (AudioOutInterface) sound.getFm());
        gui.run(); //blocking
        LOG.info("Exiting rom thread loop");
    }

    @Override
    protected void handleCloseRom() {
        gui.close();
        super.handleCloseRom();
    }

    @Override
    protected void newFrame() {
        videoMode = vdp.getVideoMode();
        renderScreenLinearInternal(gui.getScreen(), getStats(System.nanoTime()));
        handleVdpDumpScreenData();
        sound.output(0);
        elapsedNs = (int) (syncCycle(startCycle) - startCycle);
        processSaveState();
        pauseAndWait();
        startCycle = System.nanoTime();
    }

    @Override
    protected void initAfterRomLoad() {
        sound = AbstractSoundManager.createSoundProvider(systemType, region);
        resetAfterRomLoad();
    }

    @Override
    protected void processSaveState() {
        if (saveStateFlag) {
            stateHandler.processState(gui.getNes());
            if (stateHandler.getType() == BaseStateHandler.Type.SAVE) {
                stateHandler.storeData();
            } else {
                sound.getPsg().reset();
            }
            stateHandler = NesStateHandler.EMPTY_STATE;
            saveStateFlag = false;
        }
    }

    @Override
    public SystemLoader.SystemType getSystemType() {
        return systemType;
    }
}