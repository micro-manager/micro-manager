package org.micromanager.acquisition;

import org.micromanager.api.AcquisitionInterface;
import java.util.Enumeration;
import java.util.Hashtable;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MMScriptException;
import org.micromanager.api.AcquisitionEngine;

public class AcquisitionManager {
   Hashtable<String, AcquisitionInterface> acqs_;
   
   public AcquisitionManager() {
      acqs_ = new Hashtable<String, AcquisitionInterface>();
   }
   
   public void openAcquisition(String name, String rootDir) throws MMScriptException {
      if (acquisitionExists(name))
         throw new MMScriptException("The name is in use");
      else {
         MMAcquisition acq = new MMAcquisition(name, rootDir);
         acqs_.put(name, acq);
      }
   }

   public void openAcquisitionSnap(String name, String rootDir) throws MMScriptException {
      if (acquisitionExists(name))
    throw new MMScriptException("The name is in use");
      else
         ;
//          acqs_.put(name, new MMAcquisitionSnap(name, rootDir));
      }
   
   public void openAcquisition(String name, String rootDir, boolean show) throws MMScriptException {
      this.openAcquisition(name, rootDir, show, false);
   }

   public void openAcquisition(String name, String rootDir, boolean show, boolean diskCached) throws MMScriptException {
      this.openAcquisition(name, rootDir, show, diskCached, false);
   }

   public void openAcquisition(String name, String rootDir, boolean show,
           boolean diskCached, boolean existing) throws MMScriptException {
      if (acquisitionExists(name)) {
         throw new MMScriptException("The name is in use");
      } else {
         acqs_.put(name, new MMAcquisition(name, rootDir, show, diskCached, existing));
      }
   }
   
   public AcquisitionInterface openAcquisitionSnap(String name, String rootDir, MMStudioMainFrame gui_, boolean show) throws MMScriptException {
//      MMAcquisition acq = new MMAcquisitionSnap(name, rootDir, gui_, show);
 //     acqs_.put(name, acq);
 //     return acq;
      return null;
   }
     
   public void closeAcquisition(String name) throws MMScriptException {
      if (!acqs_.containsKey(name))
         throw new MMScriptException("The name does not exist");
      else {
         acqs_.get(name).close();
         acqs_.remove(name);
      }
   }
   
   public void closeImage5D(String name) throws MMScriptException {
      if (!acquisitionExists(name))
         throw new MMScriptException("The name does not exist");
      else
         acqs_.get(name).closeImage5D();
   }
   
   public Boolean acquisitionExists(String name) {
      if (acqs_.containsKey(name)) {
         if (acqs_.get(name).windowClosed()) {
            acqs_.get(name).close();
            acqs_.remove(name);
            return false;
         }
         return true;
      }
      return false;
   }
   
   public boolean hasActiveImage5D(String name) throws MMScriptException {
      if (acquisitionExists(name)) {
         return ! acqs_.get(name).windowClosed();
      }
      return false;
   }
      
   public AcquisitionInterface getAcquisition(String name) throws MMScriptException {
      if (acquisitionExists(name))
         return acqs_.get(name);
      else
         throw new MMScriptException("Undefined acquisition name: " + name);
   }

   public void closeAll() {
      for (Enumeration<AcquisitionInterface> e=acqs_.elements(); e.hasMoreElements(); )
         e.nextElement().close();
      
      acqs_.clear();
   }

   public String getUniqueAcquisitionName(String name) {
      char seperator = '_';
      while (acquisitionExists(name)) {
         int lastSeperator = name.lastIndexOf(seperator);
         if (lastSeperator == -1)
            name += seperator + "1";
         else {
            Integer i = Integer.parseInt(name.substring(lastSeperator + 1));
            i++;
            name = name.substring(0, lastSeperator) + seperator + i;
         }
      }
      return name;
   }

}
