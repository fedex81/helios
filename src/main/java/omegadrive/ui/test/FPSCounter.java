package omegadrive.ui.test;

public class FPSCounter {
    private float currentFPS = 0;
    private int count = 0;

    private long timeStamp = 0L;

    private static final long ONE_SECOND = 1000000000L;

    public FPSCounter() {
        timeStamp = System.nanoTime();
    }

    public void countFrame() {
        count++;

        // Calculate expired time
        long currentTimeStamp = System.nanoTime();
        long expiredTime = currentTimeStamp - timeStamp;

        // If more then a second has expired then store the current FPS and
        // reset the timestamp
        if (expiredTime >= ONE_SECOND) {
            timeStamp = currentTimeStamp;
            currentFPS = (float) (count / (((double) expiredTime) / ((double) ONE_SECOND)));
            count = 0;
        }
    }

    public float getFPS() {
        return currentFPS;
    }
}
