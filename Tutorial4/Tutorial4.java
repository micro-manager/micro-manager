
public class Tutorial4 {
   
   public static void main(String[] args) {
      
      // create core object
      CMMCore core = new CMMCore();
      
      try {
         
         // load devices
         core.loadDevice("Camera", "DemoCamera", "DCam");
         core.loadDevice("Emission", "DemoCamera", "DWheel");
         core.loadDevice("Excitation", "DemoCamera", "DWheel");
         core.loadDevice("Dichroic", "DemoCamera", "DWheel");
         core.loadDevice("Objective", "DemoCamera", "DObjective");
         core.loadDevice("X", "DemoCamera", "DStage");
         core.loadDevice("Y", "DemoCamera", "DStage");
         core.loadDevice("Z", "DemoCamera", "DStage"); 
         
         core.initializeAllDevices();
         
         // Set labels for state devices
         //
         // emission filter
         core.defineStateLabel("Emission", 0, "Chroma-D460");
         core.defineStateLabel("Emission", 1, "Chroma-HQ620");
         core.defineStateLabel("Emission", 2, "Chroma-HQ535");
         core.defineStateLabel("Emission", 3, "Chroma-HQ700");
         
         // excitation filter
         core.defineStateLabel("Excitation", 2, "Chroma-D360");
         core.defineStateLabel("Excitation", 3, "Chroma-HQ480");
         core.defineStateLabel("Excitation", 4, "Chroma-HQ570");
         core.defineStateLabel("Excitation", 5, "Chroma-HQ620");
         
         // excitation dichroic
         core.defineStateLabel("Dichroic", 0, "400DCLP");
         core.defineStateLabel("Dichroic", 1, "Q505LP");
         core.defineStateLabel("Dichroic", 2, "Q585LP");
         
         // objective
         core.defineStateLabel("Objective", 1, "Nikon 10X S Fluor");
         core.defineStateLabel("Objective", 3, "Nikon 20X Plan Fluor ELWD");
         core.defineStateLabel("Objective", 5, "Zeiss 4X Plan Apo");
         
         // define configurations
         //
         core.defineConfig("Channel", "FITC", "Emission", "State", "2");
         core.defineConfig("Channel", "FITC", "Excitation", "State", "3");
         core.defineConfig("Channel", "FITC", "Dichroic", "State", "1");
         
         core.defineConfig("Channel", "DAPI", "Emission", "State", "1");
         core.defineConfig("Channel", "DAPI", "Excitation", "State", "2");
         core.defineConfig("Channel", "DAPI", "Dichroic", "State", "0");
         
         core.defineConfig("Channel", "Rhodamine", "Emission", "State", "3");
         core.defineConfig("Channel", "Rhodamine", "Excitation", "State", "4");
         core.defineConfig("Channel", "Rhodamine", "Dichroic", "State", "2");
         
         // set initial imaging mode
         //
         core.setProperty("Camera", "Exposure", "55");
         core.setProperty("Objective", "Label", "Nikon 10X S Fluor");
         core.setConfig("Channel", "DAPI");
         
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
         
         // list configurations
         StrVector groups = core.getAvailableConfigGroups();
         for (int i=0; i<groups.size(); i++) {
            StrVector configs = core.getAvailableConfigs(groups.get(i));
            System.out.println("Group " + groups.get(i));
            for (int j=0; j<configs.size(); j++) {
               Configuration cdata = core.getConfigData(groups.get(i), configs.get(j));
               System.out.println("   Configuration " + configs.get(j));
               for (int k=0; k<cdata.size(); k++) {
                  PropertySetting s = cdata.getSetting(k);
                  System.out.println("      " + s.getDeviceLabel() + ", " +
                                     s.getPropertyName() + ", " + s.getPropertyValue());
               }
            }
      }
         
      } catch (Exception e) {
         System.out.println(e.getMessage());
         System.exit(1);
      }      
   }
}
