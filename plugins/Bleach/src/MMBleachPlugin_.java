import ij.IJ;
import ij.plugin.PlugIn;
import mmcorej.CMMCore;

/*
 * Created on June 17, 2007
 * author: Nenad Amodaj
 */

/**
 * ImageJ plugin wrapper for uManager.
 */
public class MMBleachPlugin_ implements PlugIn {
   private static BleachControlDlg dlg_;
   private CMMCore core_;
      

   public void run(String arg) {
      
      core_ = MMStudioPlugin.getMMCoreInstance();
      if (core_ == null) {
         IJ.error("Micro-Manager Studio must be running!");
         return;
      }
      
      if (dlg_ != null && dlg_.isActive())
         dlg_.dispose();
      
      dlg_ = new BleachControlDlg(core_);
      dlg_.setVisible(true);
   }
}
