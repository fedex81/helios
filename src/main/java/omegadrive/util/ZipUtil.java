package omegadrive.util;

import com.google.common.io.ByteStreams;
import omegadrive.SystemLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
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

    static final Predicate<ZipEntry> isSupportedBinaryType =
            ze -> Arrays.stream(SystemLoader.binaryTypes).anyMatch(t -> ze.getName().endsWith(t));
    private static Logger LOG = LogManager.getLogger(ZipUtil.class.getSimpleName());

    public static Optional<ZipEntry> getSupportedZipEntryIfAny(Path zipFilePath) {
        ZipFile zipFile = null;
        InputStream is = null;
        Optional<ZipEntry> entry = Optional.empty();
        boolean res = false;
        try {
            zipFile = new ZipFile(zipFilePath.toFile());
            entry = (Optional<ZipEntry>) zipFile.stream().filter(isSupportedBinaryType).findFirst();
        } catch (Exception e) {
            LOG.error("Unable to parse contents {}", zipFilePath.toAbsolutePath().toString(), e);
        } finally {
            closeQuietly(is);
            closeQuietly(zipFile);
        }
        return entry;
    }

    public static int[] loadZipFileContents(Path zipFilePath) {
        ZipFile zipFile = null;
        InputStream is = null;
        Optional<ZipEntry> entry = Optional.empty();
        int[] res = new int[0];
        try {
            zipFile = new ZipFile(zipFilePath.toFile());
            entry = (Optional<ZipEntry>) zipFile.stream().filter(isSupportedBinaryType).findFirst();
            if (entry.isPresent()) {
                is = zipFile.getInputStream(entry.get());
                res = FileLoader.toIntArray(ByteStreams.toByteArray(is));
                LOG.info("Using zipEntry: {}", entry.get().getName());
            } else {
                LOG.error("Unable to find a valid ZipEntry {}", zipFilePath.toAbsolutePath().toString());
            }
        } catch (Exception e) {
            LOG.error("Unable to parse contents {}", zipFilePath.toAbsolutePath().toString(), e);
        } finally {
            closeQuietly(is);
            closeQuietly(zipFile);
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
