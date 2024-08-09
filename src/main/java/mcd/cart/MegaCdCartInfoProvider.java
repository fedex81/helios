package mcd.cart;

import mcd.cdd.CdModel;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.vdp.util.MemView;
import org.slf4j.Logger;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class MegaCdCartInfoProvider extends MdCartInfoProvider {

    private final static Logger LOG = LogHelper.getLogger(MegaCdCartInfoProvider.class.getSimpleName());

    private final static int headerLen = 0x10;

    private final static int TRACK01_SECURITY_CODE_START = 0x200;
    private final static byte[] SCD_SYS_BYTES = "SEGADISCSYSTEM".getBytes();
    private final static byte ff = (byte) 0xff;
    private final static byte[] CD_SYNC_BYTES = {0x00, ff, ff, ff, ff, ff, ff, ff, ff, ff, ff, 0x00};

    enum SecurityCodeInfo {
        USA(RegionDetector.Region.USA, "d7b7e8e00cee68e15828197ee5090f5a14acd436", 0x584),
        EU(RegionDetector.Region.EUROPE, "4a1946f9aaf7261d9d4ccb0556b7342eaf1cf2f8", 0x56E),
        JP(RegionDetector.Region.JAPAN, "ffe78f1ffdb8b76b358eb8a29b1d5f9dbeabacca", 0x156);
        public final RegionDetector.Region region;
        public final String sha1;
        public final int length;

        SecurityCodeInfo(RegionDetector.Region region, String sha1, int length) {
            this.region = region;
            this.sha1 = sha1;
            this.length = length;
        }
    }


    public SysUtil.RomFileType detectedRomFileType;
    public RegionDetector.Region securityCodeRegion;


    public static MegaCdCartInfoProvider createMcdInstance(IMemoryProvider memoryProvider, SystemProvider.RomContext rom) {
        MegaCdCartInfoProvider m = new MegaCdCartInfoProvider(memoryProvider, rom);
        m.init();
        return m;
    }

    private MegaCdCartInfoProvider(IMemoryProvider memoryProvider, SystemProvider.RomContext rom) {
        super(memoryProvider, rom);
    }

    @Override
    protected void init() {
        super.init();
        CdModel.ExtendedTrackData t1 = romContext.sheet.extTracks.get(0);
        checkTrack01Header(t1);
        securityCodeRegion = verifySecurityCodeRegion(t1);
    }

    private void checkTrack01Header(CdModel.ExtendedTrackData track01) {
        //check that *.iso is really an iso file internally
        SysUtil.RomFileType romFileType = romContext.sheet.romFileType;
        try {
            byte[] header = new byte[headerLen];
            RandomAccessFile raf = track01.file;
            raf.seek(0);
            CdModel.TrackDataType trackDataType = track01.trackDataType;
            raf.read(header, 0, header.length);
            detectedRomFileType = SysUtil.RomFileType.UNKNOWN;
            if (Arrays.equals(SCD_SYS_BYTES, 0, SCD_SYS_BYTES.length, header, 0, SCD_SYS_BYTES.length)) {
                System.out.println("valid Sega CD image");
                detectedRomFileType = SysUtil.RomFileType.ISO;
            } else if (Arrays.equals(CD_SYNC_BYTES, 0, CD_SYNC_BYTES.length, header, 0, CD_SYNC_BYTES.length)) {
                System.out.println("CD-ROM synchro pattern");
                detectedRomFileType = SysUtil.RomFileType.BIN_CUE;
            } else if (trackDataType == CdModel.TrackDataType.AUDIO) {
                System.out.println("CD-AUDIO");
                detectedRomFileType = SysUtil.RomFileType.BIN_CUE;
            }
            assert detectedRomFileType == romFileType : detectedRomFileType + " vs " + romFileType;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error(e.getMessage());
        }
    }

    private static RegionDetector.Region verifySecurityCodeRegion(CdModel.ExtendedTrackData track01) {
        try {
            byte[] secCode = new byte[0x700];
            //sector 2352 starts with 0x10 sync/header bytes, ignore them
            int secCodeStart = track01.trackDataType.size == CdModel.SectorSize.S_2048 ?
                    TRACK01_SECURITY_CODE_START : TRACK01_SECURITY_CODE_START + 0x10;
            RandomAccessFile raf = track01.file;
            raf.seek(secCodeStart);
            raf.read(secCode);
            ByteBuffer bb = ByteBuffer.wrap(secCode);
            for (SecurityCodeInfo sci : SecurityCodeInfo.values()) {
                byte[] b = new byte[sci.length];
                bb.position(0);
                bb.get(b);
                String sha1 = Util.computeSha1Sum(b);
                if (sci.sha1.equalsIgnoreCase(sha1)) {
                    System.out.println(sci.region);
                    return sci.region;
                }
            }
            LOG.error("Unknown security code!");
            StringBuilder sb = new StringBuilder(track01 + "\n");
            MemView.fillFormattedString(sb, secCode, 0, secCode.length);
            System.out.println(sb);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error(e.getMessage());
        }
        return null;
    }
}
