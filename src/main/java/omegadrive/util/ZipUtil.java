package omegadrive.util;

import com.google.common.io.ByteStreams;
import omegadrive.system.SysUtil;
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * FileUtil
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class ZipUtil {

    public static final String GZIP_EXT = ".gz";
    public static final String ZIP_EXT = ".zip";
    public static final Predicate<String> isZipFile = n -> n.endsWith(ZIP_EXT);
    public static final Predicate<String> isGZipFile = n -> n.endsWith(GZIP_EXT);
    static final BiPredicate<ZipEntry, String[]> isSupportedBinaryType =
            (ze, ext) -> Arrays.stream(ext).anyMatch(t -> ze.getName().contains(t));
    private final static Logger LOG = LogHelper.getLogger(ZipUtil.class.getSimpleName());

    public static boolean isZipArchiveByteStream(Path path) {
        return isZipFile.test(path.getFileName().toString().toLowerCase());
    }

    public static boolean isGZipByteStream(Path path) {
        return isGZipFile.test(path.getFileName().toString().toLowerCase());
    }

    public static Optional<? extends ZipEntry> getSupportedZipEntryIfAny(Path zipFilePath, String... ext) {
        String[] extArray = ext == null || ext.length == 0 ? SysUtil.binaryTypes : ext;
        Optional<? extends ZipEntry> entry = Optional.empty();
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
            entry = zipFile.stream().filter(e -> isSupportedBinaryType.test(e, extArray)).findFirst();
        } catch (Exception e) {
            LOG.error("Unable to parse contents {}", zipFilePath.toAbsolutePath().toString(), e);
        }
        return entry;
    }

    public static String getGZipFileName(Path path) {
        return path.getFileName().toString().replace(GZIP_EXT, ""); //just a convention
    }

    public static byte[] readZipFileContents(Path path, String... ext) {
        Optional<? extends ZipEntry> entry = getSupportedZipEntryIfAny(path, ext);
        return entry.map(e -> readZipFileImpl(path, e)).orElse(new byte[0]);
    }

    private static byte[] readZipFileImpl(Path path, ZipEntry entry) {
        byte[] res = new byte[0];
        try (ZipFile zipFile = new ZipFile(path.toFile());
             InputStream is = zipFile.getInputStream(entry)) {
            res = ByteStreams.toByteArray(is);
            LOG.info("Using zipEntry: {}", entry.getName());
        } catch (Exception e) {
            LOG.error("Unable to parse contents {}", path.toAbsolutePath().toString(), e);
        }
        return res;
    }

    public static byte[] readGZipFileContents(Path path) {
        byte[] res = new byte[0];
        try (InputStream fis = Files.newInputStream(path);
             InputStream zis = new GZIPInputStream(fis)) {
            res = ByteStreams.toByteArray(zis);
        } catch (Exception e) {
            LOG.error("Unable to parse contents {}", path.toAbsolutePath().toString(), e);
        }
        return res;
    }

    public static void closeQuietly(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (IOException e) {
            //do nothing
        }
    }
}
