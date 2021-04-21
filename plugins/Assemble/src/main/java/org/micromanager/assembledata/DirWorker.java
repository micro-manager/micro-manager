package org.micromanager.assembledata;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.micromanager.Studio;
import org.micromanager.data.Datastore;

/** @author nico */
public class DirWorker {

  private static final String TARGET = "Combined";

  public static void run(
      Studio studio,
      AssembleDataForm form,
      String dirLocation,
      List<FileNameInfo> fni1,
      List<FileNameInfo> fni2,
      int xOffset,
      int yOffset,
      boolean test) {
    Runnable t =
        () -> {
          execute(studio, form, dirLocation, fni1, fni2, xOffset, yOffset, test);
        };
    Thread assembleThread = new Thread(t);
    assembleThread.start();
  }

  public static void execute(
      Studio studio,
      AssembleDataForm form,
      String dirLocation,
      List<FileNameInfo> fni1,
      List<FileNameInfo> fni2,
      int xOffset,
      int yOffset,
      boolean test) {

    Datastore targetStore = null;
    String currentWell = null;

    try {
      int targetPosition = 0;
      for (int i = 0; i < fni1.size(); i++) {
        if (!fni1.get(i).well().equals(currentWell)) {
          String target = TARGET + "-" + fni1.get(i).well();
          File fTarget = new File(dirLocation + File.separator + target);
          if (targetStore != null) {
            targetStore.close();
          }
          targetStore =
              studio.data().createMultipageTIFFDatastore(fTarget.getAbsolutePath(), false, false);
          currentWell = fni1.get(i).well();
          targetPosition = 0;
        }
        File f1 = new File(dirLocation + File.separator + fni1.get(i).fileName());
        File f2 = new File(dirLocation + File.separator + fni2.get(i).fileName());
        if (!f1.exists() || !f1.isDirectory()) {
          studio.logs().showError("Failed to find " + f1.getPath());
        }
        if (!f2.exists() || !f2.isDirectory()) {
          studio.logs().showError("Failed to find " + f2.getPath());
        }

        try (Datastore store1 = studio.data().loadData(f1.getPath(), false)) {
          try (Datastore store2 = studio.data().loadData(f2.getPath(), false)) {
            targetStore =
                AssembleDataAlgo.assemble(
                    studio,
                    form,
                    targetStore,
                    store1,
                    store2,
                    xOffset,
                    yOffset,
                    targetPosition,
                    test);
          }
        }
        targetPosition++;
      }
      if (targetStore != null) {
        targetStore.close();
      }
    } catch (IOException ioe) {
      studio.logs().showError(ioe, "Failed to open file ");
    }
    form.setStatus("Done...");
  }
}
