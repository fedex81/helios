/*
 * SavestateGameLoader
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 17:02
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

package omegadrive.automated;

import com.google.common.collect.ImmutableMap;
import omegadrive.SystemLoader;
import omegadrive.save.SavestateTest;
import omegadrive.system.SystemProvider;
import omegadrive.util.Util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static omegadrive.system.SystemProvider.SystemEvent.*;

public class SavestateGameLoader {

    public static String romFolder = "/home/fede/roms/test/savestate_test";

    private static String saveStateFolder = SavestateTest.saveStateFolder.toAbsolutePath().toString();

    private static final String s2_rom = "Sonic The Hedgehog 2 (World) (Rev A).zip";
    private static final String s1_rom = "Sonic The Hedgehog (USA, Europe).zip";
    private static final String sor2_rom = "Streets of Rage 2 (USA).zip";
    private static final String batr_rom = "Adventures of Batman & Robin, The (USA).zip";
    private static final String cmx_rom = "Comix Zone (USA).zip";
    private static final String fant_rom = "Fantasia (World) (Rev A).zip";
    private static final String gaxe_rom = "Golden Axe (World) (Rev A).zip";
    private static final String gaxe2_rom = "Golden Axe II (World).zip";
    private static final String micMania_rom = "Mickey Mania - The Timeless Adventures of Mickey Mouse (USA).zip";
    private static final String mk2_rom = "Mortal Kombat II (World).zip";
    private static final String spark_rom = "Sparkster (USA).zip";
    private static final String s3d_rom = "Sonic 3D Blast ~ Sonic 3D Flickies' Island (USA, Europe, Korea).zip";
    private static final String tf4_rom = "Lightening Force - Quest for the Darkstar (USA).zip";
    private static final String vec_rom = "Vectorman (USA, Europe).zip";
    private static final String vec2_rom = "Vectorman 2 (USA).zip";

    private static final String contra_rom = "Contra - The Hard Corps (J) [!].bin.zip";
    private static final String dynhead_rom = "Dynamite Headdy (J) [c][!].bin.zip";

    public static Map<String, String> saveStates = ImmutableMap.<String, String>builder().
            put("SONIC3D.GS0", s3d_rom).
            put("SONIC3D.GS1", s3d_rom).
            put("Thunder4.gs0", tf4_rom).
            put("Vector2.gs5", vec2_rom).
            put("Vector2.gs6", vec2_rom).
            put("Vector2.gs7", vec2_rom).
            put("VECTORMA.GS0", vec_rom).
            put("sor2.gs0", sor2_rom).
            put("sor2.gs1", sor2_rom).
            put("sor2.gs2", sor2_rom).
            put("sor2.gs3", sor2_rom).
            put("s2_int.gs0", s2_rom).
            put("test01.gs0", s1_rom).
            put("test02.gs0", s2_rom).
            put("BATROB.gs0", batr_rom).
            put("BATROB.gs9", batr_rom).
            put("COMIX_ZN.GS0", cmx_rom).
            put("FANTASI.gs0", fant_rom).
            put("FANTASI.gs9", fant_rom).
            put("G-AXE.GS0", gaxe_rom).
            put("G-AXE.GS1", gaxe_rom).
            put("G-AXE.GS2", gaxe_rom).
            put("G-AXE.GS4", gaxe_rom).
            put("G-AXE.GS8", gaxe_rom).
            put("G-AXE.GS9", gaxe_rom).
            put("G-AXE2.GS1", gaxe2_rom).
            put("G-AXE2.GS2", gaxe2_rom).
            put("mickeym.gs0", micMania_rom).
            put("mickeym.gs1", micMania_rom).
            put("mickeym.gs2", micMania_rom).
            put("mickeym.gs3", micMania_rom).
            put("mickeym.gs9", micMania_rom).
            put("MORTAL2.GS0", mk2_rom).
            put("Rocket2.gs0", spark_rom).
            put("Rocket2.gs8", spark_rom).
            put("Rocket2.gs9", spark_rom).
            put("CONTRA4.GS0", contra_rom).
            put("D_HEDD_T.gs0", dynhead_rom).
            build();


    static int saveStateTestNumber = 21;
    static SystemLoader loader;

    public static void main(String[] args) {
        loader = SystemLoader.getInstance();
//        loadOne();
        loadAll(false);
    }

    public static void loadOne() {
        SystemProvider genesis = load(loader, saveStates.keySet().toArray()[saveStateTestNumber].toString(),
                saveStates.values().toArray()[saveStateTestNumber].toString());
        Util.sleep(10_000);
        genesis.handleSystemEvent(CLOSE_APP, null);
    }

    public static void loadAll(boolean loop) {
        SystemProvider genesis = null;
        do {
            for (Map.Entry<String, String> entry : saveStates.entrySet()) {
                genesis = load(loader, entry.getKey(), entry.getValue());
                Util.sleep(10_000);
                genesis.handleSystemEvent(CLOSE_ROM, null);
                Util.sleep(1_000);
            }
        } while (loop);
        Optional.ofNullable(genesis).ifPresent(g -> g.handleSystemEvent(CLOSE_APP, null));
    }


    public static SystemProvider load(SystemLoader loader, String saveFileName, String romFile) {
        Path rom = Paths.get(romFolder, romFile);
        Path saveFile = Paths.get(saveStateFolder, saveFileName);
        System.out.println("Loading ROM: " + rom.toAbsolutePath().toString());
        System.out.println("Loading state file: " + saveFileName);
        SystemProvider genesis = loader.createSystemProvider(rom);
        genesis.handleSystemEvent(NEW_ROM, rom);
        Util.sleep(1_000);
        genesis.handleSystemEvent(LOAD_STATE, saveFile);
        return genesis;
    }
}
