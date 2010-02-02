
public class TestZeiss {

   /**
    * @param args
    */
   public static void main(String[] args) {
      // Initialize the system
      CMMCore core = new CMMCore();
      try {
         // load devices
         core.loadDevice("Camera", "Hamamatsu", "Hamamatsu_DCAM");
         core.loadDevice("REFLECTOR", "ZeissMTB", "Reflector");         
         core.initializeAllDevices();
 
         StrVector labels = core.getAllowedPropertyValues("REFLECTOR", "Label");
         for (int i=0; i<labels.size(); i++)
         {
            System.out.println( "Setting label " + labels.get(i));
            core.setProperty("REFLECTOR", "Label", labels.get(i));
            core.waitForSystem();
            String newLabel = core.getProperty("REFLECTOR", "Label");
            System.out.println( "Label " + newLabel + " set.");
         }

      } catch (Exception e){
         System.out.println("Exception: " + e.getMessage() + "\nExiting now.");
         System.exit(1);        
      }
   }

}
