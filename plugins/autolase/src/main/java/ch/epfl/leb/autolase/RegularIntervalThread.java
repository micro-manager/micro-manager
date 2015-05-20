package ch.epfl.leb.autolase;

/**
 * This class represents a thread that executes a specified operation at 
 * regular intervals.
 * 
 * @author Thomas Pengo
 */
public abstract class RegularIntervalThread implements Runnable {
    public static final int DEFAULT_UPDATE_TIME = 250;
    
    long updateTime = DEFAULT_UPDATE_TIME;
    Runnable theThread;

    boolean running = false;
    boolean stopping = false;

    /**
     * Start/stops the execution of the thread. 
     * 
     * @param running 
     */
    public void setRunning(boolean running) {
        this.running = running;
    }

    /**
     * Returns true if the code is executing.
     * 
     * @return 
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Creates a new ticker running theThread at specified intervals.
     * 
     * @param theThread 
     */
    public RegularIntervalThread(Runnable theThread) {
        this.theThread = theThread;
    }

    /**
     * Changes the update time to updateTime.
     * 
     * @param updateTime 
     */
    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public void run() {
        
        long lastTime = System.currentTimeMillis();
        while(!stopping) {
            
            if (running)
                theThread.run();
                
            
            long waitTime = lastTime+updateTime-System.currentTimeMillis();
            if (waitTime>0)
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    stopping = true;
                }
            
            lastTime = System.currentTimeMillis();
        }
    }
   
}
