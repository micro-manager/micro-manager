/* 
 * Test_MMCoreJ.java
 * Created on Jul 8, 2005
 *
 * Test program for MMCore Java interface : MMCoreJ
 */

/**
 * @author nenada
 *
 */

public class Test_MMCoreJ {
   CMMCore m_core;
   static final String CAMERA = "Camera";
   
   public static void TestMode(CMMCore core, String mode){
      try {
         // set mode
         System.out.println("Testing mode " + mode);
         core.setProperty(CAMERA, "Mode", mode);
         
         StrVector props = core.getDevicePropertyNames(CAMERA);
         System.out.println("Device properties:");
         for (int i=0; i<props.size(); i++) {
            StrVector allowedVals = core.getAllowedPropertyValues(CAMERA, props.get(i));
            System.out.println("   " + props.get(i) + " = " + core.getProperty(CAMERA, props.get(i)));
            for (int j=0; j<allowedVals.size(); j++){
               System.out.println("      " + allowedVals.get(j));
            }
         }
         
         // snap single image
         core.setExposure(100);
         core.snapImage();
         if (core.getBytesPerPixel() == 1) {
            // 8-bit grayscale pixels
            byte[] img = (byte[])core.getImage();
            System.out.println("Image snapped, " + img.length + " pixels total, 8 bits each.");
            System.out.println("Pixel [0] value = " + img[0]);
         }
         else if (core.getBytesPerPixel() == 2){
            // 16-bit grayscale pixels
            short[] img = (short[])core.getImage();
            System.out.println("Image snapped, " + img.length + " pixels total, 16 bits each.");
            System.out.println("Pixel [0] value = " + img[0]);             
         }
         else {
            System.out.println("Dont' know how to handle images with " +
                               core.getBytesPerPixel() + " byte pixels.");             
         }
      } catch (Exception e) {
         System.out.println("Exception: " + e.getMessage() + "\nExiting now.");
         System.exit(1);
      }
   }
   
	public static void main(String[] args) {
		// Load the native MMCore wrapper library
		System.loadLibrary("MMCoreJ_wrap");

		// Initialize the system
		CMMCore core = new CMMCore();
      try {
         core.loadDevice("Camera", "Hamamatsu", "Hamamatsu_DCAM");
         //core.LoadDevice("Sensicam", "Sensicam");
         core.initializeAllDevices();
      } catch (Exception e){
         System.out.println("Exception: " + e.getMessage() + "\nExiting now.");
         System.exit(1);        
      }
        
		// test 512, 8-bit mode
      TestMode(core, "512-8bit");
      
      // test 512, 16-bit mode
      TestMode(core, "512-12bit");
      
      // test 1024, 8-bit mode
      TestMode(core, "1024-8bit");
      
      // test 1024, 16-bit mode
      TestMode(core, "1024-12bit");
      
      // ...now try massive image acquisition to test for meamory load and
      // potential leaks in the native libraries
      int numSnaps = 3;
      System.out.println("Memory stress test starting, " + numSnaps + " images.");
      for (int i=0; i<numSnaps; i++){
         long startTime = System.currentTimeMillis();
         try {
            core.snapImage();
            if (core.getBytesPerPixel() == 1) {
               // 8-bit grayscale pixels
               byte[] img = (byte[])core.getImage();
            }
            else if (core.getBytesPerPixel() == 2){
               // 16-bit grayscale pixels
               short[] img = (short[])core.getImage();
            }
            else {
               System.out.println("Unexpected " + core.getBytesPerPixel() + " pixel depth.");
               System.exit(1);
            }
         } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage() + "\nExiting now.");
            System.exit(1);        
         }
         long endTime = System.currentTimeMillis();
         System.out.println("Image " + i + " snapped in " + (endTime-startTime) + "ms.");
      }
	}
}
