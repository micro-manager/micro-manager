package org.micromanager.acquisition.internal;


import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Set;

import org.json.JSONObject;

import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;

// TODO: this class consists solely of passthroughs now, and should be deleted
// in favor of a more direct approach.
public class AcquisitionManager {
   public void openAcquisition(String name, String rootDir) throws MMScriptException {
      ReportingUtils.logError("TODO: ignoring possibility that that data is already open");
      MMAcquisition acq = new MMAcquisition(name, rootDir);
   }
   
   public void openAcquisition(String name, String rootDir, boolean show) throws MMScriptException {
      this.openAcquisition(name, rootDir, show, false);
   }

   public void openAcquisition(String name, String rootDir, boolean show, boolean diskCached) throws MMScriptException {
      this.openAcquisition(name, rootDir, show, diskCached, false);
   }

   public void openAcquisition(String name, String rootDir, boolean show,
           boolean diskCached, boolean existing) throws MMScriptException {
      new MMAcquisition(name, rootDir, show, diskCached, existing);
   }
   
   public MMAcquisition createAcquisition(JSONObject summaryMetadata, boolean diskCached, AcquisitionEngine engine, boolean displayOff) {
      return new MMAcquisition("Acq", summaryMetadata, diskCached, engine, !displayOff);
   }
}
