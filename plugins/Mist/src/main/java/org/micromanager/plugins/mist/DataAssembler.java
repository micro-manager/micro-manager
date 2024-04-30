package org.micromanager.plugins.mist;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import org.micromanager.Studio;
import org.micromanager.alerts.UpdatableAlert;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultImageJConverter;
import org.micromanager.display.DataViewer;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Methods to assemble data based on coordinates established
 * with Fiji Mist plugin.  Multiresolution NDTIff support is experimental.
 */
public class DataAssembler {

   private final Studio studio_;

   public DataAssembler(Studio studio) {
      studio_ = studio;
   }

   private List<MistGlobalData> parseMistEntries(String locationsFile) throws MistParseException {
      List<MistGlobalData> mistEntries = new ArrayList<>();
      File mistFile = new File(locationsFile);
      if (!mistFile.exists()) {
         throw new MistParseException("Mist global positions file not found: "
                 + mistFile.getAbsolutePath());
      }
      try {
         BufferedReader br = new BufferedReader(new FileReader(mistFile));
         String line;
         while ((line = br.readLine()) != null) {
            if (!line.startsWith("file: ")) {
               continue;
            }
            int fileNameEnd = line.indexOf(';');
            String fileName = line.substring(6, fileNameEnd);
            int siteNr = Integer.parseInt(fileName.substring(fileName.lastIndexOf('_') + 1,
                    fileName.length() - 8));
            int index = fileName.indexOf("MMStack_");
            int end = fileName.substring(index).indexOf("-") + index;
            String well = fileName.substring(index + 8, end);
            // x, y
            int posStart = line.indexOf("position: ") + 11;
            String lineEnd = line.substring(posStart);
            String xy = lineEnd.substring(0, lineEnd.indexOf(')'));
            String[] xySplit = xy.split(",");
            int positionX = Integer.parseInt(xySplit[0]);
            int positionY = Integer.parseInt(xySplit[1].trim());
            // row, column
            int gridStart = line.indexOf("grid: ") + 7;
            lineEnd = line.substring(gridStart);
            String rowCol = lineEnd.substring(0, lineEnd.indexOf(')'));
            String[] rowColSplit = rowCol.split(",");
            int row = Integer.parseInt(rowColSplit[0]);
            int col = Integer.parseInt(rowColSplit[1].trim());
            mistEntries.add(new MistGlobalData(
                    fileName, siteNr, well, positionX, positionY, row, col));
         }
      } catch (IOException e) {
         throw new MistParseException("Error reading Mist global positions file: " + e.getMessage());
      } catch (NumberFormatException e) {
         throw new MistParseException("Error parsing Mist global positions file: " + e.getMessage());
      }
      return mistEntries;
   }

   /**
    * Calculates the size of the new image that will be created by stitching the images
    *
    * @param mistEntries List of MistGlobalData entries
    * @param dataViewer  DataViewer containing the input data
    * @return Rectangle containing the new image dimensions
    */
   private Point newImageSize(List<MistGlobalData> mistEntries, DataViewer dataViewer) {
      // calculate new image dimensions
      DataProvider dp = dataViewer.getDataProvider();
      int imWidth = dp.getSummaryMetadata().getImageWidth();
      int imHeight = dp.getSummaryMetadata().getImageHeight();
      int maxX = 0;
      int maxY = 0;
      for (MistGlobalData entry : mistEntries) {
         if (entry.getPositionX() > maxX) {
            maxX = entry.getPositionX();
         }
         if (entry.getPositionY() > maxY) {
            maxY = entry.getPositionY();
         }
      }
      int newWidth = maxX + imWidth;
      int newHeight = maxY + imHeight;

      return new Point(newWidth, newHeight);
   }

   /**
    * Calculates the number of rows and columns in the new image that will be created by stitching.
    *
    * @param mistEntries List of MistGlobalData entries
    * @param dataViewer  DataViewer containing the input data
    * @return Rectangle containing the number of rows and columns
    */
   private Point newImageRowsCols(List<MistGlobalData> mistEntries, DataViewer dataViewer) {
      DataProvider dp = dataViewer.getDataProvider();
      int maxRow = 0;
      int maxCol = 0;
      for (MistGlobalData entry : mistEntries) {
         if (entry.getRowNr() > maxRow) {
            maxRow = entry.getRowNr();
         }
         if (entry.getColNr() > maxCol) {
            maxCol = entry.getColNr();
         }
      }

      return new Point(maxCol, maxRow);
   }

   /**
    * This function can take a long, long time to execute.  Make sure not to call it on the EDT.
    *
    * @param locationsFile Output file from the Mist stitching plugin.  "img-global-positions-0"
    * @param dataViewer    Micro-Manager dataViewer containing the input data/
    * @param newStore      Datastore to write the stitched images to.
    */
   public void assembleData(String locationsFile, final DataViewer dataViewer, Datastore newStore,
                            List<String> channelList, Map<String, Integer> mins,
                            Map<String, Integer> maxes, MutablePropertyMapView profileSettings,
                            JFrame parent) {
      UpdatableAlert updatableAlert = studio_.alerts().postUpdatableAlert(
              "Mist plugin", "started processing");
      List<MistGlobalData> mistEntries = null;
      try {
         mistEntries = parseMistEntries(locationsFile);
      } catch (MistParseException e) {
         studio_.logs().showError(e.getMessage());
         return;
      }
      DataProvider dp = dataViewer.getDataProvider();

      Point newImageSize = newImageSize(mistEntries, dataViewer);
      Point newImageRowsCols = newImageRowsCols(mistEntries, dataViewer);


      int newWidth = newImageSize.x;
      int newHeight = newImageSize.y;
      int maxRow = newImageRowsCols.x;

      final int newNrC = channelList.size();
      final int newNrT = (maxes.getOrDefault(Coords.T, 0) - mins.getOrDefault(Coords.T, 0)
              + 1);
      final int newNrZ = (maxes.getOrDefault(Coords.Z, 0) - mins.getOrDefault(Coords.Z, 0)
              + 1);
      final int newNrP = dp.getSummaryMetadata().getIntendedDimensions().getP()
              / mistEntries.size();
      int maxNumImages = newNrC * newNrT * newNrZ * newNrP;
      ProgressMonitor monitor = new ProgressMonitor(parent,
              "Stitching images...", null, 0, maxNumImages);
      DataViewer newDataViewer = null;
      long startTime = System.currentTimeMillis();
      try {
         // add Summary metadata to new store
         Coords.Builder cb = studio_.data().coordsBuilder().c(newNrC).t(newNrT).z(newNrZ).p(newNrP);
         newStore.setSummaryMetadata(dp.getSummaryMetadata().copyBuilder().imageHeight(newHeight)
                 .imageWidth(newWidth).intendedDimensions(cb.build())
                 .build());
         ((DefaultDatastore) newStore).increaseZoomLevel(maxRow);
         if (profileSettings.getBoolean("shouldDisplay", true)) {
            newDataViewer = studio_.displays().createDisplay(newStore);
         }
         Coords dims = dp.getSummaryMetadata().getIntendedDimensions();
         Coords.Builder intendedDimensionsB = dims.copyBuilder();
         for (String axis : new String[]{Coords.C, Coords.P, Coords.T, Coords.C}) {
            if (!dims.hasAxis(axis)) {
               intendedDimensionsB.index(axis, 1);
            }
         }
         Coords intendedDimensions = intendedDimensionsB.build();
         Coords.Builder imgCb = studio_.data().coordsBuilder();
         int nrImages = 0;
         for (int newP = 0; newP < newNrP; newP++) {
            int tmpC = -1;
            for (int c = 0; c < intendedDimensions.getC(); c++) {
               if (!channelList.contains(dp.getSummaryMetadata().getChannelNameList().get(c))) {
                  break;
               }
               tmpC++;
               for (int t = mins.getOrDefault(Coords.T, 0);
                    t <= maxes.getOrDefault(Coords.T, 0); t++) {
                  for (int z = mins.getOrDefault(Coords.Z, 0); z <= maxes.getOrDefault(Coords.Z, 0);
                       z++) {
                     if (monitor.isCanceled()) {
                        newStore.freeze();
                        if (newDataViewer == null) {
                           newStore.close();
                        }
                        return;
                     }
                     ImagePlus newImgPlus = IJ.createImage(
                             "Stitched image-" + newP, "16-bit black", newWidth, newHeight, 2);
                     boolean imgAdded = false;
                     for (int p = 0; p < mistEntries.size(); p++) {
                        if (monitor.isCanceled()) {
                           newStore.freeze();
                           if (newDataViewer == null) {
                              newStore.close();
                           }
                           return;
                        }
                        Image img = null;
                        Coords coords = imgCb.c(c).t(t).z(z)
                                .p(newP * mistEntries.size() + p)
                                .build();
                        if (dp.hasImage(coords)) {
                           img = dp.getImage(coords);
                        }
                        if (img != null) {
                           imgAdded = true;
                           String posName = img.getMetadata().getPositionName("");
                           int siteNr = Integer.parseInt(posName.substring(posName.lastIndexOf('_')
                                   + 1));
                           for (MistGlobalData entry : mistEntries) {
                              if (entry.getSiteNr() == siteNr) {
                                 int x = entry.getPositionX();
                                 int y = entry.getPositionY();
                                 ImageProcessor ip = DefaultImageJConverter.createProcessor(img,
                                         false);
                                 newImgPlus.getProcessor().insert(ip, x, y);
                              }
                           }
                        }
                     }
                     if (imgAdded) {
                        Image newImg = studio_.data().ij().createImage(newImgPlus.getProcessor(),
                                imgCb.c(tmpC).t(t - mins.getOrDefault(Coords.T, 0))
                                        .z(z - mins.getOrDefault(Coords.Z, 0))
                                        .p(newP).build(),
                                dp.getImage(imgCb.c(c).t(t).z(z).p(newP * mistEntries.size())
                                        .build()).getMetadata().copyBuilderWithNewUUID().build());
                        newStore.putImage(newImg);
                        nrImages++;
                        final int count = nrImages;
                        SwingUtilities.invokeLater(() -> monitor.setProgress(count));
                        int processTime = (int) ((System.currentTimeMillis() - startTime) / 1000);
                        updatableAlert.setText("Processed " + nrImages + " images of "
                                + maxNumImages + " in " + processTime + " seconds");
                     }
                  }
               }
            }
         }
      } catch (IOException e) {
         studio_.logs().showError("Error creating new data store: " + e.getMessage());
      } catch (NullPointerException npe) {
         studio_.logs().showError("Coding error in Mist plugin: " + npe.getMessage());
      } finally {
         try {
            newStore.freeze();
            if (newDataViewer == null) {
               newStore.close();
            }
         } catch (IOException ioe) {
            studio_.logs().logError(ioe, "IO Error while freezing DataProvider");
         }

         SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(() -> monitor.setProgress(maxNumImages));
         });
      }
   }

   public void assembleDataToNDTiff(String locationsFile,
                                    final DataViewer dataViewer,
                                    String fileLocation,
                                    List<String> channelList,
                                    Map<String, Integer> mins,
                                    Map<String, Integer> maxes,
                                    MutablePropertyMapView profileSettings,
                                    JFrame parent) {
      UpdatableAlert updatableAlert = studio_.alerts().postUpdatableAlert(
              "Mist plugin", "started processing");
      List<MistGlobalData> mistEntries = null;
      try {
         mistEntries = parseMistEntries(locationsFile);
      } catch (MistParseException e) {
         studio_.logs().showError(e.getMessage());
         return;
      }
      DataProvider dp = dataViewer.getDataProvider();

      Point newImageSize = newImageSize(mistEntries, dataViewer);
      Point newImageRowsCols = newImageRowsCols(mistEntries, dataViewer);

      int newWidth = newImageSize.x;
      int newHeight = newImageSize.y;
      int maxRow = newImageRowsCols.x;

      final int newNrC = channelList.size();
      final int newNrT = (maxes.getOrDefault(Coords.T, 0) - mins.getOrDefault(Coords.T, 0)
              + 1);
      final int newNrZ = (maxes.getOrDefault(Coords.Z, 0) - mins.getOrDefault(Coords.Z, 0)
              + 1);
      final int newNrP = dp.getSummaryMetadata().getIntendedDimensions().getP()
              / mistEntries.size();
      int maxNumImages = newNrC * newNrT * newNrZ * newNrP;
      ProgressMonitor monitor = new ProgressMonitor(parent,
              "Stitching images...", null, 0, maxNumImages);
      DataViewer newDataViewer = null;
      long startTime = System.currentTimeMillis();
      int nrImages = 0;
      for (int newP = 0; newP < newNrP; newP++) {
         Datastore newStore = null;
         try {
            // create new NDTiff store for this position/well
            Coords.Builder imgCb = studio_.data().coordsBuilder();
            String wellName = dp.getImage(imgCb.p(newP * mistEntries.size()).build())
                    .getMetadata().getPositionName("");
            wellName = wellName.substring(0, wellName.indexOf('-'));
            String newFileLocation = fileLocation + File.separator + wellName;
            newStore = studio_.data().createNDTIFFDatastore(newFileLocation);
            imgCb.c(newNrC).t(newNrT).z(newNrZ).p(1);
            newStore.setSummaryMetadata(dp.getSummaryMetadata().copyBuilder().imageHeight(newHeight)
                    .imageWidth(newWidth).intendedDimensions(imgCb.build())
                    .build());
            if (profileSettings.getBoolean("shouldDisplay", true)) {
               newDataViewer = studio_.displays().createDisplay(newStore);
            }
            Coords dims = dp.getSummaryMetadata().getIntendedDimensions();
            Coords.Builder intendedDimensionsB = dp.getSummaryMetadata().getIntendedDimensions()
                    .copyBuilder();
            for (String axis : new String[]{Coords.C, Coords.T, Coords.C}) {
               if (!dims.hasAxis(axis)) {
                  intendedDimensionsB.index(axis, 1);
               }
            }
            intendedDimensionsB.p(1);
            Coords intendedDimensions = intendedDimensionsB.build();
            int tmpC = -1;
            for (int c = 0; c < intendedDimensions.getC(); c++) {
               if (!channelList.contains(dp.getSummaryMetadata().getChannelNameList().get(c))) {
                  break;
               }
               tmpC++;
               for (int t = mins.getOrDefault(Coords.T, 0);
                    t <= maxes.getOrDefault(Coords.T, 0); t++) {
                  for (int z = mins.getOrDefault(Coords.Z, 0); z <= maxes.getOrDefault(Coords.Z, 0);
                       z++) {
                     if (monitor.isCanceled()) {
                        newStore.freeze();
                        if (newDataViewer == null) {
                           newStore.close();
                        }
                        return;
                     }
                     ImagePlus newImgPlus = IJ.createImage(
                             "Stitched image-" + newP, "16-bit black", newWidth, newHeight, 2);
                     boolean imgAdded = false;
                     for (int p = 0; p < mistEntries.size(); p++) {
                        if (monitor.isCanceled()) {
                           newStore.freeze();
                           if (newDataViewer == null) {
                              newStore.close();
                           }
                           return;
                        }
                        Image img = null;
                        Coords coords = imgCb.c(c).t(t).z(z)
                                .p(newP * mistEntries.size() + p)
                                .build();
                        if (dp.hasImage(coords)) {
                           img = dp.getImage(coords);
                        }
                        if (img != null) {
                           imgAdded = true;
                           String posName = img.getMetadata().getPositionName("");
                           int siteNr = Integer.parseInt(posName.substring(posName.lastIndexOf('_')
                                   + 1));
                           for (MistGlobalData entry : mistEntries) {
                              if (entry.getSiteNr() == siteNr) {
                                 int x = entry.getPositionX();
                                 int y = entry.getPositionY();
                                 ImageProcessor ip = DefaultImageJConverter.createProcessor(img,
                                         false);
                                 newImgPlus.getProcessor().insert(ip, x, y);
                              }
                           }
                        }
                     }
                     if (imgAdded) {
                        Image newImg = studio_.data().ij().createImage(newImgPlus.getProcessor(),
                                imgCb.c(tmpC).t(t - mins.getOrDefault(Coords.T, 0))
                                        .z(z - mins.getOrDefault(Coords.Z, 0))
                                        .p(newP).build(),
                                dp.getImage(imgCb.c(c).t(t).z(z).p(newP * mistEntries.size())
                                        .build()).getMetadata().copyBuilderWithNewUUID().build());
                        newStore.putImage(newImg);
                        nrImages++;
                        final int count = nrImages;
                        SwingUtilities.invokeLater(() -> monitor.setProgress(count));
                        int processTime = (int) ((System.currentTimeMillis() - startTime) / 1000);
                        updatableAlert.setText("Processed " + nrImages + " images of "
                                + maxNumImages + " in " + processTime + " seconds");
                     }
                  }
               }
            }
         } catch (IOException e) {
            studio_.logs().showError("Error creating new data store: " + e.getMessage());
         } catch (NullPointerException npe) {
            studio_.logs().showError("Coding error in Mist plugin: " + npe.getMessage());
         } finally {
            try {
               if (newStore != null) {
                  ((DefaultDatastore) newStore).increaseZoomLevel(4);
                  newStore.freeze();
                  if (newDataViewer == null) {
                     newStore.close();
                  }
               }
            } catch (IOException ioe) {
               studio_.logs().logError(ioe, "IO Error while freezing DataProvider");
            }
         }

         SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(() -> monitor.setProgress(maxNumImages));
         });
      }
   }

}
