import mmcorej.CMMCore;

/* test_stream.java
 * Created May, 2007
 *
 * MicroManager sample code
 */

/**
 * Example program to demonstrate multi-threaded fast acquistion and disk streaming.
 * Requires MMJ_.jar and ij.jar on the classpath.
 */
public class Test_MMStream {
   
   public static void main(String[] args) {
      // Initialize the system
      CMMCore core = new CMMCore();
      try {
         core.loadSystemConfiguration("MMConfig_stream.cfg");
         
         final int numFrames = 50;
         final int memoryFootprintMB = 25;
         final double intervalMs = 50.0; // ms

         core.setCircularBufferMemoryFootprint(memoryFootprintMB);

         System.out.println("Buffer capacity: " + core.getBufferTotalCapacity());
         String camera = core.getCameraDevice();
         core.setExposure(10);
         core.startSequenceAcquisition(numFrames, intervalMs);

         DiskStreamingThread streamWriter = new DiskStreamingThread(core);
         
         Thread.sleep(300);
         streamWriter.start();

         while (core.deviceBusy(camera)) {
            core.getLastImage();
            System.out.println("Displaying current image, " + core.getRemainingImageCount() + " images waiting in que.");
            Thread.sleep(110);
         }
         
         streamWriter.join();

         System.out.println("Done! Free space = " + core.getBufferFreeCapacity());
         
      } catch (Exception e){
         e.printStackTrace();
      }
   }
   
   private static void handleException(Exception e) {
      System.out.println("Exception: " + e.getMessage() + "\nExiting now.");
      System.exit(1);        
   }
}
