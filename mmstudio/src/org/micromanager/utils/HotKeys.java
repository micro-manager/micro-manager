package org.micromanager.utils;

import java.io.File;
import java.util.LinkedHashMap;

/**
 *
 * @author nico
 */
public class HotKeys {
   public static final LinkedHashMap<Integer, hotKeyAction> keys_ =
           new LinkedHashMap<Integer, hotKeyAction>();

   public class hotKeyAction {
      public static final int GUICOMMAND = 0;
      public static final int BEANSHELLSCRIPT = 1;
      public static final int SNAP = 0;
      public static final int TOGGLELIVE = 1;
      public static final int TOGGLESHUTTER = 2;
      public static final int ACQUIRE = 3;

      public int type_;  // either GUICOMMAND or BEANSHELLSCRIPT
      public int guiCommand_;
      public File beanShellScript_;
   }
   
}
