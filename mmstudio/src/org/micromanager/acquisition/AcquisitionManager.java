package org.micromanager.acquisition;

import java.util.Enumeration;
import java.util.Hashtable;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MMScriptException;

public class AcquisitionManager {
   Hashtable<String, MMAcquisition> acqs_;
   
   public AcquisitionManager() {
      acqs_ = new Hashtable<String, MMAcquisition>();
   }
   
   public void openAcquisition(String name, String rootDir) throws MMScriptException {
      if (acquisitionExists(name))
         throw new MMScriptException("The name is in use");
      else
         acqs_.put(name, new MMAcquisition(name, rootDir));
   }

   public void openAcquisitionSnap(String name, String rootDir) throws MMScriptException {
	      if (acquisitionExists(name))
	         throw new MMScriptException("The name is in use");
	      else
	         acqs_.put(name, new MMAcquisitionSnap(name, rootDir));
	   }
   
   public void openAcquisition(String name, String rootDir, boolean show) throws MMScriptException {
      if (acquisitionExists(name))
         throw new MMScriptException("The name is in use");
      else
         acqs_.put(name, new MMAcquisition(name, rootDir, show));
   }
   
   public MMAcquisition openAcquisitionSnap(String name, String rootDir, MMStudioMainFrame gui_, boolean show) throws MMScriptException {
      MMAcquisition acq = new MMAcquisitionSnap(name, rootDir, gui_, show);
      acqs_.put(name, acq);
      return acq;
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
		   return ! getAcquisition(name).imgWin_.isClosed();
	   } else
		   return false;
			   
   }
      
   public MMAcquisition getAcquisition(String name) throws MMScriptException {
      if (acquisitionExists(name))
         return acqs_.get(name);
      else
         throw new MMScriptException("Undefined acquisition name: " + name);
   }

   public void closeAll() {
      for (Enumeration<MMAcquisition> e=acqs_.elements(); e.hasMoreElements(); )
         e.nextElement().close();
      
      acqs_.clear();
   }

}
