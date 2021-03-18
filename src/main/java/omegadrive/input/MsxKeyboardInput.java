/*
 * MsxKeyboardInput
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

package omegadrive.input;

import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Taken from the TMSX project, see KeyboardDecoder
 * https://github.com/tjitze/TMSX
 *
 * @author tjitze.rienstra
 */
public class MsxKeyboardInput extends KeyboardInput {

    //TODO fix
    protected static MsxKeyboardInput currentAdapter;

    public MsxKeyboardInput() {
        currentAdapter = this;
    }

    public static MsxKeyboardInput getMsxKeyAdapter() {
        return currentAdapter;
    }

    /*
     * This array contains the MSX key matrix (11 rows of 8 bits each)
     * The array is updated by the pressKey and depressKey methods and
     * is read out by the getRowValue method.
     */
        private byte[] rows = new byte[11];

        /*
         * Matrix for translating characters to key matrix codes.
         * Every entry is of the form {character, row, column},
         * meaning that character is mapped to the matrix at position
         * row/column.
         */
        private int[][] charMatrix = {
                {'0', 0, 0}, {')', 0, 0}, {'1', 0, 1}, {'!', 0, 1}, {'2', 0, 2}, {'@', 0, 2},
                {'3', 0, 3}, {'#', 0, 3}, {'4', 0, 4}, {'$', 0, 4}, {'5', 0, 5}, {'%', 0, 5},
                {'6', 0, 6}, {'&', 0, 6}, {'7', 0, 7}, {'\'', 0, 7},

                {'8', 1, 0}, {'*', 1, 0}, {'9', 1, 1}, {'(', 1, 1}, {'-', 1, 2}, {'_', 1, 2},
                {'=', 1, 3}, {'+', 1, 3}, {'\\', 1, 4}, {'|', 1, 4}, {'[', 1, 5}, {'{', 1, 5},
                {']', 1, 6}, {'}', 1, 6}, {';', 1, 7}, {':', 1, 7},

                {'A', 2, 6}, {'B', 2, 7}, {'a', 2, 6}, {'b', 2, 7},
                {'/', 2, 4}, {'?', 2, 4}, {'.', 2, 3}, {'>', 2, 3},
                {',', 2, 2}, {'<', 2, 2}, {'`', 2, 1}, {'~', 2, 1},
                {'\'', 2, 0}, {'"', 2, 0},

                {'C', 3, 0}, {'D', 3, 1}, {'E', 3, 2}, {'F', 3, 3}, {'G', 3, 4}, {'H', 3, 5},
                {'I', 3, 6}, {'J', 3, 7},
                {'c', 3, 0}, {'d', 3, 1}, {'e', 3, 2}, {'f', 3, 3}, {'g', 3, 4}, {'h', 3, 5},
                {'i', 3, 6}, {'j', 3, 7},

                {'K', 4, 0}, {'L', 4, 1}, {'M', 4, 2}, {'N', 4, 3}, {'O', 4, 4}, {'P', 4, 5},
                {'Q', 4, 6}, {'R', 4, 7},
                {'k', 4, 0}, {'l', 4, 1}, {'m', 4, 2}, {'n', 4, 3}, {'o', 4, 4}, {'p', 4, 5},
                {'q', 4, 6}, {'r', 4, 7},

                {'S', 5, 0}, {'T', 5, 1}, {'U', 5, 2}, {'V', 5, 3}, {'W', 5, 4}, {'X', 5, 5},
                {'Y', 5, 6}, {'Z', 5, 7},
                {'s', 5, 0}, {'t', 5, 1}, {'u', 5, 2}, {'v', 5, 3}, {'w', 5, 4}, {'x', 5, 5},
                {'y', 5, 6}, {'z', 5, 7}

        };

        /*
         * Matrix for translating special keys to key matrix codes.
         * Every entry is of the form {KeyEvent, row, column},
         * meaning that KeyEvent is mapped to the matrix at position
         * row/column.
         */
        private int[][] keyMatrix = {
                {KeyEvent.VK_SHIFT, 		6, 0},
                {KeyEvent.VK_CONTROL,		6, 1},
                {KeyEvent.VK_ALT_GRAPH, 	6, 2}, // GRAPH ??
                {KeyEvent.VK_CAPS_LOCK,		6, 3},
                {KeyEvent.VK_CODE_INPUT, 	6, 4}, // CODE ??
                {KeyEvent.VK_F1, 			6, 5},
                {KeyEvent.VK_F2, 			6, 6},
                {KeyEvent.VK_F3, 			6, 7},

                {KeyEvent.VK_F4, 			7, 0},
                {KeyEvent.VK_F5,			7, 1},
                {KeyEvent.VK_ESCAPE, 		7, 2},
                {KeyEvent.VK_TAB,			7, 3},
                {KeyEvent.VK_STOP, 			7, 4}, // STOP ??
                {KeyEvent.VK_BACK_SPACE,	7, 5},
                {KeyEvent.VK_F6, 			7, 6}, // SELECT ??
                {KeyEvent.VK_ENTER, 		7, 7},

                {KeyEvent.VK_SPACE,			8, 0},
                {KeyEvent.VK_HOME,			8, 1},
                {KeyEvent.VK_INSERT, 		8, 2},
                {KeyEvent.VK_DELETE,		8, 3},
                {KeyEvent.VK_LEFT, 			8, 4},
                {KeyEvent.VK_UP,			8, 5},
                {KeyEvent.VK_DOWN, 			8, 6},
                {KeyEvent.VK_RIGHT, 		8, 7},

                /* We ignore row 9 and 10 (numeric pad keys) */
        };

        @Override
        public void keyTyped(KeyEvent e) {	}

        @Override
        public void keyPressed(KeyEvent e) {
            processKeyEvent(e, true);
        }

        @Override
        public void keyReleased(KeyEvent e) {
            processKeyEvent(e, false);
        }

        public void stopKeyPressed() {
            pressKey(7, 4);
        }

        public void stopKeyDepressed() {
            depressKey(7, 4);
        }

        /**
         * Process key event.
         *
         * @param e The key event.
         * @param state true if key pressed, false if key depressed
         */
        private void processKeyEvent(KeyEvent e, boolean state) {

            /* Try to match character and translate it to a pressKey/depressKey call */
            int c = (int)e.getKeyChar();
            boolean success = false;
            keySearchLoop:
            for (int i = 0; i < charMatrix.length; i++) {
                if (c == charMatrix[i][0]) {
                    if (state) {
                        pressKey(charMatrix[i][1], charMatrix[i][2]);
                    } else {
                        depressKey(charMatrix[i][1], charMatrix[i][2]);
                    }
                    success = true;
                    break keySearchLoop;
                }
            }

            /* Try to match key and translate it to a pressKey/depressKey call */
            if (!success) {
                int k = e.getKeyCode();
                for (int i = 0; i < keyMatrix.length; i++) {
                    if (k == keyMatrix[i][0]) {
                        if (state) {
                            pressKey(keyMatrix[i][1], keyMatrix[i][2]);
                        } else {
                            depressKey(keyMatrix[i][1], keyMatrix[i][2]);
                        }
                    }
                }
            }
        }

        /**
         * Set value at given row/bit position to true.
         */
        private void pressKey(int row, int bit) {
            rows[row] = (byte)((rows[row] & 0xff) | (1 << bit));
        }

        /**
         * Set value at given row/bit position to false.
         */
        private void depressKey(int row, int bit) {
            rows[row] = (byte)((rows[row] & 0xff) & ~(1 << bit));
        }

        /**
         * Get the byte value of the given row. If a bit
         * is set to true, it means that the key is pressed.
         * (Warning: this value must be inverted according
         * to MSX spec)
         *
         * @param row Value of row to get.
         * @return Byte containing all bits of given row.
         */
        public byte getRowValue(int row) {
            return rows[row];
        }

        /**
         * This doesn't seem to work.
         *
         * @param state
         */
        public void setCapslock(boolean state) {
            if (state != Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK)) {
                Toolkit.getDefaultToolkit().setLockingKeyState(KeyEvent.VK_CAPS_LOCK, state);
            }
        }

    }
