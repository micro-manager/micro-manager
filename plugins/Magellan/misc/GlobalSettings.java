/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

import demo.DemoModeImageData;
import gui.GUI;
import gui.SettingsDialog;
import java.awt.Dialog;
import java.util.prefs.Preferences;
import javax.swing.filechooser.FileSystemView;
import org.micromanager.MMStudio;

/**
 *
 * @author Henry
 */
public class GlobalSettings {

   
   private static final String SAVING_DIR = "SAVING DIRECTORY";
   private static final String CHANNEL_OFFSET_PREFIX = "CHANNEL_OFFSET_";
   
   
   Preferences prefs_;
   private GUI gui_;
   private static boolean demoMode_ = false;
   private static boolean afBetweenAcqs_ = false;
   private static int[] chOffsets_ = new int[8];

   public GlobalSettings(Preferences prefs, GUI gui) {

      prefs_ = prefs;
      gui_ = gui;

      //Demo mode 
      try {
         String s = MMStudio.getInstance().getSysConfigFile();
         if (s.endsWith("NavDemo.cfg") || s.endsWith("NavDemo16Bit.cfg")) {
            demoMode_ = true;
         } else if (s.contains("BIDC")) {
            //secret features!           
         } else {
            //no secret features :(
//           autofocusBetweenSerialAcqsCheckBox_.setVisible(false);
         }
      } catch (Exception e) {
      }
      
      //load channel offsets
      for(int i =0; i < 8; i++) {
         chOffsets_[i] = prefs_.getInt(CHANNEL_OFFSET_PREFIX + i, 0);
      }      
   }
   
   public void storeSavingDirectory(String dir) {
      prefs_.put(SAVING_DIR, dir);
   }
   
   public String getStoredSavingDirectory() {
      return prefs_.get(SAVING_DIR, FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath());
   }
   
    public static boolean getAutofocusBetweenSerialAcqusitions() {
      return afBetweenAcqs_;
   }
   
   public static boolean getDemoMode() {
      return demoMode_;
   }
   
   public static int getDemoNumChannels() {
      return 6;
   }
   
   public static void setDemoMode(boolean demo) {
      demoMode_ = true;
      new DemoModeImageData();
   }

   public static int getChannelOffset(int i) {
      return chOffsets_[i];
   }
   
   public void channelOffsetChanged() {
      for (int i = 0; i < 8; i++) {
         Integer offset = gui_.getChannelOffset(i);
         if (offset != null) {
            prefs_.putInt(CHANNEL_OFFSET_PREFIX + i, offset);
            chOffsets_[i] = offset;
         }
      }
   }
}
