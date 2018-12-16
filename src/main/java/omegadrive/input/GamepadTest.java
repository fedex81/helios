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
            public void setD(int value) {
                System.out.println("DOWN: " + value);
            }

            @Override
            public void setU(int value) {
                System.out.println("UP: " + value);
            }

            @Override
            public void setL(int value) {
                System.out.println("LEFT: " + value);
            }

            @Override
            public void setR(int value) {
                System.out.println("RIGHT: " + value);
            }

            @Override
            public void setA(int value) {
                System.out.println("A: " + value);
            }

            @Override
            public void setB(int value) {
                System.out.println("B: " + value);
            }

            @Override
            public void setC(int value) {
                System.out.println("C: " + value);
            }

            @Override
            public void setS(int value) {
                System.out.println("START: " + value);
            }

            @Override
            public void setD2(int value) {

            }

            @Override
            public void setU2(int value) {

            }

            @Override
            public void setL2(int value) {

            }

            @Override
            public void setR2(int value) {

            }

            @Override
            public void setA2(int value) {

            }

            @Override
            public void setB2(int value) {

            }

            @Override
            public void setC2(int value) {

            }

            @Override
            public void setS2(int value) {

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

    private static Controller detectController() {

        Controller[] ca = ControllerEnvironment.getDefaultEnvironment().getControllers();


        for (int i = 0; i < ca.length; i++) {
            /* Get the name of the controller */
            System.out.println(ca[i].getName());
            System.out.println("Type: " + ca[i].getType().toString());

            /* Get this controllers components (buttons and axis) */
            Component[] components = ca[i].getComponents();
            System.out.println("Component Count: " + components.length);
            for (int j = 0; j < components.length; j++) {

                /* Get the components name */
                System.out.println("Component " + j + ": " + components[j].getName());
                System.out.println("    Identifier: " + components[j].getIdentifier().getName());
                System.out.print("    ComponentType: ");
                if (components[j].isRelative()) {
                    System.out.print("Relative");
                } else {
                    System.out.print("Absolute");
                }
                if (components[j].isAnalog()) {
                    System.out.print(" Analog");
                } else {
                    System.out.print(" Digital");
                }
                controller = ca[i];
            }
        }
        return controller;
    }
}
