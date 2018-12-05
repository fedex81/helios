package omegadrive.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class SavestateTest {

    public static void main(String[] args) throws IOException {
        Path p = Paths.get(".", "test01.gs0");
        System.out.println(p.toAbsolutePath().toString());
        byte[] data = Files.readAllBytes(p);
    }

    private static void load(byte[] data) {

    }
}
