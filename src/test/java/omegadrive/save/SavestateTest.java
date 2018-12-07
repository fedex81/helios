package omegadrive.save;

import com.google.common.collect.ImmutableMap;
import omegadrive.Genesis;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.savestate.GenesisStateHandler;
import omegadrive.savestate.GstStateHandler;
import omegadrive.vdp.GenesisVdpNew;
import org.junit.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class SavestateTest {

    public static Map<String, String> saveStates = ImmutableMap.of(
            "test01.gs0", "Sonic The Hedgehog (W) (REV00) [!].bin",
            "test02.gs0", "Sonic The Hedgehog 2 (W) (REV01) [!].bin"
    );

    static int saveStateTestNumber = 1;

    public static void main(String[] args) throws Exception {
        load(saveStates.keySet().toArray()[saveStateTestNumber].toString(),
                saveStates.values().toArray()[saveStateTestNumber].toString());
    }

    private static int[] getData(String key) throws Exception {
        Path p = Paths.get(".", key);
        System.out.println(p.toAbsolutePath().toString());
        byte[] data = Files.readAllBytes(p);
        int[] dataByte = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            dataByte[i] = data[i] & 0xFF;
        }
        return dataByte;
    }

    private static void load(String saveFile, String romFile) throws Exception {
        GenesisStateHandler stateHandler = new GstStateHandler();
        int[] data = getData(saveFile);
        String rom = "/home/fede/roms/" + romFile;
        String fileType = toStringValue(Arrays.copyOfRange(data, 0, 3));
        Assert.assertEquals("GST", fileType);
        System.out.println("File type: " + fileType);
        System.out.println("Version: " + data[0x50]);
        System.out.println("SwId: " + data[0x51]);

        Genesis genesis = new Genesis(false) {

            @Override
            protected void resetAfterGameLoad() {
                super.resetAfterGameLoad();
                stateHandler.loadFmState(sound.getFm(), data);
                stateHandler.loadVdpState(vdp, data);
                stateHandler.loadZ80(z80, data);
                stateHandler.load68k((MC68000Wrapper) cpu, bus.getMemory(), data);
                ((GenesisVdpNew) vdp).initMode();
            }
        };
        genesis.handleNewGame(Paths.get(rom));
    }

    private static String toStringValue(int[] data) {
        String value = "";
        for (int i = 0; i < data.length; i++) {
            value += (char) (data[i] & 0xFF);
        }
        return value;
    }
}
