///////////////////////////////////////////////////////////////////////////////
//FILE:           Annotator.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:         Nenad Amodaj, nenad@100ximaging.com, May 2008

//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008

//LICENSE:        This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

//CVS:            $Id: AcquisitionData.java 1158 2008-05-08 02:16:42Z nenad $

package org.micromanager.utils;

import java.util.Iterator;

import mmcorej.Configuration;
import mmcorej.PropertySetting;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.MMAcqDataException;

public class Annotator {

   /**
    * Extract all the metadata form the COnfiguration and store it in the JSON object
    * @param cfg
    * @return
    */
   public static JSONObject generateJSONMetadata(Configuration cfg) {
      JSONObject md = new JSONObject();
      try {
         for (long i=0; i<cfg.size(); i++) {
            PropertySetting s = cfg.getSetting(i);
            md.put(s.getKey(), s.getPropertyValue());
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      return md;
   }

   /**
    * Merge metadata from the specified JSON object into the state metadata
    * @param acq
    * @param frame
    * @param channel
    * @param slice
    * @param md
    */
   public static void setStateMetadata(AcquisitionData acq, int frame, int channel, int slice, JSONObject md) {
      try {
         acq.setSystemState(frame, channel, slice, md);
      } catch (MMAcqDataException e) {
         ReportingUtils.logError(e);
      }
   }
   
   /**
    * Merge metadata from the specified JSON object into the image metadata
    * @param acq
    * @param frame
    * @param channel
    * @param slice
    * @param md
    */
   public static void setImageMetadata(AcquisitionData acq, int frame, int channel, int slice, JSONObject md) {
      try {
         for (Iterator<?> i = md.keys(); i.hasNext();) {
            String key = (String)i.next();
            acq.setImageValue(frame, channel, slice, key, md.getString(key));
         }
      } catch (MMAcqDataException e) {
         ReportingUtils.logError(e);
      }
      catch (JSONException e) {
          ReportingUtils.logError(e);
      }
   }

   
   /**
    * Merge metadata from the specified Configuration object into the image metadata
    * @param acq
    * @param frame
    * @param channel
    * @param slice
    * @param md
    */
   public static void setImageMetadata(AcquisitionData acq, int frame, int channel, int slice, Configuration cfg) {
      try {
         for (long i=0; i<cfg.size(); i++) {
            PropertySetting s = cfg.getSetting(i);
            acq.setImageValue(frame, channel, slice, s.getKey(), s.getPropertyValue());
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }
   
   /**
    * Merge metadata from the specified Configuration object into the state metadata
    * @param acq
    * @param frame
    * @param channel
    * @param slice
    * @param md
    */
   public static void setStateMetadata(AcquisitionData acq, int frame, int channel, int slice, Configuration cfg) {
      try {
         setStateMetadata(acq, frame, channel, slice, generateJSONMetadata(cfg));
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

}
