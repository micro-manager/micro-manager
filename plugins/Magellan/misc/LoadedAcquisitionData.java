/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

import acq.MMImageCache;
import acq.MultiResMultipageTiffStorage;
import imagedisplay.DisplayPlus;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import misc.Log;

/**
 * 
 */
public class LoadedAcquisitionData {
   
   public LoadedAcquisitionData(String dir) {
      try {
         MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(dir);
         MMImageCache imageCache = new MMImageCache(storage);
         imageCache.setSummaryMetadata(storage.getSummaryMetadata());
         new DisplayPlus(imageCache, null, storage.getSummaryMetadata(), storage);
      } catch (IOException ex) {
         Log.log("Couldn't open acquisition", true);
      }
   }
   
   
}
