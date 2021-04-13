
package org.micromanager.assembledata;

import java.io.IOException;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.ColorPalettes;

/**
 *
 * @author nico
 */
public class ShowWorker {
   
    public static void run(Studio studio, AssembleDataForm form, DataProvider dp1, DataProvider dp2,
           int xOffset, int yOffset, boolean test) {
       Runnable t = () -> {
          execute ( studio,  form, dp1,  dp2, xOffset,  yOffset, test);
       };
       Thread assembleThread = new Thread(t);
       assembleThread.start();
       
    }

   public static void execute(Studio studio, AssembleDataForm form, DataProvider dp1, DataProvider dp2,
           int xOffset, int yOffset, boolean test) {

      Datastore targetStore = studio.data().createRAMDatastore();
      targetStore = AssembleDataAlgo.assemble(studio, form, targetStore, dp1, dp2, xOffset, yOffset, 0, test);

      if (targetStore != null) {
         DisplayWindow disp = studio.displays().createDisplay(targetStore);
         DisplaySettings dispSettings = disp.getDisplaySettings();
         DisplaySettings.Builder dpb = dispSettings.copyBuilder();
         for (int i=0; i < targetStore.getNextIndex(Coords.C); i++) {
            // this is scary stuff
            dpb.colorModeComposite().channel(i, 
                    dispSettings.getChannelSettings(0).copyBuilder().
                            color(ColorPalettes.
                                    getFromColorblindFriendlyPalette(i)).build());
         }

         DisplaySettings newDP = dpb.zoomRatio(0.33).build();
         disp.compareAndSetDisplaySettings(dispSettings, newDP);
         studio.displays().manage(targetStore);
         try {
            targetStore.freeze();
         } catch (IOException ioe) {
         }
         form.setStatus("Done...");
      }
   }

}
