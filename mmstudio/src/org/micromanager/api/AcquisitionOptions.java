package org.micromanager.api;

public class AcquisitionOptions {
   public boolean saveFiles;
   public boolean displayLastFrameOnly;
   public String acquisitionDirectory;
   public String comment;
   
   public AcquisitionOptions() {
      saveFiles = true;
      displayLastFrameOnly = true;
      acquisitionDirectory = new String("C:/MM_Acquisition");
   }
}
