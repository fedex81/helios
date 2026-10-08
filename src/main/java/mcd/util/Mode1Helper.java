package mcd.util;

import mcd.McdDeviceHelper.McdLaunchContext;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.system.MediaSpecHolder;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

import static omegadrive.system.SysUtil.*;
import static omegadrive.system.SysUtil.RomFileType.ISO;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class Mode1Helper {

    private static final Logger LOG = LogHelper.getLogger(Mode1Helper.class.getSimpleName());

    public static void checkMode1AndInsertDisc(McdLaunchContext mcdLaunchContext, MediaSpecHolder mediaSpec) {
        mcdLaunchContext.mainBus.setEnableMode1(true);
        MdCartInfoProvider cartridgeInfoProvider = mcdLaunchContext.mainBus.getCartridgeInfoProvider();
        //TODO hack McdVer mode1
        boolean mcdVer = cartridgeInfoProvider.toString().contains("krikzz");
        boolean isBios = cartridgeInfoProvider.getSerial().startsWith("BR ") && !mcdVer;
        //bios aka bootRom
        if (isBios) {
            mcdLaunchContext.mainBus.setEnableMode1(false);
            LOG.info("Bios detected with serial: {}, disabling mode1 mapper", cartridgeInfoProvider.getSerial());
            //NOTE: load bios as a file, use it as the current bios, disregarding the default bios
            mcdLaunchContext.mainBus.setBios(
                    McdBiosHolder.loadBios(mediaSpec.getRegion(), mediaSpec.cartFile.romFile));
        } else if (mediaSpec.hasDiscImage() && mediaSpec.cdFile.bootable) {
            mcdLaunchContext.mainBus.setEnableMode1(false);
            LOG.info("CUE/ISO file detected, disabling mode1 mapper");
            assert mediaSpec.cdFile.sheetOpt.isPresent();
            mcdLaunchContext.cdd.tryInsert(mediaSpec.cdFile.sheetOpt.get());
        } else if (!isBios && mediaSpec.hasRomCart()) {
            checkMode1CartAndDisc(mcdLaunchContext, mediaSpec);
        } else {
            LOG.error("wot");
        }
    }

    /**
     * Pier Solar MD cart + MCD compatible disc
     * - CD has no bootable code, just 1 DATA TRACK (PCM DATA) and 3 CDDA tracks
     * - it needs the cart to be inserted
     * Doom 32x Fusion
     * - (CD32x official releases boot from CD with no cart inserted)
     * - DoomFusion boots from a 32x cart with CD inserted
     * Flux
     * - boots from cart and plays a CD Audio
     **/
    private static void checkMode1CartAndDisc(McdLaunchContext mcdLaunchContext, MediaSpecHolder msh) {
        MdCartInfoProvider cartInfo = mcdLaunchContext.mainBus.getCartridgeInfoProvider();
        String serial = cartInfo.getSerial();
        String name = cartInfo.getRomName();
        boolean isPierSolar = name.startsWith("Pier Solar") || serial.contains("GM T-574013");
        boolean isDoom32xFusion = name.startsWith("DCD32X") || serial.contains("DMF32XCD");
        boolean isFlux = name.startsWith("Flux ") || serial.contains("GM T-70416");
        if (isPierSolar || isDoom32xFusion || isFlux) {
            mcdLaunchContext.mainBus.setEnableMode1(true);
            final String n = Util.getNameWithoutExtension(name);
            String[] exts = {CUE_EXT, ISO_EXT, ISO_GZ_EXT};
            for (String ext : exts) {
                String discName = n + ext;
                Path discPath = msh.cartFile.romFile.resolveSibling(discName);
                if (Files.exists(discPath) && Files.isReadable(discPath)) {
                    RomFileType type = CUE_EXT.equals(ext) ? RomFileType.BIN_CUE : ISO;
                    LOG.warn("Disc detected attempting to load {} file: {}", type, discPath);
                    msh.cdFile = MediaSpecHolder.MediaSpec.of(discPath, type, msh.systemType);
                    msh.cdFile.sheetOpt.ifPresent(s -> mcdLaunchContext.cdd.tryInsert(s));
                    break;
                }
            }
        }
    }
}
