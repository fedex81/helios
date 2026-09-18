package mcd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mcd.McdWordRamTest.subGetLsbFn;
import static mcd.McdWordRamTest.subSetLsbFn;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdWram2MTest extends McdRegTestBase {

    @BeforeEach
    public void setup() {
        setupBase();
    }


    @Test
    public void testWramToMain() {
        assert ctx.wramSetup.mode == _2M;
//        giveWordRAMAccess_SubToMain();
    }


    //SonicCD "Title Screen/Sub.asm"
    void giveWordRAMAccess_SubToMain() {
        int gamemode = subGetLsbFn.apply(lc.subBus);

        // If the Main CPU already has access (Bit 0 is 0), exit immediately
        if ((gamemode & 0x01) == 0) {
            return;
        }

        // Clear bit 0 to request Main CPU access
        // (Equivalent to the intended hardware bclr/bset action)
//        GAMEMMODE &= ~0x01;
        gamemode &= ~1;
        subSetLsbFn.accept(lc.subBus, gamemode);

        // Wait until the hardware actually grants access (Bit 0 becomes 0)
        while ((subGetLsbFn.apply(lc.subBus) & 0x01) != 0) {
            // Busy wait loop
        }
    }
}