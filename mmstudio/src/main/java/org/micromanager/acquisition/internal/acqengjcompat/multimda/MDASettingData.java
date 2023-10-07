package org.micromanager.acquisition.internal.acqengjcompat.multimda;

import java.io.File;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.acquisition.SequenceSettings;

/**
 * Data structure to remember the AcqSettings file, as well as
 * PositionList.
 */
public class MDASettingData {
   private final Studio studio_;
   private File acqSettingFile_;
   private SequenceSettings acqSettings_;
   private PositionList positionList_;
   private String presetGroup_;
   private String presetName_;

   public MDASettingData(Studio studio, File acqSettingFile, SequenceSettings acqSettings) {
      studio_ = studio;
      acqSettingFile_ = acqSettingFile;
      acqSettings_ = acqSettings;
   }

   public void setPositionList(PositionList pl) {
      positionList_ = pl;
   }

   public PositionList getPositionList() {
      return positionList_;
   }

   public SequenceSettings getSequenceSettings() {
      return acqSettings_;
   }

   public File getAcqSettingFile() {
      return acqSettingFile_;
   }

   public void setAcqSettings(File acqFile, SequenceSettings acqSettings) {
      acqSettingFile_ = acqFile;
      acqSettings_ = acqSettings;
   }

   public void setPresetGroup(String presetGroup) {
      presetGroup_ = presetGroup;
   }

   public String getPresetGroup() {
      return presetGroup_;
   }

   public void setPresetName(String presetName) {
      presetName_ = presetName;
   }

   public String getPresetName() {
      return presetName_;
   }

}
