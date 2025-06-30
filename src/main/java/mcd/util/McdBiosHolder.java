package mcd.util;

import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import s32x.util.BiosHolder;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

import static mcd.dict.MegaCdDict.MCD_BOOT_ROM_WINDOW_SIZE;
import static omegadrive.SystemLoader.biosFolder;
import static omegadrive.util.RegionDetector.Region;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdBiosHolder {

    private static final Logger LOG = LogHelper.getLogger(McdBiosHolder.class.getSimpleName());
    public static String biosBasePath = System.getProperty("bios.folder", biosFolder) + "/mcd";

    /**
     * MD5: 854b9150240a198070150e4566ae1290
     * SHA1: 5adb6c3af218c60868e6b723ec47e36bbdf5e6f0
     */
    public static String masterBiosNameUs = "bios_CD_U.bin";
    /**
     * MD5: e66fa1dc5820d254611fdcdba0662372
     * SHA1: f891e0ea651e2232af0c5c4cb46a0cae2ee8f356
     */
    public static String masterBiosNameEu = "bios_CD_E.bin";
    /**
     * MD5: 278a9397d192149e84e820ac621a8edd
     * SHA1: 4846f448160059a7da0215a5df12ca160f26dd69
     */
    public static String masterBiosNameJp = "bios_CD_J.bin";

    private Map<Region, BiosHolder.BiosData> biosData = new EnumMap<>(Region.class);

    private static McdBiosHolder INSTANCE;

    public static McdBiosHolder getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new McdBiosHolder();
        }
        return INSTANCE;
    }

    private McdBiosHolder() {
        init();
    }

    private void init() {
        LOG.info("MCD bios base path: {}", biosBasePath);
        Path u = Paths.get(biosBasePath, masterBiosNameUs);
        Path e = Paths.get(biosBasePath, masterBiosNameEu);
        Path j = Paths.get(biosBasePath, masterBiosNameJp);
        boolean pathOk = Files.exists(u) && Files.exists(e) && Files.exists(j);
        if (!pathOk) {
            LOG.error("One or more bios not found: \n{}\n{}\n{}", u, e, j);
            return;
        }
        Map<Region, Path> m = Map.of(Region.USA, u, Region.JAPAN, j, Region.EUROPE, e);
        LOG.info("MCD bios files: {}", m);

        biosData.put(Region.USA, new BiosHolder.BiosData(FileUtil.readFileSafe(u)));
        biosData.put(Region.EUROPE, new BiosHolder.BiosData(FileUtil.readFileSafe(e)));
        biosData.put(Region.JAPAN, new BiosHolder.BiosData(FileUtil.readFileSafe(j)));

        boolean dataOk = biosData.entrySet().stream().allMatch(entry -> entry.getValue().buffer.capacity() > 0);
        if (!dataOk) {
            LOG.error("Unable to load one or more bios: {}", biosData);
            return;
        }
        LOG.info("MCD bios set loaded: {}", biosData);
    }

    public ByteBuffer getBiosBuffer(Region region) {
        BiosHolder.BiosData bd = biosData.get(region);
        LOG.info("{} using bios: {}", region, bd);
        return bd.buffer;
    }

    public byte[] getBios(Region region) {
        return biosData.get(region).buffer.array();
    }


    public static ByteBuffer loadBios(Region region, Path p) {
        ByteBuffer bios;
        try {
            assert Files.exists(p);
            byte[] b = FileUtil.readBinaryFile(p, ".bin", ".md");
            assert b.length > 0;
            bios = ByteBuffer.wrap(b);
            LOG.info("Loading bios at {}, region: {}, size: {}", p.toAbsolutePath(), region, b.length);
        } catch (Error | Exception e) {
            LOG.error("Unable to load bios at {}", p.toAbsolutePath());
            bios = ByteBuffer.allocate(MCD_BOOT_ROM_WINDOW_SIZE);
        }
        return bios;
    }
}
