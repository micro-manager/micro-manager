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
import mmcorej.CMMCore;
import org.micromanager.MMStudio;

/**
 *
 * @author Henry
 */
public class GlobalSettings {

   
   private static final String SAVING_DIR = "SAVING DIRECTORY";
   private static final String CHANNEL_OFFSET_PREFIX = "CHANNEL_OFFSET_";
   
   private static GlobalSettings singleton_;
   Preferences prefs_;
   private GUI gui_;
   private static boolean demoMode_ = false;
   private static boolean afBetweenAcqs_ = false;
   private int[] chOffsets_ = new int[8];

   public GlobalSettings(Preferences prefs, GUI gui) {
       singleton_ = this;
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
      for(int i =0; i < 6; i++) {
         chOffsets_[i] = prefs_.getInt(CHANNEL_OFFSET_PREFIX + i, 0);
      }      
   }
   
   public static GlobalSettings getInstance() {
       return singleton_;
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

   public int getChannelOffset(int i) {
      return chOffsets_[i];
   }

    public void channelOffsetChanged() {
        int minVal = 200;
        for (int i = 0; i < 6; i++) {
            Integer offset = gui_.getChannelOffset(i);
            if (offset != null) {
                prefs_.putInt(CHANNEL_OFFSET_PREFIX + i, offset);
                chOffsets_[i] = offset;
                minVal = Math.min(minVal, offset);
//            System.out.println("Ch "+i+prefs_.getInt(CHANNEL_OFFSET_PREFIX + i, -50));
            }
        }
        //synchrnize with offsets in device adapter
        try {
            if (minVal != 200) {
                CMMCore core = MMStudio.getInstance().getCore();
                String channelOffsets = "";
                for (int i = 0; i < 6; i++) {                    
                    channelOffsets += chOffsets_[i] - minVal;
                }
                if (core.hasProperty("BitFlowCameraX2", "CenterOffset")) {
                    core.setProperty("BitFlowCameraX2", "CenterOffset", minVal);
                } else if (core.hasProperty("bitFlowCamera", "CenterOffset")) {
                    core.setProperty("BitFlowCamera", "CenterOffset", minVal);
                }
                if (core.hasProperty("BitFlowCameraX2", "ChannelOffsets")) {
                    core.setProperty("BitFlowCameraX2", "ChannelOffsets", channelOffsets);
                } else if (core.hasProperty("bitFlowCamera", "ChannelOffsets")) {
                    core.setProperty("BitFlowCamera", "ChannelOffsets", channelOffsets);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
