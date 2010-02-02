import ij.IJ;
import ij.macro.Functions;
import ij.macro.MacroExtension;
import ij.macro.ExtensionDescriptor;
import ij.plugin.PlugIn;

//import org.micromanager.MMStudioPlugin;

import org.micromanager.api.Autofocus;

import mmcorej.CMMCore;


public class MMtest implements PlugIn, MacroExtension {

   private CMMCore core_;

   public void run(String arg) {
      /* This does not seem to work...
      if (!IJ.macroRunning()) {
         IJ.error("Cannot install extensions from outside a macro!");
            return;
       }
       */
       if (core_ == null) {
          core_ = MMStudioPlugin.getMMCoreInstance();
       }
       if (core_ == null) {
          IJ.error("Make sure that Micro-Manager is running and try again!");
          return;
       }

       Functions.registerExtensions(this);
   }
                                 
   private ExtensionDescriptor[] extensions = {
       ExtensionDescriptor.newDescriptor("setExposure", this, ARG_NUMBER),
       ExtensionDescriptor.newDescriptor("getExposure", this, ARG_OUTPUT+ARG_NUMBER),
   };

  public ExtensionDescriptor[] getExtensionFunctions() {
    return extensions;
  }

  public String handleExtension(String name, Object[] args) {
     System.out.println("In handleExtension with name: " + name + " and " + args.length + "args");
    if (name.equals("setExposure")) {
       try {
          core_.setExposure((Double)args[0]);
       } catch (Exception e) {
          IJ.error ("Error in setExposure: " + e.getMessage());
       }
    } else if (name.equals("getExposure")) {
       try {
          double exposure = core_.getExposure();
          System.out.println("Exposure: " + exposure);
          ((Double []) args[0])[0] = new Double(exposure);
       } catch (Exception e) {
          IJ.error ("Error in getExposure: " + e.getMessage());
       }
    } 

   return null;
  }
}
