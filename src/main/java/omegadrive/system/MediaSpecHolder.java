package omegadrive.system;

import mcd.cart.MegaCdCartInfoProvider;
import mcd.cdd.ExtendedCueSheet;
import omegadrive.SystemLoader.SystemType;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cart.MediaInfoProvider;
import omegadrive.system.SysUtil.RomFileType;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.ZipUtil;
import org.slf4j.Logger;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class MediaSpecHolder {

    private static final Logger LOG = LogHelper.getLogger(MediaSpecHolder.class.getSimpleName());
    public static final Path NO_PATH = Path.of("NO_PATH");
    public static final MediaSpecHolder NO_ROM = MediaSpecHolder.of(NO_PATH);

    public static class MediaSpec {
        public Path romFile;
        public RomFileType type;
        public MediaInfoProvider mediaInfoProvider;
        public SystemType systemType;
        public Optional<ExtendedCueSheet> sheetOpt = Optional.empty();
        public int mediaSizeBytes;
        public RegionDetector.Region region;
        public boolean bootable;
        public boolean compressed;

        public static MediaSpec of(Path p, RomFileType t, SystemType st) {
            MediaSpec m = new MediaSpec();
            m.romFile = p;
            m.type = t;
            m.systemType = st;
            m.compressed = ZipUtil.isCompressedByteStream(p);
            m.init();
            return m;
        }

        protected void init() {
            boolean isCartBased = !type.isDiscImage();
            boolean isCartMdBased = isCartBased && systemType == SystemType.MD || systemType == SystemType.S32X;
            boolean validPath = !romFile.toAbsolutePath().equals(NO_PATH.toAbsolutePath());
            if (isCartMdBased && validPath) {
                mediaInfoProvider = getMdInfoProvider(romFile, compressed);
                mediaSizeBytes = mediaInfoProvider.getRomSize();
            } else if (isCartBased && validPath) {
                mediaInfoProvider = getMediaInfoProvider(romFile);
                mediaSizeBytes = mediaInfoProvider.getRomSize();
            } else if (type.isDiscImage()) {
                assert !ZipUtil.isCompressedByteStream(romFile);
                assert !compressed;
                sheetOpt = Optional.of(new ExtendedCueSheet(romFile, type));
                mediaInfoProvider = getMcdInfoProvider(romFile, this);
//                mediaSizeBytes = sheetOpt.get().extTracks.get(0).lenBytes; //TODO ??
            }
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", MediaSpec.class.getSimpleName() + "[", "]")
                    .add("romFile=" + romFile)
                    .add("type=" + type)
                    .add("mediaInfoProvider=" + mediaInfoProvider)
                    .add("systemType=" + systemType)
                    .add("mediaSizeBytes=" + mediaSizeBytes)
                    .add("region=" + region)
                    .add("bootable=" + bootable)
                    .add("compressed=" + compressed)
                    .toString();
        }
    }

    public static MediaInfoProvider getMediaInfoProvider(Path p) {
        MediaInfoProvider mip = new MediaInfoProvider();
        byte[] b = FileUtil.readBinaryFile(p);
        mip.romSize = b.length;
        mip.romName = p.getFileName().toString();
        return mip;
    }

    public static MdCartInfoProvider getMcdInfoProvider(Path p, MediaSpec mediaSpec) {
        MdCartInfoProvider mdi = MegaCdCartInfoProvider.createMcdInstance(mediaSpec);
        mdi.romName = p.getFileName().toString();
        return mdi;
    }

    public static MdCartInfoProvider getMdInfoProvider(Path p, boolean compressed) {
        MdCartInfoProvider mdi = null;
        if (compressed) {
            byte[] b = FileUtil.readBinaryFile(p);
            assert b.length > 0;
            mdi = MdCartInfoProvider.createMdInstance(b);
        } else {
            try (RandomAccessFile raf = new RandomAccessFile(p.toFile(), "r")) {
                mdi = MdCartInfoProvider.createMdInstance(raf);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        mdi.romName = p.getFileName().toString();
        return mdi;
    }

    private MediaSpec cartFile;
    private MediaSpec cdFile;
    public SystemType systemType;
    public RegionDetector.Region region;

    public static MediaSpecHolder of(Path path) {
        return of(path, SystemType.NONE);
    }

    public static MediaSpecHolder of(String filePath) {
        return of(Path.of(filePath), SystemType.NONE);
    }

    public static MediaSpecHolder of(File file) {
        return of(Path.of(file.getAbsolutePath()), SystemType.NONE);
    }

    public static MediaSpecHolder of(File file, SystemType systemType) {
        return of(Path.of(file.getAbsolutePath()), systemType);
    }

    public static MediaSpecHolder of(Path file, SystemType systemType) {
        MediaSpecHolder r = new MediaSpecHolder();
        r.systemType = systemType;
        RomFileType rft = RomFileType.CART_ROM;
        rft = file.toString().endsWith(".cue") ? RomFileType.BIN_CUE : rft;
        rft = file.toString().endsWith(".iso") ? RomFileType.ISO : rft;
        MediaSpec ms = MediaSpec.of(file, rft, systemType);
        if (rft.isDiscImage()) {
            r.cdFile = ms;
        } else {
            r.cartFile = ms;
        }
        return r;
    }

    public void reload() {
        assert hasRomCart() ? cartFile.systemType == SystemType.NONE || cartFile.systemType == systemType : true;
        assert hasDiscImage() ? cdFile.systemType == SystemType.NONE || cdFile.systemType == systemType : true;
        if (hasRomCart()) {
            cartFile.systemType = systemType;
            cartFile.init();
        }
        if (hasDiscImage()) {
            cdFile.systemType = systemType;
            cdFile.init();
        }
    }

    public boolean hasRomCart() {
        return cartFile != null && cartFile.type == RomFileType.CART_ROM;
    }

    public boolean hasDiscImage() {
        return cdFile != null && cdFile.type.isDiscImage();
    }

    public MediaSpec getBootableMedia() {
        if (hasDiscImage() && hasRomCart()) {
            throw new RuntimeException("TODO");
        }
        if (hasRomCart()) return cartFile;
        if (hasDiscImage()) return cdFile;
        return null;
    }

    public RegionDetector.Region getRegion() {
        return region;
    }

    @Override
    public String toString() {
        return systemType + "," + getBootableMedia().romFile.toAbsolutePath();
    }
}
