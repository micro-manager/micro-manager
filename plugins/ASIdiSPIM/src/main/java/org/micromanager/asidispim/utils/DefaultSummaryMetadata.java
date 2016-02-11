
package org.micromanager.asidispim.utils;

import java.net.UnknownHostException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.Studio;
import org.micromanager.data.SummaryMetadata.SummaryMetadataBuilder;

/**
 *
 * @author Nico
 */
public class DefaultSummaryMetadata {
   /**
    * Utility function to generate summary metadata 
    * Replace once there is similar function in the mm api 
    * Fills in boiler plate code in the metadata builder.  
    * 
    * Currently:
    *    Channelgroup
    *    Computername
    *    MM version
    *    ProfileName
    *    StartDate
    * 
    * @param gui  Studio object
    * @param name Name of this dataset
    * @return 
    */
   public static SummaryMetadataBuilder defaultBuilder(Studio gui, String name){
      String hostname = "";
      try {
         hostname = java.net.InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException ex) {
         Logger.getLogger(DefaultSummaryMetadata.class.getName()).log(Level.SEVERE, null, ex);
      }
      SummaryMetadataBuilder smb = gui.data().getSummaryMetadataBuilder();
      return smb.
              channelGroup(gui.getCMMCore().getChannelGroup()).
              computerName(hostname).
              metadataVersion(null).
              microManagerVersion(gui.compat().getVersion()).
              name(name).
              profileName(gui.profile().getProfileName()).
              startDate((new Date().toString()));
   }
}
