import mmcorej.CMMCore;

public class DiskStreamingThread extends Thread {

   CMMCore core_;
   public DiskStreamingThread(CMMCore core) {
      core_ = core;
   }

   public void run() {
      int count = 0;
      String camera = core_.getCameraDevice();
      try {
         while (core_.getRemainingImageCount() > 0 || core_.deviceBusy(camera))
         {
            if (core_.getRemainingImageCount() > 0)
            {
               Object img = core_.popNextImage();
               Thread.sleep(350);
               System.out.println("Saved image: " + count++);
            }
         }
      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }
}


