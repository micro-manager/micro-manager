/*
Copyright (c) 2010-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.data;

import static edu.ucsf.valelab.gaussianfit.DataCollectionForm.EXTENSION;
import static edu.ucsf.valelab.gaussianfit.DataCollectionForm.getInstance;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.LittleEndianDataInputStream;
import edu.ucsf.valelab.tsf.MMLocM;
import edu.ucsf.valelab.tsf.TaggedSpotsProtos;
import ij.process.ImageProcessor;
import java.awt.Cursor;
import java.awt.FileDialog;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Static functions for loading and saving files Tightly integrated with DataCollection Form (from
 * which it gets data and to which data are inserted).
 *
 * @author nico
 */
public class LoadAndSave {

   // Our Tagged Spot Format application ID
   public static int MMAPPID = 6;

   /**
    * Load Gaussian spot data from indicated file. This is for the file type developed by Bo Huang
    * and adopted by Nikon Updates the ImageJ status bar to show progress
    *
    * @param selectedFile - file that should be in binary format
    * @param caller       - Calling JFrame (used to set wait cursor)
    */
   public static void loadBin(File selectedFile, JFrame caller) {
      try {
         ij.IJ.showStatus("Loading data..");
         caller.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

         List<SpotData> spotList = new ArrayList<SpotData>();

         float pixelSize = (float) 160.0; // how do we get this from the file?

         LittleEndianDataInputStream fin = new LittleEndianDataInputStream(
               new BufferedInputStream(new FileInputStream(selectedFile)));
         byte[] m425 = {77, 52, 50, 53};
         for (int i = 0; i < 4; i++) {
            if (fin.readByte() != m425[i]) {
               throw (new IOException("Not a .bin file"));
            }
         }

         boolean nStorm = true;
         byte[] ns = new byte[4];
         byte[] guid = {71, 85, 73, 68};
         for (int i = 0; i < 4; i++) {
            ns[i] = fin.readByte();
            if (ns[i] != guid[i]) {
               nStorm = false;
            }
         }

         if (nStorm) { // read away 57 bytes
            fin.skipBytes(53);
         } else {
            // there may be a more elegant way to go back 4 bytes
            fin.close();
            fin = new LittleEndianDataInputStream(
                  new BufferedInputStream(new FileInputStream(selectedFile)));
            fin.skipBytes(4);
         }

         int nrFrames = fin.readInt();
         int molType = fin.readInt();
         int nr = 0;
         boolean hasZ = false;
         double maxZ = Double.NEGATIVE_INFINITY;
         double minZ = Double.POSITIVE_INFINITY;

         for (int i = 0; i <= nrFrames; i++) {
            int nrMolecules = fin.readInt();
            for (int j = 0; j < nrMolecules; j++) {
               // total size of data on disk is 17 bytes
               float x = fin.readFloat();
               float y = fin.readFloat();
               float xc = fin.readFloat();
               float yc = fin.readFloat();
               float h = fin.readFloat();
               float a = fin.readFloat(); // integrated dens. based on fitting
               float w = fin.readFloat();
               float phi = fin.readFloat();
               float ax = fin.readFloat();
               float b = fin.readFloat();
               float intensity = fin.readFloat();
               int c = fin.readInt();
               int union = fin.readInt();
               int frame = fin.readInt();
               int union2 = fin.readInt();
               int link = fin.readInt();
               float z = fin.readFloat();
               float zc = fin.readFloat();

               if (zc != 0.0) {
                  hasZ = true;
               }
               if (zc > maxZ) {
                  maxZ = zc;
               }
               if (zc < minZ) {
                  minZ = zc;
               }

               SpotData gsd = new SpotData(null, 0, 0, i,
                     0, nr, (int) xc, (int) yc);
               gsd.setData(intensity, b, pixelSize * xc, pixelSize * yc, 0.0, w, ax, phi, c);
               gsd.setZCenter(zc);
               gsd.setOriginalPosition(x, y, z);
               spotList.add(gsd);
               nr++;
            }
         }

         String name = selectedFile.getName();

         RowData.Builder builder = new RowData.Builder();
         builder.setName(name).setTitle(name).setDisplayWindow(null).
               setColColorRef("").setWidth(256).setHeight(256).
               setPixelSizeNm(pixelSize).setZStackStepSizeNm(0.0f).
               setShape(3).setHalfSize(2).setNrFrames(1).setNrSlices(1).
               setNrPositions(1).setMaxNrSpots(nr).setSpotList(spotList).
               setIsTrack(false).setCoordinate(DataCollectionForm.Coordinates.NM).
               setHasZ(hasZ).setMinZ(minZ).setMaxZ(maxZ);
         DataCollectionForm.getInstance().addSpotData(builder);


      } catch (FileNotFoundException ex) {
         JOptionPane.showMessageDialog(getInstance(), "File not found");
      } catch (IOException ex) {
         JOptionPane.showMessageDialog(getInstance(), "Error while reading file");
      } catch (OutOfMemoryError ome) {
         JOptionPane.showMessageDialog(getInstance(), "Out Of Memory");
      } finally {
         caller.setCursor(Cursor.getDefaultCursor());
         ij.IJ.showStatus("");
         ij.IJ.showProgress(1.0);
      }
   }

   /**
    * Loads a text file saved from this application back into memory
    *
    * @param selectedFile
    * @param caller       - JFrame calling code, used to set Waitcursor
    */
   public static void loadText(File selectedFile, JFrame caller) {

      ij.IJ.showStatus("Loading data..");

      caller.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      try {
         BufferedReader fr = new BufferedReader(new FileReader(selectedFile));
         loadTextFromBufferedReader(fr);
      } catch (FileNotFoundException ex) {
         JOptionPane.showMessageDialog(getInstance(), "File not found");
      } finally {
         caller.setCursor(Cursor.getDefaultCursor());
         ij.IJ.showStatus("");
         ij.IJ.showProgress(1.0);
      }
   }

   /**
    * @param fr - input data as a stream
    */
   public static void loadTextFromBufferedReader(BufferedReader fr) {

      try {

         String info = fr.readLine();
         String[] infos = info.split("\t");
         HashMap<String, String> infoMap = new HashMap<String, String>();
         for (String info1 : infos) {
            String[] keyValue = info1.split(": ");
            if (keyValue.length >= 2) {
               infoMap.put(keyValue[0], keyValue[1]);
            }
         }
         String test = infoMap.get("has_Z");
         boolean hasZ = false;
         if (test != null && test.equals("true")) {
            hasZ = true;
         }
         int appId = Integer.parseInt(infoMap.get("application_id"));

         String head = fr.readLine();
         String[] headers = head.split("\t");
         String spot;
         List<SpotData> spotList = new ArrayList<SpotData>();
         double maxZ = Double.NEGATIVE_INFINITY;
         double minZ = Double.POSITIVE_INFINITY;

         while ((spot = fr.readLine()) != null) {
            String[] spotTags = spot.split("\t");
            if (spotTags.length != headers.length) {
               ReportingUtils.logError("Failed to import spot " + spotTags[0]);
               continue;
            }
            HashMap<String, String> k = new HashMap<String, String>();
            for (int i = 0; i < headers.length; i++) {
               k.put(headers[i], spotTags[i]);
            }

            SpotData gsd = new SpotData(null,
                  Integer.parseInt(k.get(headers[3])),
                  Integer.parseInt(k.get(headers[2])),
                  Integer.parseInt(k.get(headers[1])),
                  Integer.parseInt(k.get(headers[4])),
                  Integer.parseInt(k.get(headers[0])),
                  Integer.parseInt(k.get(headers[5])),
                  Integer.parseInt(k.get(headers[6]))
            );
            gsd.setData(Double.parseDouble(k.get(headers[9])),
                  Double.parseDouble(k.get(headers[10])),
                  Double.parseDouble(k.get(headers[7])),
                  Double.parseDouble(k.get(headers[8])), 0.0,
                  Double.parseDouble(k.get(headers[11])),
                  Double.parseDouble(k.get(headers[12])),
                  Double.parseDouble(k.get(headers[13])),
                  Double.parseDouble(k.get(headers[14]))
            );
            if (appId == MMAPPID) {
               gsd.addKeyValue(SpotData.Keys.APERTUREINTENSITY,
                     Double.parseDouble(k.get("intensity_aperture")));
               gsd.addKeyValue(SpotData.Keys.APERTUREBACKGROUND,
                     Double.parseDouble(k.get("background_aperture")));
               gsd.addKeyValue(SpotData.Keys.INTENSITYRATIO,
                     Double.parseDouble(k.get("intensity_ratio")));
               gsd.addKeyValue(SpotData.Keys.MSIGMA,
                     Double.parseDouble(k.get("m_sigma")));
               if (k.containsKey("integral_aperture_sigma")) {
                  gsd.addKeyValue(SpotData.Keys.INTEGRALAPERTURESIGMA,
                        Double.parseDouble(k.get("integral_aperture_sigma")));
               }
            }
            if (hasZ) {
               double zc = Double.parseDouble(k.get("z"));
               gsd.setZCenter(zc);
               if (zc > maxZ) {
                  maxZ = zc;
               }
               if (zc < minZ) {
                  minZ = zc;
               }
            }
            spotList.add(gsd);

         }

         // Add transformed data to data overview window
         float zStepSize = (float) 0.0;
         if (infoMap.containsKey("z_step_size")) {
            zStepSize = (float) (Double.parseDouble(infoMap.get("z_step_size")));
         }

         int halfBoxSize = Integer.parseInt(infoMap.get("box_size")) / 2;
         RowData.Builder builder = new RowData.Builder();
         builder.setName(infoMap.get("name")).setTitle(infoMap.get("name")).
               setWidth(Integer.parseInt(infoMap.get("nr_pixels_x"))).
               setHeight(Integer.parseInt(infoMap.get("nr_pixels_y"))).
               setPixelSizeNm(Math.round(Double.parseDouble(infoMap.get("pixel_size")))).
               setZStackStepSizeNm(zStepSize).
               setShape(Integer.parseInt(infoMap.get("fit_mode"))).
               setHalfSize(halfBoxSize).
               setNrChannels(Integer.parseInt(infoMap.get("nr_channels"))).
               setNrFrames(Integer.parseInt(infoMap.get("nr_frames"))).
               setNrSlices(Integer.parseInt(infoMap.get("nr_slices"))).
               setNrPositions(Integer.parseInt(infoMap.get("nr_pos"))).
               setMaxNrSpots(spotList.size()).
               setSpotList(spotList).
               setCoordinate(DataCollectionForm.Coordinates.NM).
               setHasZ(hasZ).setMinZ(minZ).setMaxZ(maxZ);
         DataCollectionForm.getInstance().addSpotData(builder);


      } catch (NumberFormatException ex) {
         JOptionPane.showMessageDialog(getInstance(), "File format did not meet expectations");

      } catch (IOException ex) {
         JOptionPane.showMessageDialog(getInstance(), "Error while reading file");
      }

   }

   /**
    * Load a .tsf file
    *
    * @param selectedFile - File to be loaded
    * @param caller       - Calling GUI element, used to set WaitCursor
    */
   public static void loadTSF(File selectedFile, JFrame caller) {
      TaggedSpotsProtos.SpotList psl;
      long spotsMissedWithErrors = 0;

      try {

         ij.IJ.showStatus("Loading data..");

         caller.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

         FileInputStream fi = new FileInputStream(selectedFile);
         DataInputStream di = new DataInputStream(fi);

         // the new file format has an initial 0, then the offset (in long)
         // to the position of spotList
         int magic = di.readInt();
         if (magic != 0) {
            // reset and mark do not seem to work on my computer
            fi.close();
            fi = new FileInputStream(selectedFile);
            psl = TaggedSpotsProtos.SpotList.parseDelimitedFrom(fi);
         } else {
            // TODO: evaluate after creating code writing this format
            long offset = di.readLong();
            fi.skip(offset);
            psl = TaggedSpotsProtos.SpotList.parseDelimitedFrom(fi);
            fi.close();
            fi = new FileInputStream(selectedFile);
            fi.skip(12); // size of int + size of long
         }
         ExtensionRegistry registry = ExtensionRegistry.newInstance();
         int appId = psl.getApplicationId();
         if (appId == MMAPPID) {
            registry.add(MMLocM.intensityAperture);
            registry.add(MMLocM.intensityBackground);
            registry.add(MMLocM.intensityRatio);
            registry.add(MMLocM.mSigma);
            registry.add(MMLocM.integralApertureSigma);
         }
         String name = psl.getName();
         String title = psl.getName();
         int width = psl.getNrPixelsX();
         int height = psl.getNrPixelsY();
         float pixelSize = psl.getPixelSize();
         int shape = 1;
         if (psl.getFitMode() == TaggedSpotsProtos.FitMode.TWOAXIS) {
            shape = 2;
         } else if (psl.getFitMode() == TaggedSpotsProtos.FitMode.TWOAXISANDTHETA) {
            shape = 3;
         }
         int halfSize = psl.getBoxSize() / 2;
         int nrChannels = psl.getNrChannels();
         int nrFrames = psl.getNrFrames();
         int nrSlices = psl.getNrSlices();
         int nrPositions = psl.getNrPos();
         boolean isTrack = psl.getIsTrack();
         long expectedSpots = psl.getNrSpots();
         long esf = expectedSpots / 100;
         long maxNrSpots = 0;
         boolean hasZ = false;
         double maxZ = Double.NEGATIVE_INFINITY;
         double minZ = Double.POSITIVE_INFINITY;

         ArrayList<SpotData> spotList = new ArrayList<SpotData>();
         TaggedSpotsProtos.Spot pSpot;
         while (fi.available() > 0 && (expectedSpots == 0 || maxNrSpots < expectedSpots)) {

            try {
               pSpot = TaggedSpotsProtos.Spot.parseDelimitedFrom(fi, registry);

               SpotData gSpot = new SpotData((ImageProcessor) null, pSpot.getChannel(),
                     pSpot.getSlice(), pSpot.getFrame(), pSpot.getPos(),
                     pSpot.getMolecule(), pSpot.getXPosition(), pSpot.getYPosition());
               gSpot.setData(pSpot.getIntensity(), pSpot.getBackground(), pSpot.getX(),
                     pSpot.getY(), 0.0, pSpot.getWidth(), pSpot.getA(), pSpot.getTheta(),
                     pSpot.getXPrecision());
               if (appId == MMAPPID) {
                  gSpot.addKeyValue(SpotData.Keys.APERTUREINTENSITY,
                        pSpot.getExtension(MMLocM.intensityAperture));
                  gSpot.addKeyValue(SpotData.Keys.APERTUREBACKGROUND,
                        pSpot.getExtension(MMLocM.intensityBackground));
                  gSpot.addKeyValue(SpotData.Keys.INTENSITYRATIO,
                        pSpot.getExtension(MMLocM.intensityRatio));
                  gSpot.addKeyValue(SpotData.Keys.MSIGMA,
                        pSpot.getExtension(MMLocM.mSigma));
                  if (pSpot.hasExtension(MMLocM.integralApertureSigma)) {
                     gSpot.addKeyValue(SpotData.Keys.INTEGRALAPERTURESIGMA,
                           pSpot.getExtension(MMLocM.integralApertureSigma));
                  }
               }
               if (pSpot.hasZ()) {
                  double zc = pSpot.getZ();
                  gSpot.setZCenter(zc);
                  hasZ = true;
                  if (zc > maxZ) {
                     maxZ = zc;
                  }
                  if (zc < minZ) {
                     minZ = zc;
                  }
               }
               maxNrSpots++;
               if ((esf > 0) && ((maxNrSpots % esf) == 0)) {
                  ij.IJ.showProgress((double) maxNrSpots / (double) expectedSpots);
               }

               spotList.add(gSpot);
            } catch (InvalidProtocolBufferException ipbe) {
               spotsMissedWithErrors++;
               ReportingUtils.logError("ProtocolBuffer Exception: " + ipbe.getMessage());
            }
         }

         RowData.Builder builder = new RowData.Builder();
         builder.setName(name).setTitle(title).setWidth(width).setHeight(height).
               setPixelSizeNm(pixelSize).setZStackStepSizeNm(0.0f).setShape(shape).
               setHalfSize(halfSize).setNrChannels(nrChannels).
               setNrFrames(nrFrames).setNrSlices(nrSlices).
               setNrPositions(nrPositions).setMaxNrSpots(maxNrSpots).
               setSpotList(spotList).setIsTrack(isTrack).
               setCoordinate(DataCollectionForm.Coordinates.NM).
               setHasZ(hasZ).setMinZ(minZ).setMaxZ(maxZ);
         DataCollectionForm.getInstance().addSpotData(builder);

      } catch (FileNotFoundException ex) {
         JOptionPane.showMessageDialog(getInstance(), "File not found");
      } catch (IOException ex) {
         JOptionPane.showMessageDialog(getInstance(), "Error while reading file");
      } finally {
         caller.setCursor(Cursor.getDefaultCursor());
         ij.IJ.showStatus("");
         ij.IJ.showProgress(1.0);
         if (spotsMissedWithErrors > 0) {
            ReportingUtils.showError("Failed to read " + spotsMissedWithErrors + " spot(s)");
         }
      }
   }

   /**
    * Save data set in TSF (Tagged Spot File) format
    *
    * @param rowData          row with spot data to be saved
    * @param bypassFileDialog
    * @param dir
    * @param caller
    * @return
    */

   public static String saveData(final RowData[] rowData,
         boolean bypassFileDialog,
         String dir,
         final JFrame caller) {
      String[] parts = rowData[0].getName().split(File.separator);
      String name = parts[parts.length - 1];
      String fn = name + EXTENSION;
      if (!bypassFileDialog) {
         FileDialog fd = new FileDialog(caller, "Save Spot Data", FileDialog.SAVE);
         fd.setFile(fn);
         fd.setVisible(true);
         String selectedItem = fd.getFile();
         if (selectedItem == null) {
            return "";
         }
         fn = fd.getFile();
         if (!fn.contains(".")) {
            fn += EXTENSION;
         }
         dir = fd.getDirectory();
      }
      final File selectedFile = new File(dir + File.separator + fn);
      final String fdir = dir;

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {
            for (int rowNr = 0; rowNr < rowData.length; rowNr++) {
               TaggedSpotsProtos.SpotList.Builder tspBuilder = TaggedSpotsProtos.SpotList
                     .newBuilder();
               tspBuilder.setApplicationId(MMAPPID).
                     setName(rowData[rowNr].getName()).
                     setFilepath(rowData[rowNr].title_).
                     setNrPixelsX(rowData[rowNr].width_).
                     setNrPixelsY(rowData[rowNr].height_).
                     setNrSpots(rowData[rowNr].spotList_.size()).
                     setPixelSize(rowData[rowNr].pixelSizeNm_).
                     setBoxSize(rowData[rowNr].halfSize_ * 2).
                     setNrChannels(rowData[rowNr].nrChannels_).
                     setNrSlices(rowData[rowNr].nrSlices_).
                     setIsTrack(rowData[rowNr].isTrack_).
                     setNrPos(rowData[rowNr].nrPositions_).
                     setNrFrames(rowData[rowNr].nrFrames_).
                     setLocationUnits(TaggedSpotsProtos.LocationUnits.NM).
                     setIntensityUnits(TaggedSpotsProtos.IntensityUnits.PHOTONS).
                     setNrSpots(rowData[rowNr].maxNrSpots_);
               switch (rowData[rowNr].shape_) {
                  case (1):
                     tspBuilder.setFitMode(TaggedSpotsProtos.FitMode.ONEAXIS);
                     break;
                  case (2):
                     tspBuilder.setFitMode(TaggedSpotsProtos.FitMode.TWOAXIS);
                     break;
                  case (3):
                     tspBuilder.setFitMode(TaggedSpotsProtos.FitMode.TWOAXISANDTHETA);
                     break;
               }

               TaggedSpotsProtos.SpotList spotList = tspBuilder.build();
               try {
                  caller.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                  FileOutputStream fo;
                  if (rowNr == 0) {
                     fo = new FileOutputStream(selectedFile);
                  } else {
                     String[] nameParts = rowData[rowNr].getName().split(File.separator);
                     String tmpName = nameParts[nameParts.length - 1];
                     fo = new FileOutputStream(
                           new File(fdir + File.separator + tmpName + EXTENSION));
                  }
                  // write space for magic nr and offset to spotList
                  for (int i = 0; i < 12; i++) {
                     fo.write(0);
                  }

                  int counter = 0;
                  for (SpotData gd : rowData[rowNr].spotList_) {

                     if ((counter % 1000) == 0) {
                        ij.IJ.showStatus("Saving spotData...");
                        ij.IJ.showProgress(counter, rowData[rowNr].spotList_.size());
                     }

                     if (gd != null) {
                        TaggedSpotsProtos.Spot.Builder spotBuilder = TaggedSpotsProtos.Spot
                              .newBuilder();
                        // TODO: precede all these calls with check for presence of member
                        // or be OK with default values?
                        spotBuilder.setMolecule(counter).
                              setFrame(gd.getFrame()).
                              setChannel(gd.getChannel()).
                              setPos(gd.getPosition()).
                              setSlice(gd.getSlice()).
                              setX((float) gd.getXCenter()).
                              setY((float) gd.getYCenter()).
                              setIntensity((float) gd.getIntensity()).
                              setBackground((float) gd.getBackground()).
                              setXPosition(gd.getX()).
                              setYPosition(gd.getY()).
                              setWidth((float) gd.getWidth()).
                              setA((float) gd.getA()).
                              setTheta((float) gd.getTheta()).
                              setXPrecision((float) gd.getSigma()).
                              setExtension(MMLocM.intensityAperture,
                                    gd.getValue(SpotData.Keys.APERTUREINTENSITY, -1.0).floatValue())
                              .
                                    setExtension(MMLocM.intensityBackground,
                                          gd.getValue(SpotData.Keys.APERTUREBACKGROUND, -1.0)
                                                .floatValue()).
                              setExtension(MMLocM.intensityRatio,
                                    gd.getValue(SpotData.Keys.INTENSITYRATIO, -1.0).floatValue()).
                              setExtension(MMLocM.mSigma,
                                    gd.getValue(SpotData.Keys.MSIGMA, -1.0).floatValue()).
                              setExtension(MMLocM.integralApertureSigma,
                                    gd.getValue(SpotData.Keys.INTEGRALAPERTURESIGMA, -1.0)
                                          .floatValue()
                              );

                        if (rowData[rowNr].hasZ_) {
                           spotBuilder.setZ((float) gd.getZCenter());
                        }

                        TaggedSpotsProtos.Spot spot = spotBuilder.build();

                        // write message size and message
                        spot.writeDelimitedTo(fo);
                        counter++;
                     }
                  }

                  FileChannel fc = fo.getChannel();
                  long offset = fc.position();
                  spotList.writeDelimitedTo(fo);

                  // now go back to write offset to the stream
                  fc.position(4);
                  DataOutputStream dos = new DataOutputStream(fo);
                  dos.writeLong(offset - 12);

                  fo.close();

                  ij.IJ.showProgress(1);
                  ij.IJ.showStatus("Finished saving spotData...");
               } catch (IOException ex) {
                  Logger.getLogger(DataCollectionForm.class.getName()).log(Level.SEVERE, null, ex);
               } finally {
                  caller.setCursor(Cursor.getDefaultCursor());
               }
            }
         }
      };

      (new Thread(doWorkRunnable)).start();

      return dir;
   }

   /**
    * Save data set as a text file
    *
    * @param rows   - row with spot data to be saved
    * @param dir
    * @param caller - JFrame of calling code to provide visual feedback
    * @return
    */

   public static String saveDataAsText(final RowData[] rows, String dir, final JFrame caller) {
      final FileDialog fd = new FileDialog(caller, "Save Spot Data", FileDialog.SAVE);
      String[] parts = rows[0].getName().split(File.separator);
      String name = parts[parts.length - 1];
      fd.setFile(name + ".txt");
      FilenameFilter fnf = new FilenameFilter() {

         @Override
         public boolean accept(File file, String string) {
            return string.endsWith(".txt");
         }
      };
      fd.setFilenameFilter(fnf);
      fd.setVisible(true);
      String selectedItem = fd.getFile();
      if (selectedItem != null) {
         String fn = fd.getFile();
         if (!fn.contains(".")) {
            fn += ".txt";
         }
         final File selectedFile = new File(fd.getDirectory() + File.separator + fn);

         Runnable doWorkRunnable = new Runnable() {

            @Override
            public void run() {
               try {
                  for (int rowNr = 0; rowNr < rows.length; rowNr++) {
                     String tab = "\t";
                     caller.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                     FileWriter fw;
                     if (rowNr == 0) {
                        fw = new FileWriter(selectedFile);
                     } else {
                        String[] nameParts = rows[rowNr].getName().split(File.separator);
                        String tmpName = nameParts[nameParts.length - 1];
                        fw = new FileWriter(
                              new File(fd.getDirectory() + File.separator + tmpName + ".txt"));
                     }
                     fw.write(""
                           + "application_id: " + MMAPPID + tab
                           + "name: " + rows[rowNr].getName() + tab
                           + "filepath: " + rows[rowNr].title_ + tab
                           + "nr_pixels_x: " + rows[rowNr].width_ + tab
                           + "nr_pixels_y: " + rows[rowNr].height_ + tab
                           + "pixel_size: " + rows[rowNr].pixelSizeNm_ + tab
                           + "nr_spots: " + rows[rowNr].maxNrSpots_ + tab
                           + "box_size: " + rows[rowNr].halfSize_ * 2 + tab
                           + "nr_channels: " + rows[rowNr].nrChannels_ + tab
                           + "nr_frames: " + rows[rowNr].nrFrames_ + tab
                           + "nr_slices: " + rows[rowNr].nrSlices_ + tab
                           + "nr_pos: " + rows[rowNr].nrPositions_ + tab
                           + "location_units: " + TaggedSpotsProtos.LocationUnits.NM + tab
                           + "intensity_units: " + TaggedSpotsProtos.IntensityUnits.PHOTONS + tab
                           + "fit_mode: " + rows[rowNr].shape_ + tab
                           + "is_track: " + rows[rowNr].isTrack_ + tab
                           + "has_Z: " + rows[rowNr].hasZ_ + "\n");
                     fw.write("molecule\tframe\tslice\tchannel\tpos\tx_position\t"
                           + "y_position\tx\ty\tintensity\t"
                           + "background\twidth\ta\ttheta\t"
                           + "sigma\tintensity_aperture\tbackground_aperture\t"
                           + "intensity_ratio\tm_sigma\tintegral_aperture_sigma");
                     if (rows[rowNr].hasZ_) {
                        fw.write("\tz");
                     }
                     fw.write("\n");

                     int counter = 1;
                     for (SpotData gd : rows[rowNr].spotList_) {

                        if ((counter % 1000) == 0) {
                           ij.IJ.showStatus("Saving spotData...");
                           ij.IJ.showProgress(counter, rows[rowNr].spotList_.size());
                        }

                        if (gd != null) {
                           fw.write("" + counter + tab
                                 + gd.getFrame() + tab
                                 + gd.getSlice() + tab
                                 + gd.getChannel() + tab
                                 + gd.getPosition() + tab
                                 + gd.getX() + tab
                                 + gd.getY() + tab
                                 + String.format("%.2f", gd.getXCenter()) + tab
                                 + String.format("%.2f", gd.getYCenter()) + tab
                                 + String.format("%.2f", gd.getIntensity()) + tab
                                 + String.format("%.2f", gd.getBackground()) + tab
                                 + String.format("%.2f", gd.getWidth()) + tab
                                 + String.format("%.3f", gd.getA()) + tab
                                 + String.format("%.3f", gd.getTheta()) + tab
                                 + String.format("%.3f", gd.getSigma()) + tab);
                           String remainder = "";
                           if (gd.hasKey(SpotData.Keys.APERTUREINTENSITY)) {
                              remainder += String.format("%.2f",
                                    gd.getValue(SpotData.Keys.APERTUREINTENSITY).floatValue());
                           } else {
                              remainder += "-1.000";
                           }
                           remainder += tab;

                           if (gd.hasKey(SpotData.Keys.APERTUREBACKGROUND)) {
                              remainder += String.format("%.2f",
                                    gd.getValue(SpotData.Keys.APERTUREBACKGROUND).floatValue());
                           } else {
                              remainder += "-1.000";
                           }

                           remainder += tab;
                           if (gd.hasKey(SpotData.Keys.INTENSITYRATIO)) {
                              remainder += String.format("%.3f",
                                    gd.getValue(SpotData.Keys.INTENSITYRATIO).floatValue());
                           } else {
                              remainder += "-1.000";
                           }

                           remainder += tab;
                           if (gd.hasKey(SpotData.Keys.MSIGMA)) {
                              remainder += String
                                    .format("%.3f", gd.getValue(SpotData.Keys.MSIGMA).floatValue());
                           } else {
                              remainder += "-1.000";
                           }

                           remainder += tab;
                           if (gd.hasKey(SpotData.Keys.INTEGRALAPERTURESIGMA)) {
                              remainder += String.format("%.3f",
                                    gd.getValue(SpotData.Keys.INTEGRALAPERTURESIGMA).floatValue());
                           } else {
                              remainder += "-1.000";
                           }

                           fw.write(remainder);
                           if (rows[rowNr].hasZ_) {
                              fw.write(tab + String.format("%.2f", gd.getZCenter()));
                           }
                           fw.write("\n");

                           counter++;
                        }
                     }

                     fw.close();

                     ij.IJ.showProgress(1);
                     ij.IJ.showStatus("Finished saving spotData to text file...");
                  }
               } catch (IOException ex) {
                  JOptionPane
                        .showMessageDialog(getInstance(), "Error while saving data in text format");
               } finally {
                  caller.setCursor(Cursor.getDefaultCursor());
               }
            }
         };

         (new Thread(doWorkRunnable)).start();

      }
      return dir;
   }

}