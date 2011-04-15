package org.micromanager.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 *
 * @author nico
 */
public class HotKeys {
   Preferences root_;
   private static Preferences prefs_;
   private static final int STOP = -1;
   private static final String KEY = "Key";
   private static final String TYPE = "Type";
   private static final String GUICOMMAND = "GuiCommand";
   private static final String FILENAME = "FileName";

   // Note that this data structure is not synchroinized.  Since we are not
   // currently reading and writing at the same time, and access it only from
   // a single thread (I think), this should be safe.
   // Howvere, if this changes in the future, please synchronize this structure
   public static final LinkedHashMap<Integer, HotKeyAction> keys_ =
           new LinkedHashMap<Integer, HotKeyAction>();

   public  static boolean active_ = true;

   public HotKeys () {
      root_ = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root_.node(root_.absolutePath() + "/HotKeys");
   }

   public void loadSettings() {
      // restore previously listed hotkeys from prefs
      if (prefs_ == null)
         return;

      int j = 0;
      int key = STOP;
      int type = HotKeyAction.GUICOMMAND;
      int guiCommand = HotKeyAction.SNAP;
      File file;
      do {
         key = prefs_.getInt(KEY + j, STOP);
         if (key != STOP) {
            type = prefs_.getInt(TYPE + j, HotKeyAction.GUICOMMAND);
            if (type == HotKeyAction.GUICOMMAND) {
               guiCommand = prefs_.getInt(GUICOMMAND + j, HotKeyAction.SNAP);
               HotKeyAction action = new HotKeyAction(guiCommand);
               keys_.put(key, action);
            }  else {
               file = new File(prefs_.get(FILENAME + j, ""));
               HotKeyAction action = new HotKeyAction(file);
               keys_.put(key, action);
            }
         }
         j++;
      }
      while (key != STOP);
   }
   
   public void saveSettings() {
      if (prefs_ == null)
         return;

      Iterator it = keys_.entrySet().iterator();
      int i = 0;
      while (it.hasNext()) {
         Map.Entry pairs = (Map.Entry)it.next();
         prefs_.putInt(KEY + i, ((Integer) pairs.getKey()).intValue());
         HotKeyAction action = (HotKeyAction) pairs.getValue();
         prefs_.putInt(TYPE + i, action.type_);
         if (action.type_ == HotKeyAction.GUICOMMAND)
            prefs_.putInt(GUICOMMAND + i, action.guiCommand_);
         else
            prefs_.put(FILENAME + i, action.beanShellScript_.getAbsolutePath());
         i++;
      }

      // Add key as signal for the reader to stop reading
      prefs_.putInt(KEY + i, STOP);

   }
}
