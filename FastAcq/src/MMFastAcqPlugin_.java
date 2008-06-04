

import ij.IJ;
import ij.plugin.PlugIn;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import mmcorej.CMMCore;

import org.micromanager.fastacq.FastAcqMainFrame;

public class MMFastAcqPlugin_ implements PlugIn {

   static FastAcqMainFrame frame_;
   private CMMCore core_;

   public void run(String arg) {
      try {
         // create and display control panel frame
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

         core_ = MMStudioPlugin.getMMCoreInstance();
         if (core_ == null) {
            IJ.error("Micro-Manager Studio must be running!");
            return;
         }

         if (frame_ == null) {
            frame_ = new FastAcqMainFrame(core_);
            frame_.setVisible(true);
            frame_.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
         } else {
            JOptionPane.showMessageDialog(frame_, "Another instance of this plugin is already running.\n" +
            "Only one instance allowed.");
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}