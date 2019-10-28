/*
 * FileLoader
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 01/07/19 16:01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.util;

import omegadrive.SystemLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.filechooser.FileFilter;
import java.io.*;
import java.net.URL;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class FileLoader {

    private static Logger LOG = LogManager.getLogger(FileLoader.class.getSimpleName());

    private static int[] EMPTY = new int[0];

    public static String basePath = System.getProperty("user.home") + File.separatorChar + "roms";

    private static String SNAPSHOT_VERSION = "SNAPSHOT";
    private static String MANIFEST_RELATIVE_PATH = "/META-INF/MANIFEST.MF";
    private static String BIOS_JAR_PATH = ".";
    public static String QUICK_SAVE_FILENAME = "quick_save";

    public static FileFilter ROM_FILTER = new FileFilter() {
        @Override
        public String getDescription() {
            return Arrays.toString(SystemLoader.binaryTypes) + " files";
        }

        @Override
        public boolean accept(File f) {
            String name = f.getName().toLowerCase();
            return f.isDirectory() || Arrays.stream(SystemLoader.binaryTypes).anyMatch(name::endsWith);
        }
    };

    public static FileFilter SAVE_STATE_FILTER = new FileFilter() {
        @Override
        public String getDescription() {
            return "state files";
        }

        @Override
        public boolean accept(File f) {
            String name = f.getName().toLowerCase();
            return f.isDirectory() || name.contains(".gs") || name.contains(".s0");
        }
    };

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
        Path pathObj = Paths.get(".", fileName);
        List<String> lines = Collections.emptyList();
        if(pathObj.toFile().exists()) {
            try {
                lines = Files.readAllLines(pathObj);
            } catch (IOException e) {
                LOG.error("Unable to load " + fileName + ", from path: " + pathObj);
            }
            return lines;
        }
        String classPath = getCurrentClasspath();
        if (isRunningInJar(classPath)) {
            return loadFileContentFromJar(fileName);
        }
        LOG.warn("Unable to load: " + fileName);
        return lines;
    }

    public static int[] loadBiosFile(Path file) {
        if(file.toFile().exists()){
            return readFileSafe(file);
        }
        String classPath = getCurrentClasspath();
        if (isRunningInJar(classPath)) {
            String fileName = file.getFileName().toString();
            return readFileFromJar(fileName);
        }
        LOG.error("Unable to load: " + file.toAbsolutePath().toString());
        return new int[0];
    }

    private static int[] readFileFromJar(String fileName){
        IntBuffer buffer = IntBuffer.allocate(0);
        try (
                InputStream inputStream = FileLoader.class.getResourceAsStream("/" + fileName)
        ) {
            buffer = IntBuffer.allocate(inputStream.available());
            while(inputStream.available() > 0){
                buffer.put(inputStream.read());
            }
        } catch (Exception e) {
            LOG.error("Unable to load " + fileName + ", from path: " + fileName, e);
        }
        return buffer.array();
    }

    private static List<String> loadFileContentFromJar(String fileName) {
        List<String> lines = Collections.emptyList();
        try (
                InputStream inputStream = FileLoader.class.getResourceAsStream("/" + fileName);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))
        ) {
            lines = reader.lines().collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error("Unable to load " + fileName + ", from path: " + fileName, e);
        }
        return lines;
    }

    public static int[] loadBinaryFile(Path file, SystemLoader.SystemType systemType) {
        return loadBinaryFile(file, ROM_FILTER);
    }

    private static int[] loadBinaryFile(Path file, FileFilter fileFilter) {
        int[] data = new int[0];
        try {
            String fileName = file.toAbsolutePath().toString();
            if (fileFilter.accept(file.toFile())) {
                data = FileLoader.readFile(file);
                if (data == null || data.length == 0) {
                    throw new RuntimeException("Empty file!");
                }
            } else {
                throw new RuntimeException("Unexpected file: " + fileName);
            }
        } catch (Exception e) {
            LOG.error("Unable to load: " + file.toAbsolutePath().toString(), e);
        }
        return data;
    }

    public static String loadVersionFromManifest() {
        String version = SNAPSHOT_VERSION;
        String classPath = getCurrentClasspath();
        if (!isRunningInJar(classPath)) {
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

    private static String getCurrentClasspath(){
        Class clazz = FileLoader.class;
        String className = clazz.getSimpleName() + ".class";
        return clazz.getResource(className).toString();
    }

    private static boolean isRunningInJar(String classPath){
        return classPath.startsWith("jar");
    }
}
