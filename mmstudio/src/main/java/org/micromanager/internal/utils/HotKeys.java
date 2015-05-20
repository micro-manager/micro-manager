package org.micromanager.internal.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author nico
 */
public class HotKeys {
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

   public void loadSettings() {
      // restore previously listed hotkeys from profile

      int j = 0;
      int key;
      int type;
      int guiCommand;
      File file;
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      do {
         key = profile.getInt(HotKeys.class, KEY + j, STOP);
         if (key != STOP) {
            type = profile.getInt(HotKeys.class, TYPE + j,
                  HotKeyAction.GUICOMMAND);
            if (type == HotKeyAction.GUICOMMAND) {
               guiCommand = profile.getInt(HotKeys.class, GUICOMMAND + j,
                     HotKeyAction.SNAP);
               HotKeyAction action = new HotKeyAction(guiCommand);
               keys_.put(key, action);
            }  else {
               file = new File(profile.getString(HotKeys.class,
                        FILENAME + j, ""));
               HotKeyAction action = new HotKeyAction(file);
               keys_.put(key, action);
            }
         }
         j++;
      }
      while (key != STOP);
   }
   
   public void saveSettings() {
      Iterator it = keys_.entrySet().iterator();
      int i = 0;
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      while (it.hasNext()) {
         Map.Entry pairs = (Map.Entry) it.next();
         profile.setInt(HotKeys.class, KEY + i,
               ((Integer) pairs.getKey()).intValue());
         HotKeyAction action = (HotKeyAction) pairs.getValue();
         profile.setInt(HotKeys.class, TYPE + i, action.type_);
         if (action.type_ == HotKeyAction.GUICOMMAND)
            profile.setInt(HotKeys.class, GUICOMMAND + i, action.guiCommand_);
         else
            profile.setString(HotKeys.class, FILENAME + i,
                  action.beanShellScript_.getAbsolutePath());
         i++;
      }

      // Add key as signal for the reader to stop reading
      profile.setInt(HotKeys.class, KEY + i, STOP);
   }

   public static void load(File f) throws FileNotFoundException {
      if (f== null || !f.canRead())
         return;

      DataInputStream in = new DataInputStream
              (new BufferedInputStream(new FileInputStream(f)));
      keys_.clear();
      try {
         while (in.available() > 0) {
            int key = in.readInt();
            int type = in.readInt();
            String filePath = "";
            if (type == HotKeyAction.GUICOMMAND) {
               int guiCommand = in.readInt();
               HotKeyAction action = new HotKeyAction(guiCommand);
               keys_.put(key, action);
            }
            else {
               int strLength = in.readInt();
               for (int i=0; i < strLength; i ++)
                  filePath += in.readChar();
               HotKeyAction action = new HotKeyAction(new File(filePath));
               keys_.put(key, action);
            }
         }
         in.close();
      } catch (IOException ex) {
         ReportingUtils.showError("Error while reading in Shortcuts");
      }
   }
   /*
    * Save Hotkeys to a file
    * File needs to exist and be writeable
    */
   public static void save(File f) throws FileNotFoundException {
      if (f== null || !f.canWrite())
         return;

      DataOutputStream out = new DataOutputStream
              (new BufferedOutputStream(new FileOutputStream(f)));

      Iterator it = keys_.entrySet().iterator();
      int i = 0;
      while (it.hasNext()) {
         try {
            Map.Entry pairs = (Map.Entry) it.next();
            out.writeInt(((Integer) pairs.getKey()).intValue());
            HotKeyAction action = (HotKeyAction) pairs.getValue();
            out.writeInt(action.type_);
            if (action.type_ == HotKeyAction.GUICOMMAND) {
               out.writeInt(action.guiCommand_);
            } else {
               out.writeInt(action.beanShellScript_.getAbsolutePath().length());
               out.writeChars(action.beanShellScript_.getAbsolutePath());
            }
            i++;
         } catch (IOException ex) {
            ReportingUtils.showError("Error while saving Shortcuts");
         }
      }
      try {
         out.close();
      } catch (IOException ex) {
         ReportingUtils.showError("Error while closing Shortcuts file");
      }

   }
}
