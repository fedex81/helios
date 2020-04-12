package omegadrive.save;

import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.m68k.M68kProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.savestate.GenesisStateHandler;
import omegadrive.savestate.GstStateHandler;
import omegadrive.savestate.GstStateHandlerOld;
import omegadrive.sound.SoundProvider;
import omegadrive.system.Genesis;
import omegadrive.util.FileLoader;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/**
 * SavestateHelper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class SavestateHelper {

    private final static Logger LOG = LogManager.getLogger(Genesis.class.getSimpleName());

    protected IMemoryProvider memory;
    protected BaseVdpProvider vdp;
    protected JoypadProvider joypad;
    protected SoundProvider sound;
    protected InputProvider inputProvider;
    protected GenesisBusProvider bus;
    protected Z80Provider z80;
    protected M68kProvider cpu;

    long cnt = 0;
    private GenesisStateHandler loadStateHandler;
    public GenesisStateHandler saveStateHandler = GenesisStateHandler.createSaveInstance(FileLoader.QUICK_SAVE_FILENAME);

    public void saveLoadStateInternal() {
        saveStateHandler.processState(vdp, z80, bus, sound, cpu, memory);
        if (loadStateHandler == null) {
            saveStateHandler.storeData();
            loadStateHandler = GenesisStateHandler.createLoadInstance(FileLoader.QUICK_SAVE_FILENAME);
        }
        GstStateHandlerOld sh = new GstStateHandlerOld();
        sh.type = BaseStateHandler.Type.SAVE;
        sh.init(FileLoader.QUICK_SAVE_FILENAME + cnt);
        sh.processState(vdp, z80, bus, sound, cpu, memory);

        byte[] newArr = Arrays.copyOfRange(saveStateHandler.getData(), 0, sh.getData().length);
        newArr[2] = 'T';
        boolean res = Arrays.equals(sh.getData(), newArr);
        if (!res) {
            LOG.info(cnt);
            sh.storeData();
            saveStateHandler.storeData();
        }

        ((GstStateHandler) loadStateHandler).setData(saveStateHandler.getData());
        loadStateHandler.processState(vdp, z80, bus, sound, cpu, memory);
        cnt++;
    }
}
