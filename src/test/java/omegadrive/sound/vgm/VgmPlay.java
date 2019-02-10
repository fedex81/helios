package omegadrive.sound.vgm;

import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.YM2612;
import uk.co.omgdrv.simplevgm.Runner;
import uk.co.omgdrv.simplevgm.VGMPlayer;
import uk.co.omgdrv.simplevgm.model.VgmFmProvider;
import uk.co.omgdrv.simplevgm.model.VgmPsgProvider;
import uk.co.omgdrv.simplevgm.psg.SmsApu;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class VgmPlay {

    public static void main(String[] args) throws Exception {
        Path folder = Paths.get("ab.vgz");
        System.out.println(folder.toAbsolutePath());
        VgmPsgProvider vgmPsgProvider = new SmsApu();
        VgmFmProvider vgmFmProvider = createVgmFmProvider(new YM2612());
//        VgmFmProvider vgmFmProvider = createVgmFmProvider(new Ym2612Nuke());
        VGMPlayer vgmPlayer = VGMPlayer.createInstance(vgmPsgProvider, vgmFmProvider, 44100);
        Runner.playRecursive(vgmPlayer, folder);

//        Runner.main(new String[]{"mss.vgm"});
    }

    private static VgmFmProvider createVgmFmProvider(FmProvider ym2612) {
        return new VgmFmProvider() {
            @Override
            public int reset() {
                return ym2612.reset();
            }

            @Override
            public int init(int clock, int rate) {
                return ym2612.init(clock, rate);
            }

            @Override
            public void update(int[] ints, int offset, int end) {
                ym2612.update(ints, offset, end);
            }

            @Override
            public void write0(int address, int data) {
                ym2612.write(FmProvider.FM_ADDRESS_PORT0, address);
                ym2612.write(FmProvider.FM_DATA_PORT0, data);
            }

            @Override
            public void write1(int address, int data) {
                ym2612.write(FmProvider.FM_ADDRESS_PORT1, address);
                ym2612.write(FmProvider.FM_DATA_PORT1, data);
            }

            private void logWrite(int address, int data) {
                String str = String.format("write addr: %s, data: %s",
                        Integer.toHexString(address), Integer.toHexString(data));
                System.out.println(str);
            }
        };
    }
}