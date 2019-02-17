package omegadrive.ui;

import java.awt.*;
import java.util.Random;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class RenderingStrategyTest {

    private static Random rnd = new Random();
    static int w = 320;
    static int h = 240;
    static int ow = 1920;
    static int oh = 1080;
    static int[][] screenData = new int[w][h];
    static int[] linear = new int[w * h];
    static int[] output = new int[ow * oh];
    static Dimension inputD = new Dimension(w, h);
    static Dimension outputD = new Dimension(ow, oh);

    public static void main(String[] args) {
//        testNearest();
        testLinear();
    }

    private static void testLinear() {
        System.out.println("testLinear - Warmup");
        //warm-up
        testLinear(10000);
        System.out.println("testLinear");
        for (int i = 0; i < 6; i++) {
            testLinear(10000);
        }
    }

    private static void testNearest() {
        System.out.println("testNearest - Warmup");
        //warm-up
        testNearest(1000);
        System.out.println("testNearest");
        for (int i = 0; i < 6; i++) {
            testNearest(1000);
        }
    }


    private static void testNearest(int cycles) {
        long start = System.nanoTime();
        //warm-up
        for (int i = 0; i < cycles; i++) {
            screenData = getData(screenData, inputD);
            RenderingStrategy.toLinear(linear, screenData, inputD);
            RenderingStrategy.renderNearest(linear, output, inputD, outputD);
        }
        System.out.println("Time ms: " + (System.nanoTime() - start) / 1_000_000d);
    }

    private static void testLinear(int cycles) {
        long start = System.nanoTime();
        //warm-up
        for (int i = 0; i < cycles; i++) {
            screenData = getData(screenData, inputD);
            RenderingStrategy.toLinear(linear, screenData, inputD);
        }
        System.out.println("Time ms: " + (System.nanoTime() - start) / 1_000_000d);
    }

    private static int[][] getData(int[][] screenData, Dimension input) {
        int factor = rnd.nextInt(10);

        for (int i = 0; i < input.width; i++) {
            for (int j = 0; j < input.height; j++) {
                screenData[i][j] = factor * i * input.width + j;
            }
        }
        return screenData;
    }
}
