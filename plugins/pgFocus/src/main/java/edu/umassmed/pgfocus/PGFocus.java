package edu.umassmed.pgfocus;

import mmcorej.CMMCore;
import org.jfree.ui.RefineryUtilities;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Biomedical Imaging Group.
 * Molecular Medicine
 * University of Massachusetts Medical School
 *
 * @author Karl Bellve
 */

@Plugin(type = MenuPlugin.class)
public class PGFocus implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "pgFocus";
   public static final String tooltipDescription =
         "Control the pgFocus open-source software and open hardware focus stabilization device";

   private CMMCore core_;
   private Studio gui_;
   private PGFocusFrame myFrame_;

   @Override
   public void setContext(Studio app) {
      gui_ = app;
      setCore(app.getCMMCore());
   }

   @Override
   public void onPluginSelected() {
      if (myFrame_ == null) {
         try {
            myFrame_ = new PGFocusFrame(gui_);
         } catch (Exception e) {
            e.printStackTrace();
            return;
         }
      }
      myFrame_.pack();
      RefineryUtilities.centerFrameOnScreen(myFrame_);
      myFrame_.setVisible(true);
   }

   @Override
   public String getName() {
      return menuName;
   }

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   public void dispose() {
      if (myFrame_ != null) {
         myFrame_.safePrefs();
      }
   }

   @Override
   public String getHelpText() {
      return tooltipDescription;
   }

   @Override
   public String getVersion() {
      return "0.10";
   }

   @Override
   public String getCopyright() {
      return "(C) 2014 Karl Bellve, Biomedical Imaging Group, Molecular Medicine, "
               + "Umass Medical School";
   }


   public CMMCore getCore() {
      return core_;
   }


   public void setCore(CMMCore core) {
      this.core_ = core;
   }
}
