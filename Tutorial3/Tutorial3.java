
public class Tutorial3 {

   public static void main(String[] args) {
      
      // create core object
      CMMCore core = new CMMCore();
      
      try {
         // load devices
         core.loadDevice("Camera", "DemoCamera", "DCam");
         core.loadDevice("Emmision", "DemoCamera", "DWheel");
         core.loadDevice("Exictation", "DemoCamera", "DWheel");
         core.loadDevice("Shutter", "DemoCamera", "DWheel");
         core.loadDevice("Z", "DemoCamera", "DStage");
         
         // initialize
         core.initializeAllDevices();
         
         // list devices
         StrVector devices = core.getLoadedDevices();
         System.out.println("Device status:");
         
         for (int i=0; i<devices.size(); i++){
            System.out.println(devices.get(i)); 
            // list device properties
            StrVector properties = core.getDevicePropertyNames(devices.get(i));
            if (properties.size() == 0)
               System.out.println("   No properties.");
            for (int j=0; j<properties.size(); j++){
               System.out.println("   " + properties.get(j) + " = "
                     + core.getProperty(devices.get(i), properties.get(j)));
               StrVector values = core.getAllowedPropertyValues(devices.get(i), properties.get(j));
               for (int k=0; k<values.size(); k++){
                  System.out.println("      " + values.get(k));
               }
            }
         }
         
      } catch (Exception e) {
         System.out.println(e.getMessage());
         System.exit(1);
      }      
   }
}
