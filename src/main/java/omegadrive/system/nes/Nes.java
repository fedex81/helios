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
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.ExternalPad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.savestate.NesStateHandler;
import omegadrive.system.BaseSystem;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.nio.file.Path;

public class Nes extends BaseSystem<BaseBusProvider> {

    private static final Logger LOG = LogHelper.getLogger(Nes.class.getSimpleName());

    static {
        ControllerImpl.JINPUT_ENABLE = false;
        LOG.info("Disabling halfNes jinput");
    }
    private NesHelper.NesGUIInterface gui;

    protected Nes(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        super(emuFrame);
        this.systemType = systemType;
    }

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        return new Nes(systemType, emuFrame);
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
            LOG.info("Setting region override from: {} to {}", romRegion, ovrRegion);
            romRegion = ovrRegion;
        }
        return romRegion;
    }

    @Override
    protected BaseStateHandler createStateHandler(Path file, BaseStateHandler.Type type) {
        NesStateHandler n = (NesStateHandler) super.createStateHandler(file, type);
        n.setNes(gui.getNes());
        return n;
    }

    @Override
    public void init() {
        stateHandler = BaseStateHandler.EMPTY_STATE;
        bus = SysUtil.NO_OP_BUS;
        vdp = SysUtil.NO_OP_VDP_PROVIDER;
        memory = MemoryProvider.NO_MEMORY;
        joypad = ExternalPad.createTwoButtonsPad(NesHelper.cnt1, NesHelper.cnt2);
        initCommon();
    }

    @Override
    protected void loop() {
        targetNs = (long) (getRegion().getFrameIntervalMs() * Util.MILLI_IN_NS);
        videoMode = vdp.getVideoMode();
        gui = NesHelper.createNes(getRomPath(), this, (AudioOutInterface) sound.getFm());
        vdp = gui.getVdpProvider();
        gui.run(); //blocking
    }

    @Override
    protected void handleCloseRom() {
        gui.close();
        super.handleCloseRom();
    }

    @Override
    protected void initAfterRomLoad() {
        super.initAfterRomLoad();
        resetAfterRomLoad();
    }

    @Override
    protected void resetCycleCounters(int counter) {
        //DO NOTHING
    }

    @Override
    protected void updateVideoMode(boolean force) {
        videoMode = vdp.getVideoMode();
    }
}