package omegadrive.input;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import omegadrive.joypad.JoypadProvider;

import java.io.File;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class GamepadTest {

    private static Controller controller;
    public static float ON = 1.0f;

    public static void main(String[] args) {
        String lib = new File(".").getAbsolutePath() + File.separator + "privateLib"
                + File.separator + "linux";
        System.out.println(lib);
        System.setProperty("net.java.games.input.librarypath", lib);
        InputProvider inputProvider = InputProvider.createInstance(createTestJoypadProvider());
        pollInputs(inputProvider);
    }

    private static JoypadProvider createTestJoypadProvider() {
        return new JoypadProvider() {
            @Override
            public void initialize() {

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

    public static String detectControllerVerbose() {
        Controller[] ca = ControllerEnvironment.getDefaultEnvironment().getControllers();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < ca.length; i++) {
            /* Get the name of the controller */
            sb.append("\n" + ca[i].getName() + "\n");
            sb.append("Position: [" + i + "]\n");
            sb.append("Type: " + ca[i].getType().toString() + "\n");

            /* Get this controllers components (buttons and axis) */
            Component[] components = ca[i].getComponents();
            sb.append("Component Count: " + components.length + "\n");
            for (int j = 0; j < components.length; j++) {

                /* Get the components name */
                sb.append("Component " + j + ": " + components[j].getName() + "\n");
                sb.append("    Identifier: " + components[j].getIdentifier().getName() + "\n");
                sb.append("    ComponentType: ");
                if (components[j].isRelative()) {
                    sb.append("Relative");
                } else {
                    sb.append("Absolute");
                }
                if (components[j].isAnalog()) {
                    sb.append(" Analog");
                } else {
                    sb.append(" Digital");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
