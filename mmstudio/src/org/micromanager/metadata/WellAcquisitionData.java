package org.micromanager.metadata;

import java.io.File;
import java.util.Hashtable;

public class WellAcquisitionData {
   String label_;
   String basePath_;
   private Hashtable<String, AcquisitionData> sites_;
   
   public WellAcquisitionData() {
      label_ = "undefined";
      basePath_ = null;
      sites_ = new Hashtable<String, AcquisitionData>();
   }
   
   public void createNew(String label, String path, boolean autoName) throws MMAcqDataException {
      String testPath = path + "/" + label;
      String testName = label;
      if (autoName) {
         testName = generateRootName(label, path);
         testPath = path + "/" + testName;
      }
      
      File outDir = new File(testPath);
      
      // check if the path already exists
      if (!outDir.mkdirs())
         throw new MMAcqDataException("Unable to create WELL level directory: " + basePath_);
      
      if (autoName)
         label_ = testName;
      else
         label_ = label;
      basePath_ = testPath;
   }
   
   public void createNew(String label, boolean autoName) throws MMAcqDataException {
      if (!autoName && sites_.containsKey(label))
         throw new MMAcqDataException("Label already exists: " + label);
      
      label_ = label;
      if (autoName)
         label_ = generateInMemoryName(label);
      
      basePath_ = null;
   }
   
   public AcquisitionData createNewImagingSite(String name, boolean autoName) throws MMAcqDataException {
      if (sites_.containsKey(name))
         throw new MMAcqDataException("Imaging site already exists: " + name);
      
      AcquisitionData ad = new AcquisitionData();
      ad.createNew(name, basePath_, autoName);
      
      sites_.put(ad.getName(), ad);
         
      return ad;
   }
   
   public AcquisitionData createNewImagingSite() throws MMAcqDataException {
      AcquisitionData ad = new AcquisitionData();
      ad.createNew(AcquisitionData.METADATA_SITE_PREFIX, basePath_, true);      
      sites_.put(ad.getName(), ad);
         
      return ad;
   }
   
   public AcquisitionData getImagingSite(String name) {
      return sites_.get(name);
   }
   
   public String getLabel() {
      return label_;
   }
   
   AcquisitionData[] getImagingSItes() {
      AcquisitionData adArray[] = new AcquisitionData[sites_.size()];
      return adArray;
   }
   
   ////////////////////////////////////////////////////////////////////////////
   // Private methods
   ////////////////////////////////////////////////////////////////////////////
   
   static private String generateRootName(String name, String baseDir) {
      // create new acquisition directory
      int suffixCounter = 0;
      String testPath;
      String testName;
      File testDir;
      do {
         testName = name + "_" + suffixCounter;
         testPath = new String(baseDir + "/" + testName);
         suffixCounter++;
         testDir = new File(testPath);
      } while (testDir.exists());
      return testName;
   }

   private String generateInMemoryName(String name) {
      int suffixCounter = 0;
      String testName;
      do {
         testName = name + "_" + suffixCounter;
      } while (sites_.containsKey(testName));
      return testName;
   }
   
}
