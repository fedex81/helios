package omegadrive.ui;

import org.junit.Assert;

import java.awt.*;
import java.util.Arrays;
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

    static int ow = w * 6;  //1920
    static int oh = (int) (h * 4.5); //1080

    static int[][] screenData = new int[w][h];
    static int[] linear = new int[w * h];
    static int[] output = new int[ow * oh];
    static Dimension inputD = new Dimension(w, h);
    static Dimension outputD = new Dimension(ow, oh);

    public static void main(String[] args) {
        testNearest();
        testNearestOld();
    }

    private static void testLinearCompare() {
        int[] linearNew = Arrays.copyOf(linear, linear.length);

        screenData = getData(screenData, inputD);
        RenderingStrategy.toLinear(linear, screenData, inputD);

//        RenderingStrategy.toLinearNew(linearNew, screenData, inputD);

        Assert.assertTrue(Arrays.equals(linear, linearNew));


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

    private static void testLinearNew() {
        System.out.println("testLinearNew - Warmup");
        //warm-up
        testLinearNew(10000);
        System.out.println("testLinearNew");
        for (int i = 0; i < 6; i++) {
            testLinearNew(10000);
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

    private static void testNearestOld() {
        System.out.println("testNearestOld - Warmup");
        //warm-up
        renderNearestOld(1000);
        System.out.println("testNearestOld");
        for (int i = 0; i < 6; i++) {
            renderNearestOld(1000);
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
        printPerf(System.nanoTime() - start, cycles);
    }

    private static void renderNearestOld(int cycles) {
        long start = System.nanoTime();
        //warm-up
        for (int i = 0; i < cycles; i++) {
            screenData = getData(screenData, inputD);
            RenderingStrategy.toLinear(linear, screenData, inputD);
//            RenderingStrategy.renderNearestOld(linear, output, inputD, outputD);
        }
        printPerf(System.nanoTime() - start, cycles);
    }

    private static void testLinearNew(int cycles) {
        long start = System.nanoTime();
        //warm-up
        for (int i = 0; i < cycles; i++) {
            screenData = getData(screenData, inputD);
//            RenderingStrategy.toLinearNew(linear, screenData, inputD);
        }
        printPerf(System.nanoTime() - start, cycles);
    }

    private static void testLinear(int cycles) {
        long start = System.nanoTime();
        //warm-up
        for (int i = 0; i < cycles; i++) {
            screenData = getData(screenData, inputD);
            RenderingStrategy.toLinear(linear, screenData, inputD);
        }
        printPerf(System.nanoTime() - start, cycles);
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

    private static void printPerf(long intervalNs, int cycles) {
        double timeMs = intervalNs / 1_000_000d;
        double fps = cycles / (timeMs / 1000);
        System.out.println("Time ms: " + timeMs + ", FPS: " + fps);
    }
}
