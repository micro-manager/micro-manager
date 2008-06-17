///////////////////////////////////////////////////////////////////////////////
//FILE:           PlateAcquisitionData.java
//PROJECT:        Micro-Manager-S
//SUBSYSTEM:      high content screening
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June 3, 2008
//
//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008
//                
//LICENSE:        This file is distributed under the GPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//CVS:            $Id: MetadataDlg.java 1275 2008-06-03 21:31:24Z nenad $

package com.imaging100x.hcs;

import java.io.File;
import java.util.Hashtable;

import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.WellAcquisitionData;

public class PlateAcquisitionData {
   String name_;
   String basePath_;
   private Hashtable<String, WellAcquisitionData> wells_;
   
   public PlateAcquisitionData() {
      name_ = "noname";
      basePath_ = null;
      wells_ = new Hashtable<String, WellAcquisitionData>();
   }
   
   public void createNew(String name, String path, boolean autoName) throws MMAcqDataException {
      String testPath = name + "/" + name;
      String testName = name;
      if (autoName) {
         testName = generateRootName(name, path);
         testPath = path + "/" + testName;
      }
      
      File outDir = new File(testPath);
      
      // check if the path already exists
      if (!outDir.mkdirs())
         throw new MMAcqDataException("Unable to create PLATE level directory: " + basePath_);
      
      if (autoName)
         name_ = testName;
      else
         name_ = name;
      basePath_ = testPath;
   }
      
   public WellAcquisitionData createNewWell(String label) throws MMAcqDataException {
      if (wells_.containsKey(label))
         throw new MMAcqDataException("Well already exists: " + label);
      
      WellAcquisitionData wad = new WellAcquisitionData();
      wad.createNew(label, basePath_ + "/" + label, false);      
      wells_.put(wad.getLabel(), wad);
         
      return wad;
   }
   
   public WellAcquisitionData getWell(String name) {
      return wells_.get(name);
   }
   
   WellAcquisitionData[] getWells() {
      WellAcquisitionData wadArray[] = new WellAcquisitionData[wells_.size()];
      return wadArray;
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
   
   private class WellInfo {
      public int row_;
      public int col_;
      WellAcquisitionData wellAcq_;
      
      public WellInfo(int row, int col, WellAcquisitionData wad) {
         row_ = 0;
         col_ = 0;
         wellAcq_ = wad;
      }
   }
}
