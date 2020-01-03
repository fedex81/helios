package omegadrive.util;

import com.google.common.io.ByteStreams;
import omegadrive.SystemLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            (ze, ext) -> Arrays.stream(ext).anyMatch(t -> ze.getName().endsWith(t));
    private final static Logger LOG = LogManager.getLogger(ZipUtil.class.getSimpleName());

    public static boolean isZipArchiveByteStream(Path path) {
        return isZipFile.test(path.getFileName().toString().toLowerCase());
    }

    public static boolean isGZipByteStream(Path path) {
        return isGZipFile.test(path.getFileName().toString().toLowerCase());
    }

    public static Optional<? extends ZipEntry> getSupportedZipEntryIfAny(Path zipFilePath, String... ext) {
        String[] extArray = ext == null || ext.length == 0 ? SystemLoader.binaryTypes : ext;
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

    public static int[] readZipFileContents(Path path, String... ext) {
        Optional<? extends ZipEntry> entry = getSupportedZipEntryIfAny(path, ext);
        return entry.map(e -> readZipFileImpl(path, e)).orElse(new int[0]);
    }

    private static int[] readZipFileImpl(Path path, ZipEntry entry) {
        int[] res = new int[0];
        try (ZipFile zipFile = new ZipFile(path.toFile());
             InputStream is = zipFile.getInputStream(entry)) {
            res = Util.toIntArray(ByteStreams.toByteArray(is));
            LOG.info("Using zipEntry: {}", entry.getName());
        } catch (Exception e) {
            LOG.error("Unable to parse contents {}", path.toAbsolutePath().toString(), e);
        }
        return res;
    }

    public static int[] readGZipFileContents(Path path) {
        int[] res = new int[0];
        try (InputStream fis = Files.newInputStream(path);
             InputStream zis = new GZIPInputStream(fis)) {
            res = Util.toIntArray(ByteStreams.toByteArray(zis));
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