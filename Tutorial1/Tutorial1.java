import mmcorej.CMMCore;


public class Tutorial1 {
   public static void main(String[] args) {
		
      CMMCore core = new CMMCore();
      try {
         core.loadDevice("Camera", "DemoCamera", "DCam");
         core.initializeDevice("Camera");

         core.setExposure(50);
         core.snapImage();
			
         if (core.getBytesPerPixel() == 1) {
            // 8-bit grayscale pixels
            byte[] img = (byte[])core.getImage();
            System.out.println("Image snapped, " + img.length + " pixels total, 8 bits each.");
            System.out.println("Pixel [0,0] value = " + img[0]);
         } else if (core.getBytesPerPixel() == 2){
            // 16-bit grayscale pixels
            short[] img = (short[])core.getImage();
            System.out.println("Image snapped, " + img.length + " pixels total, 16 bits each.");
            System.out.println("Pixel [0,0] value = " + img[0]);             
         } else {
            System.out.println("Dont' know how to handle images with " +
                  core.getBytesPerPixel() + " byte pixels.");             
         }
      } catch (Exception e) {
         System.out.println(e.getMessage());
         System.exit(1);
      }		
   }	
}
