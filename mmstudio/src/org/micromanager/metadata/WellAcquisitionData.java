///////////////////////////////////////////////////////////////////////////////
//FILE:           WellAcquisitionData.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      mmstudio and 3rd party applications
//DESCRIPTION:    Data representation of the single well or a grid acquisition
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June 2008
//
//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008
//                
//
//LICENSE:        This file is distributed under the BSD license.
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
//CVS:            $Id:  $

package org.micromanager.metadata;

import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;
import org.micromanager.utils.ReportingUtils;

/**
 * Represents data structure associated with well acquisition.
 * Well contains any number of imaging sites. Each imaging site
 * is represented by a separate AcquistionData object
 *
 */
public class WellAcquisitionData {
   String label_;
   String basePath_;
   private Hashtable<String, AcquisitionData> sites_;
   
   public WellAcquisitionData() {
      label_ = "undefined";
      basePath_ = null;
      sites_ = new Hashtable<String, AcquisitionData>();
   }
   
   /**
    * Check if the given path represents the well acquisition data
    * @param path - the path to check (must be directory)
    * @return - true if the tree corresponds to the well data
    */
   public static boolean isWellPath(String path) {
      File wellFile = new File(path);
      if (wellFile.isDirectory()) {
         File siteFiles[] = wellFile.listFiles();
         for (File sf : siteFiles)
            if (AcquisitionData.hasMetadata(sf.getAbsolutePath()))
               return true; // if at least one of the sites has metadata a well is declared valid
         return false;
      } else
         return false;
      
   }
   
   /**
    * Creates the new well acqusition data structure with link to the
    * file system. 
    * @param label - well label (name)
    * @param path - parent directory for the well data
    * @param autoName - if this parameter is true, the new name will be automatically
    * generated in case the given one already exists. Otherwise, duplicate name will
    * cause an exception
    * @throws MMAcqDataException
    */
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

   /**
    * Creates an empty well instance. Not associated with any disk directory.
    * Existing sites are preserved.
    * 
    * TODO: this method does not seem to be consistent and is not clear what is
    * the exact intent. Consider removing.
    * 
    * @param label - well label (name)
    * @param autoName - if this parameter is true, the new name will be automatically
    * generated in case the given one already exists. Otherwise, duplicate name will
    * cause an exception
    * @throws MMAcqDataException
    */
   public void createNew(String label, boolean autoName) throws MMAcqDataException {
      if (!autoName && sites_.containsKey(label))
         throw new MMAcqDataException("Label already exists: " + label);
      
      label_ = label;
      if (autoName)
         label_ = generateInMemoryName(label);
      
      basePath_ = null;
   }
   
   /**
    * Adds a new imaging site to the existing well, allowing for custom site names.
    * @param name - imaging site name
    * @param autoName -  if this parameter is true, the new name will be automatically
    * generated in case the given one already exists. Otherwise, duplicate name will
    * cause an exception
    * @return - returns a newly created AcquistionData object corresponding to new site
    * 
    * @throws MMAcqDataException
    */
   public AcquisitionData createNewImagingSite(String name, boolean autoName) throws MMAcqDataException {
      if (sites_.containsKey(name))
         throw new MMAcqDataException("Imaging site already exists: " + name);
      
      AcquisitionData ad = new AcquisitionData();
      ad.createNew(name, basePath_, autoName);
      
      sites_.put(ad.getName(), ad);
         
      return ad;
   }
   
   /**
    * Adds a new imaging site to the existing well. The site name will
    * be automatically generated
    * 
    * TODO: review this method. It does not seem to properly handle
    * duplicate site names.
    * 
    * @return - returns a newly created AcquistionData object corresponding to new site
    * @throws MMAcqDataException
    */
   public AcquisitionData createNewImagingSite() throws MMAcqDataException {
      AcquisitionData ad = new AcquisitionData();
      ad.createNew();      
      sites_.put(ad.getName(), ad);
         
      return ad;
   }
   
   /**
    * Returns AcqusitionData object for a given site name
    * @param name - imaging site name
    * @return - site acqusition data
    */
   public AcquisitionData getImagingSite(String name) {
      return sites_.get(name);
   }
   
   /**
    * Get well label.
    * @return - label
    */
   public String getLabel() {
      return label_;
   }
   
   /**
    * Returns an array of imaging sites
    * @return - array of AcquisitionData objects
    */
   public AcquisitionData[] getImagingSites() {
      AcquisitionData adArray[] = new AcquisitionData[sites_.size()];
      Enumeration<AcquisitionData> a = sites_.elements();
      int count = 0;
      while (a.hasMoreElements())
         adArray[count++] = a.nextElement();
      return adArray;
   }
   
   /**
    * Loads well data from a disk location
    * @param path - well data path
    * @throws MMAcqDataException
    */
   public void load(String path) throws MMAcqDataException {
      sites_.clear();
      label_ = "undefined";
      basePath_ = path;
      
      File baseDir = new File(basePath_);
      if (!baseDir.isDirectory())
         throw new MMAcqDataException("Base path for the well data must be a directory.");
      
      // list all files
      File[] files = baseDir.listFiles();
      for (File f : files) {
         if (AcquisitionData.hasMetadata(f.getAbsolutePath())) {
            AcquisitionData ad = new AcquisitionData();
            ad.load(f.getAbsolutePath());
            sites_.put(ad.getName(), ad);
         } else {
            //throw new MMAcqDataException("Not a valid well path: " + f.getAbsolutePath());
            ReportingUtils.logMessage("Skipped :" + f.getAbsolutePath());
         }
      }
      
      label_ = baseDir.getName();
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
