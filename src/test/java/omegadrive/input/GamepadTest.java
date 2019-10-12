/*
 * GamepadTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 12/10/19 17:12
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

import net.java.games.input.Controller;
import omegadrive.joypad.JoypadProvider;

import java.io.File;

public class GamepadTest {

    private static Controller controller;
    public static float ON = 1.0f;

    public static void main(String[] args) {
        String lib = new File(".").getAbsolutePath() + File.separator + "privateLib"
                + File.separator + "linux";
        System.out.println(lib);
        System.setProperty("log4j.configurationFile", "./res/log4j2-info.properties");
        System.setProperty("jinput.enable", "true");
        System.setProperty("jinput.detect.debug", "true");
        System.setProperty("net.java.games.input.librarypath", lib);
        InputProvider inputProvider = InputProvider.createInstance(createTestJoypadProvider());
        pollInputs(inputProvider);
    }

    private static JoypadProvider createTestJoypadProvider() {
        return new JoypadProvider() {
            @Override
            public void init() {

            }

            @Override
            public int readDataRegister1() {
                return 0;
            }

            @Override
            public int readDataRegister2() {
                return 0;
            }

            @Override
            public int readDataRegister3() {
                return 0;
            }

            @Override
            public long readControlRegister1() {
                return 0;
            }

            @Override
            public long readControlRegister2() {
                return 0;
            }

            @Override
            public long readControlRegister3() {
                return 0;
            }

            @Override
            public void writeDataRegister1(long data) {

            }

            @Override
            public void writeDataRegister2(long data) {

            }

            @Override
            public void writeControlRegister1(long data) {

            }

            @Override
            public void writeControlRegister2(long data) {

            }

            @Override
            public void writeControlRegister3(long data) {

            }

            @Override
            public void setButtonAction(JoypadNumber number, JoypadButton button, JoypadAction action) {
                System.out.println(number + "," + button + "," + action);
            }

            @Override
            public boolean hasDirectionPressed(JoypadNumber number) {
                return false;
            }

            @Override
            public String getState(JoypadNumber number) {
                return null;
            }

            @Override
            public void newFrame() {

            }
        };
    }

    private static void pollInputs(InputProvider inputProvider) {
        while (true) {
            inputProvider.handleEvents();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
