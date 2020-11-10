package omegadrive.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * FileUtil
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class FileUtil {

    private static final Logger LOG = LogManager.getLogger(FileUtil.class.getSimpleName());

    public static Path compressAndSaveToZipFile(Path srcFilePath) {
        String fileName = srcFilePath.getFileName().toString();
        Path folder = srcFilePath.getParent();
        String zipFileName = fileName + ".zip";
        File zipFile = Paths.get(folder.toAbsolutePath().toString(), zipFileName).toFile();
        ZipOutputStream zos = null;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(zipFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            byte[] data = Files.readAllBytes(srcFilePath);
            zos = new ZipOutputStream(bos);
            zos.putNextEntry(new ZipEntry(fileName));
            zos.write(data);
            zos.closeEntry();
        } catch (Exception e) {
            LOG.error("Unable to save source file: {}", fileName, e);
        } finally {
            closeQuietly(zos);
            closeQuietly(fos);
        }
        return zipFile.toPath();
    }

    public static Path compressAndSaveToZipFile(String fileName, Path folder, Image img, String imgType) {

        String zipFileName = fileName + ".zip";
        File zipFile = Paths.get(folder.toAbsolutePath().toString(), zipFileName).toFile();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = null;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(zipFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            zos = new ZipOutputStream(bos);
            RenderedImage ri = (RenderedImage) img;
            ImageIO.write(ri, imgType, baos);
            zos.putNextEntry(new ZipEntry(fileName));
            zos.write(baos.toByteArray());
            zos.closeEntry();
        } catch (Exception e) {
            LOG.error("Unable to save source file: {}", fileName, e);
        } finally {
            closeQuietly(zos);
            closeQuietly(fos);
        }
        return zipFile.toPath();
    }

    public static Path compressAndSaveToZipFile(Path file, Image img, String imgType) {
        return compressAndSaveToZipFile(file.getFileName().toString(), file.getParent(), img, imgType);
    }

    public static Image decompressAndLoadFromZipFile(Path zipFilePath, String imgFileName, String imgType) {
        ZipFile zipFile = null;
        InputStream is = null;
        Image img = null;
        try {
            zipFile = new ZipFile(zipFilePath.toFile());
            is = zipFile.getInputStream(zipFile.getEntry(imgFileName));
            img = ImageIO.read(is);
        } catch (Exception e) {
            LOG.error("Unable to load {} from file {}", imgFileName, zipFilePath.toAbsolutePath().toString(), e);
        } finally {
            closeQuietly(is);
            closeQuietly(zipFile);
        }
        return img;
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
