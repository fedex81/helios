package omegadrive.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * FileLoader
 *
 * @{author Federico Berti
 */
public class FileLoader {

    private static Logger LOG = LogManager.getLogger(FileLoader.class.getSimpleName());

    private static int[] EMPTY = new int[0];

    public static String basePath = System.getProperty("user.home") + File.separatorChar + "roms";

    private static String SNAPSHOT_VERSION = "SNAPSHOT";
    private static String MANIFEST_RELATIVE_PATH = "/META-INF/MANIFEST.MF";

    public static Optional<File> openRomDialog() {
        Optional<File> res = Optional.empty();
        JFileChooser fileChooser = new JFileChooser(basePath);
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public String getDescription() {
                return "md and bin files";
            }

            @Override
            public boolean accept(File f) {
                String name = f.getName().toLowerCase();
                return f.isDirectory() || name.endsWith(".md") || name.endsWith(".bin");
            }
        });
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            res = Optional.ofNullable(fileChooser.getSelectedFile());
        }
        return res;
    }

    public static int[] readFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        int[] rom = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            rom[i] = bytes[i] & 0xFF;
        }
        return rom;
    }

    public static void writeFile(Path file, int[] data) throws IOException {
        byte[] bytes = new byte[data.length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (data[i] & 0xFF);
        }
        Files.write(file, bytes);
    }

    public static int[] readFileSafe(Path file) {
        int[] rom = EMPTY;
        try {
            rom = readFile(file);
        } catch (IOException e) {
            LOG.error("Unable to load file: " + file.getFileName());
        }
        return rom;
    }

    public static String loadVersionFromManifest() {
        String version = SNAPSHOT_VERSION;
        Class clazz = FileLoader.class;
        String className = clazz.getSimpleName() + ".class";
        String classPath = clazz.getResource(className).toString();
        if (!classPath.startsWith("jar")) {
            // Class not from JAR
            LOG.info("Not running from a JAR, using version: " + version);
            return version;
        }
        String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                MANIFEST_RELATIVE_PATH;
        try (InputStream is = new URL(manifestPath).openStream()) {
            Manifest manifest = new Manifest(is);
            Attributes attr = manifest.getMainAttributes();
            version = attr.getValue("Implementation-Version");
            LOG.info("Using version from Manifest: " + version);
        } catch (Exception e) {
            LOG.error("Unable to load manifest file: " + manifestPath);
        }
        return version;
    }
}
