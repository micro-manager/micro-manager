package org.micromanager.internal.utils;

import org.micromanager.SnapLiveManager;
import org.micromanager.internal.MMStudio;

/**
 * Storage container for HotKeyActions
 *
 * @author nico
 */
public class HotKeyAction {
      public static final int GUICOMMAND = 0;
      public static final int BEANSHELLSCRIPT = 1;
      public static final int SNAP = 0;
      public static final int TOGGLELIVE = 1;
      public static final int TOGGLESHUTTER = 2;
      public static final int ADD_TO_ALBUM = 3;
      public static final int MARK = 4;
      public static final int AUTOSTRETCH = 5;
      public static final String[] guiItems_ = {"Snap", "Toggle Live", "Toggle Shutter", "->Album", "Mark Position", "Autostretch histograms"};
      public static final int NRGUICOMMANDS = guiItems_.length;

      public int type_;  // either GUICOMMAND or BEANSHELLSCRIPT
      public int guiCommand_;
      public java.io.File beanShellScript_;
      private MMStudio studio_ = MMStudio.getInstance();
      private SnapLiveManager snapLiveManager_;

      public HotKeyAction(int guiCommand) {
         type_ = GUICOMMAND;
         guiCommand_ = guiCommand;
         snapLiveManager_ = studio_.getSnapLiveManager();
      }

      public HotKeyAction(java.io.File beanshellScript) {
         type_ = BEANSHELLSCRIPT;
         beanShellScript_ = beanshellScript;
      }

      public boolean ExecuteAction() {
         if (type_ == GUICOMMAND) {
            switch (guiCommand_) {
               case SNAP:
                  studio_.live().snap(true);
                  return true;
               case TOGGLELIVE:
                  snapLiveManager_.setLiveMode(!snapLiveManager_.getIsLiveModeOn());
                  return true;
               case TOGGLESHUTTER:
                  try {
                     studio_.shutter().setShutter(!studio_.shutter().getShutter());
                  }
                  catch (Exception e) {
                     studio_.logs().logError(e, "Error setting shutter");
                  }
                  return true;
               case ADD_TO_ALBUM:
                  studio_.album().addImages(studio_.live().snap(false));
                  return true;
               case MARK:
                  studio_.markCurrentPosition();
                  return true;
               case AUTOSTRETCH:
                  studio_.displays().getCurrentWindow().autostretch();
            }
         } else {
            // Assume it's a script to run.
            studio_.scripter().runFile(beanShellScript_);
            return true;
         }
         return false;
      }


}
