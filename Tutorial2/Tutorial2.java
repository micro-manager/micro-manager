
public class Tutorial2 {
   public static void main(String[] args) {
      CMMCore core = new CMMCore();
      try {
         // load camera adapter
         core.loadDevice("Camera", "DemoCamera", "DCam");
         core.initializeDevice("Camera");

         // set some properties
         core.setProperty("Camera", "Binning", "2");
         core.setProperty("Camera", "PixelType", "8bit");
         
         // list all properties and allowed values
         StrVector props = core.getDevicePropertyNames("Camera");
         System.out.println("Device properties:");
         for (int i=0; i<props.size(); i++) {
            StrVector allowedVals = core.getAllowedPropertyValues("Camera", props.get(i));
            System.out.println("   " + props.get(i) + " = " + core.getProperty("Camera", props.get(i)));
            for (int j=0; j<allowedVals.size(); j++){
               System.out.println("      " + allowedVals.get(j));
            }
         }
      } catch (Exception e) {
         System.out.println(e.getMessage());
         System.exit(1);
      }       
   }
}
