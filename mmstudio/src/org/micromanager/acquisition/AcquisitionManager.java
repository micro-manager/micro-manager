package org.micromanager.acquisition;

import org.micromanager.api.AcquisitionInterface;
import java.util.Enumeration;
import java.util.Hashtable;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MMScriptException;
import org.micromanager.acquisition.MMVirtualAcquisition;
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
         MMVirtualAcquisition virtAcq = new MMVirtualAcquisition(name, rootDir, false, true);
         acqs_.put(name, virtAcq);
         virtAcq.initialize();
             virtAcq.show(0);
      }
   }

   public void openAcquisitionSnap(String name, String rootDir) throws MMScriptException {
	      if (acquisitionExists(name))
	         throw new MMScriptException("The name is in use");
	      else
	         acqs_.put(name, new MMAcquisitionSnap(name, rootDir));
	   }
   
   public void openAcquisition(String name, String rootDir, boolean show) throws MMScriptException {
      this.openAcquisition(name, rootDir, show, false);
   }

   public void openAcquisition(String name, String rootDir, boolean show, boolean virtual) throws MMScriptException {
      if (acquisitionExists(name)) {
         throw new MMScriptException("The name is in use");
      } else {
        acqs_.put(name, new MMVirtualAcquisition(name, rootDir, true, virtual));
      }
   }
   
   public AcquisitionInterface openAcquisitionSnap(String name, String rootDir, MMStudioMainFrame gui_, boolean show) throws MMScriptException {
      MMAcquisition acq = new MMAcquisitionSnap(name, rootDir, gui_, show);
      acqs_.put(name, acq);
      return acq;
   }
  
   public void setAcquisitionEngine(String name, AcquisitionEngine eng) {
      acqs_.get(name).setEngine(eng);
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
	   return (acqs_.containsKey(name));
   }
   
   public boolean hasActiveImage5D(String name) throws MMScriptException {
	   if (acquisitionExists(name)) {
		   return ! getAcquisition(name).windowClosed();
	   } else
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

   public void setAcquisitionCache(String acqName, MMImageCache imageCache) {
      acqs_.get(acqName).setCache(imageCache);
   }

}
