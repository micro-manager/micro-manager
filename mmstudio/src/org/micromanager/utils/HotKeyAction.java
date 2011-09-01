package org.micromanager.utils;

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
      public static final int ACQUIRE = 3;
      public static final int MARK = 4;
      public static final String[] guiItems_ = {"Snap", "Toggle Live", "Toggle Shutter", "Acquire", "Mark Position"};
      public static final int NRGUICOMMANDS = guiItems_.length;

      public int type_;  // either GUICOMMAND or BEANSHELLSCRIPT
      public int guiCommand_;
      public java.io.File beanShellScript_;
      private org.micromanager.MMStudioMainFrame gui_ =
              org.micromanager.MMStudioMainFrame.getInstance();

      public HotKeyAction(int guiCommand) {
         type_ = GUICOMMAND;
         guiCommand_ = guiCommand;
      }

      public HotKeyAction(java.io.File beanshellScript) {
         type_ = BEANSHELLSCRIPT;
         beanShellScript_ = beanshellScript;
      }

      public boolean ExecuteAction() {
         if (type_ == GUICOMMAND) {
            switch (guiCommand_) {
               case SNAP:
                  gui_.snapSingleImage();
                  return true;
               case TOGGLELIVE:
                  if (gui_.getLiveMode())
                     gui_.enableLiveMode(false);
                  else
                     gui_.enableLiveMode(true);
                  return true;
               case TOGGLESHUTTER:
                  gui_.toggleShutter();
                  return true;
               case ACQUIRE:
                  gui_.snapAndAddToImage5D();
                  return true;
               case MARK:
                  gui_.markCurrentPosition();
                  return true;
            }
         } else {
            org.micromanager.ScriptPanel.runFile(beanShellScript_);
            return true;
         }
         return false;
      }


}
