/*
 * JavaSoundManager
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 17:40
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

package omegadrive.sound.javasound;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static omegadrive.sound.javasound.JavaSoundManagerBlip.MAX_SAMPLE_DIFF_PER_FRAME;
import static omegadrive.sound.javasound.JavaSoundManagerBlip.getRateControl;

public class JavaSoundManagerBlipTest {

    @Test
    public void testRateControl() {
        int current = 12;
        int limit = 20;
        int arrayLen = limit - current;
        int delta = MAX_SAMPLE_DIFF_PER_FRAME;
        int[] exp = new int[arrayLen];
        Arrays.fill(exp, -delta);
        exp[0] = delta;
        exp[1] = 0;
        int[] act = exp.clone();
        Arrays.fill(act, -1);

        for (int i = current; i < limit; i++) {
            int res = getRateControl(current, i);
            act[i - current] = res;
            System.out.println(current + "," + i + "," + res);
        }
        Assertions.assertArrayEquals(exp, act);
    }
}

