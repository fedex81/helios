package omegadrive.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

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

    public static String loadFileContentAsString(String fileName) {
        return loadFileContent(fileName).stream().collect(Collectors.joining("\n"));
    }

    public static List<String> loadFileContent(String fileName) {
        Class clazz = FileLoader.class;
        String className = clazz.getSimpleName() + ".class";
        String classPath = clazz.getResource(className).toString();
        String filePath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                fileName;
        List<String> lines = Collections.emptyList();
        try {
            Path pathObj = Paths.get(filePath);
            lines = Files.readAllLines(pathObj);
        } catch (IOException e) {
            LOG.error("Unable to load " + fileName + ", from path: " + filePath);
        }
        return lines;
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
