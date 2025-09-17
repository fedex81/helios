package s32x.sh2.drc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import s32x.sh2.Sh2Instructions;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class Sh2DrcInstTest {

    @Test
    public void testTrapaIsBranch() {
        Assertions.assertTrue(Sh2Instructions.Sh2BaseInstruction.TRAPA.isBranch());
    }
}
