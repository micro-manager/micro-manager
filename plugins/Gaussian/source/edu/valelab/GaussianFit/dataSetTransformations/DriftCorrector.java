
package edu.valelab.GaussianFit.dataSetTransformations;

import edu.valelab.GaussianFit.DataCollectionForm;
import edu.valelab.GaussianFit.data.SpotData;
import edu.valelab.GaussianFit.DataCollectionForm.Coordinates;
import edu.valelab.GaussianFit.JitterDetector;
import static edu.valelab.GaussianFit.DataCollectionForm.getInstance;
import edu.valelab.GaussianFit.data.RowData;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.JOptionPane;

/**
 *
 * @author nico
 */
public class DriftCorrector {

   // storage of stage movement data
   class StageMovementData {

      Point2D.Double pos_;
      Point frameRange_;

      StageMovementData(Point2D.Double pos, Point frameRange) {
         pos_ = pos;
         frameRange_ = frameRange;
      }
   }
   
   /**
    * Creates a new data set that is corrected for motion blur
    * Correction is performed by projecting a number of images onto a 
    * 2D scattergram and using cross-correlation between them to find
    * the displacement
    * 
    * @param rowData 
    */
   public static void unJitter(final RowData rowData) {

      // TODO: instead of a fixed number of frames, go for a certain number of spots
      // Number of frames could be limited as well
      final int framesToCombine = 200;
      
      if (rowData.spotList_.size() <= 1) {
         return;
      }
      
           
      ij.IJ.showStatus("Executing jitter correction");
      
      Runnable doWorkRunnable = new Runnable() {
         
         @Override
         public void run() {
            
            int mag = (int) (rowData.pixelSizeNm_ / 40.0);
            while (mag % 2 != 0)
               mag += 1;
                        
            int width = mag * rowData.width_;
            int height = mag * rowData.height_;                        
            
            int size = width * height;
            
            
             // TODO: add 0 padding to deal with aberrant image sizes
            if ( (width != height) || ( (width & (width - 1)) != 0) ) {
               JOptionPane.showMessageDialog(getInstance(), 
                 "Magnified image is not a square with a size that is a power of 2");
               ij.IJ.showStatus(" ");
               return;
            }

            // TODO: what if we should go through nrSlices instead of nrFrames?
            boolean useSlices = false;
            int nrOfTests = rowData.nrFrames_ / framesToCombine;
            if (nrOfTests == 0) {
               useSlices = true;
               nrOfTests = rowData.nrSlices_ / framesToCombine;
               if (rowData.nrSlices_ % framesToCombine > 0) {
                  nrOfTests++;
               }
            } else {
               if (rowData.nrFrames_ % framesToCombine > 0) {
                  nrOfTests++;
               }
            }

            // storage of stage movement data
            class StageMovementData {
               
               Point2D.Double pos_;
               Point frameRange_;
               
               StageMovementData(Point2D.Double pos, Point frameRange) {
                  pos_ = pos;
                  frameRange_ = frameRange;
               }
            }
            ArrayList<StageMovementData> stagePos = new ArrayList<StageMovementData>();
            
            try {
               // make imageprocessors for all the images that we will generate
               ImageProcessor[] ip = new ImageProcessor[nrOfTests];
               byte[][] pixels = new byte[nrOfTests][width * height];
               
               for (int i = 0; i < nrOfTests; i++) {
                  ip[i] = new ByteProcessor(width, height);
                  ip[i].setPixels(pixels[i]);
               }
               
               double factor = (double) mag / rowData.pixelSizeNm_;

               // make 2D scattergrams of all pixelData
               for (SpotData spot : rowData.spotList_) {
                  int j;
                  if (useSlices) {
                     j = (spot.getSlice() - 1) / framesToCombine;
                  } else {
                     j = (spot.getFrame() - 1) / framesToCombine;
                  }
                  int x = (int) (factor * spot.getXCenter());
                  int y = (int) (factor * spot.getYCenter());
                  int index = (y * width) + x;
                  if (index < size && index > 0) {
                     if (pixels[j][index] != -1) {
                        pixels[j][index] += 1;
                     }
                  }
                  
               }
               
               JitterDetector jd = new JitterDetector(ip[0]);
               
               Point2D.Double fp = new Point2D.Double(0.0, 0.0);
               Point2D.Double com = new Point2D.Double(0.0, 0.0);
               
               jd.getJitter(ip[0], fp);
               
               for (int i = 1; i < ip.length; i++) {
                  ij.IJ.showStatus("Executing jitter correction..." + i);
                  ij.IJ.showProgress(i, ip.length);
                  int spotCount = 0;
                  for (int j=0; j < ip[i].getPixelCount(); j++) 
                     spotCount += ip[i].get(j);
                  
                  jd.getJitter(ip[i], com);
                  double x = (fp.x - com.x) / factor;
                  double y = (fp.y - com.y) / factor;
                  if (rowData.timePoints_ != null) {
                     rowData.timePoints_.get(i);
                  }
                  stagePos.add(new StageMovementData(new Point2D.Double(x, y),
                          new Point(i * framesToCombine, ((i + 1) * framesToCombine - 1))));
                  System.out.println("i: " + i + " nSpots: " + spotCount + " X: " + x + " Y: " + y);
               }
               
            } catch (OutOfMemoryError ex) {
               // not enough memory to allocate all images in one go
               // we need to cycle through all gaussian spots cycle by cycle

               double factor = (double) mag / rowData.pixelSizeNm_;
               
               ImageProcessor ipRef = new ByteProcessor(width, height);
               byte[] pixelsRef = new byte[width * height];
               ipRef.setPixels(pixelsRef);


               // take the first image as reference
               for (SpotData spot : rowData.spotList_) {
                  int j;
                  if (useSlices) {
                     j = (spot.getSlice() - 1) / framesToCombine;
                  } else {
                     j = (spot.getFrame() - 1) / framesToCombine;
                  }
                  if (j == 0) {
                     int x = (int) (factor * spot.getXCenter());
                     int y = (int) (factor * spot.getYCenter());
                     int index = (y * width) + x;
                     if (index < size && index > 0) {
                        if (pixelsRef[index] != -1) {
                           pixelsRef[index] += 1;
                        }
                     }
                  }
               }
               
               JitterDetector jd = new JitterDetector(ipRef);
               
               Point2D.Double fp = new Point2D.Double(0.0, 0.0);
               jd.getJitter(ipRef, fp);
               
               Point2D.Double com = new Point2D.Double(0.0, 0.0);
               ImageProcessor ipTest = new ByteProcessor(width, height);
               byte[] pixelsTest = new byte[width * height];
               ipTest.setPixels(pixelsTest);
               
               for (int i = 1; i < nrOfTests; i++) {
                  ij.IJ.showStatus("Executing jitter correction..." + i);
                  ij.IJ.showProgress(i, nrOfTests);
                  for (int p = 0; p < size; p++) {
                     ipTest.set(p, 0);
                  }
                  
                  for (SpotData spot : rowData.spotList_) {
                     int j;
                     if (useSlices) {
                        j = (spot.getSlice() - 1) / framesToCombine;
                     } else {
                        j = (spot.getFrame() - 1) / framesToCombine;
                     }
                     if (j == i) {
                        int x = (int) (factor * spot.getXCenter());
                        int y = (int) (factor * spot.getYCenter());
                        int index = (y * width) + x;
                        if (index < size && index > 0) {
                           if (pixelsTest[index] != -1) {
                              pixelsTest[index] += 1;
                           }
                        }
                     }
                  }
                  
                  jd.getJitter(ipTest, com);
                  double x = (fp.x - com.x) / factor;
                  double y = (fp.y - com.y) / factor;
                  double timePoint = i;
                  if (rowData.timePoints_ != null) {
                     rowData.timePoints_.get(i);
                  }
                  stagePos.add(new StageMovementData(new Point2D.Double(x, y),
                          new Point(i * framesToCombine, ((i + 1) * framesToCombine - 1))));
                  System.out.println("X: " + x + " Y: " + y);
               }
               
            }
            
            try {
               // Assemble stage movement data into a track
               List<SpotData> stageMovementData = new ArrayList<SpotData>();
               SpotData sm = new SpotData(null, 1, 1, 1, 1, 1, 1, 1);
               sm.setData(0, 0, 0, 0, 0.0, 0, 0, 0, 0);
               stageMovementData.add(sm);
               
               // calculate moving average for stageposition
               ArrayList<StageMovementData> stagePosMA = new ArrayList<StageMovementData>();
               int windowSize = 5;
               for (int i = 0; i < stagePos.size() - windowSize; i++) {
                  Point2D.Double avg = new Point2D.Double(0.0, 0.0);
                  for (int j = 0; j < windowSize; j++) {
                     avg.x += stagePos.get(i + j).pos_.x;
                     avg.y += stagePos.get(i + j).pos_.y;
                  }
                  avg.x /= windowSize;
                  avg.y /= windowSize;
                  
                  stagePosMA.add(new StageMovementData(avg, stagePos.get(i).frameRange_));
               }
               
               
               for (int i = 0; i < stagePosMA.size(); i++) {
                  StageMovementData smd = stagePosMA.get(i);
                  SpotData s =
                          new SpotData(null, 1, 1, i + 2, 1, 1, 1, 1);
                  s.setData(0, 0, smd.pos_.x, smd.pos_.y, 0.0, 0, 0, 0, 0);                  
                  stageMovementData.add(s);
               }

               // Add stage movement data to overview window
               // First try to copy the time points
               ArrayList<Double> timePoints = null;
               if (rowData.timePoints_ != null) {
                  timePoints = new ArrayList<Double>();
                  int tp = framesToCombine;
                  while (tp < rowData.timePoints_.size()) {
                     timePoints.add(rowData.timePoints_.get(tp));
                     tp += framesToCombine;
                  }
               }
               
               RowData newRow = new RowData(rowData.name_ + "-Jitter", 
                       rowData.title_, "", rowData.width_,rowData.height_, 
                       rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
                       rowData.shape_, rowData.halfSize_, rowData.nrChannels_, 
                       stageMovementData.size(),1, 1, stageMovementData.size(), 
                       stageMovementData, timePoints, true, Coordinates.NM, 
                       false, 0.0, 0.0);
               
               DataCollectionForm.getInstance().getRowData().add(newRow);
               
               DataCollectionForm.getInstance().fireRowAdded();
                                           
               ij.IJ.showStatus("Assembling jitter corrected dataset...");
               ij.IJ.showProgress(1);
               
               List<SpotData> correctedData = new ArrayList<SpotData>();
               Iterator it = rowData.spotList_.iterator();
               
               int testNr = 0;
               StageMovementData smd = stagePosMA.get(0);
               int counter = 0;
               while (it.hasNext()) {
                  counter++;
                  SpotData gs = (SpotData) it.next();
                  int test;
                  if (useSlices) {
                     test = gs.getSlice();
                  } else {
                     test = gs.getFrame();
                  }
                  if (test != testNr) {
                     testNr = test - 1;
                  }
                  boolean found = false;
                  if (testNr >= smd.frameRange_.x && testNr <= smd.frameRange_.y) {
                     found = true;
                  }
                  if (!found) {
                     for (int i = 0; i < stagePosMA.size() && !found; i++) {
                        smd = stagePosMA.get(i);
                        if (testNr >= smd.frameRange_.x && testNr <= smd.frameRange_.y) {
                           found = true;
                        }
                     }
                  }
                  if (found) {
                     Point2D.Double point = new Point2D.Double(gs.getXCenter() - smd.pos_.x,
                             gs.getYCenter() - smd.pos_.y);
                     SpotData gsn = new SpotData(gs);
                     gsn.setXCenter(point.x);
                     gsn.setYCenter(point.y);
                     correctedData.add(gsn);
                  } else {
                     correctedData.add(gs);
                  }
                  
                  
               }

               // Add transformed data to data overview window
               DataCollectionForm.getInstance().addSpotData(
                       rowData.name_ + "-Jitter-Correct", rowData.title_, "", 
                       rowData.width_, rowData.height_, rowData.pixelSizeNm_, 
                       rowData.zStackStepSizeNm_, rowData.shape_, 
                       rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
                       rowData.nrSlices_, 1, rowData.maxNrSpots_, correctedData,
                       null, false, Coordinates.NM, false, 0.0, 0.0);
               
               ij.IJ.showStatus("Finished jitter correction");
            } catch (OutOfMemoryError oom) {
              System.gc();
              ij.IJ.error("Out of Memory");
            }
         }
      };

      (new Thread(doWorkRunnable)).start();
   }
   

   
   /**
    * I do not remember what the difference is with the other unjitter method...
    * @param rowData
    * @param maxFrames
    * @param maxSpots 
    */
   public void unJitter2(final RowData rowData, int maxFrames, int maxSpots) {

      // TODO: instead of a fixed number of frames, go for a certain number of spots
      // Number of frames could be limited as well
      final int maxNrFrames = maxFrames;
      final int maxNrSpots = maxSpots;

      if (rowData.spotList_.size() <= 1) {
         return;
      }
      
      ij.IJ.showStatus("Executing jitter correction");

      int mag = (int) (rowData.pixelSizeNm_ / 40.0);
      while (mag % 2 != 0) {
         mag += 1;
      }

      int width = mag * rowData.width_;
      int height = mag * rowData.height_;

      int size = width * height;

      // TODO: add 0 padding to deal with aberrant image sizes
      if ((width != height) || ((width & (width - 1)) != 0)) {
         JOptionPane.showMessageDialog(DataCollectionForm.getInstance(),
                 "Magnified image is not a square with a size that is a power of 2");
         ij.IJ.showStatus(" ");
         return;
      }

      // TODO: what if we should go through nrSlices instead of nrFrames?
      boolean useSlices = false;
      //int nrOfTests = rowData.nrFrames_ / framesToCombine;
      if (rowData.nrFrames_ <= 1) {
         useSlices = true;
      }
      final int nrImages = useSlices ? rowData.nrSlices_ : rowData.nrFrames_;

      ArrayList<StageMovementData> stagePos = new ArrayList<StageMovementData>();

      double factor = (double) mag / rowData.pixelSizeNm_;
           
      // Assemble the reference image (the first one)
      ImageProcessor ipRef = new ByteProcessor(width, height);
      byte[] pixelsRef = new byte[width * height];
      ipRef.setPixels(pixelsRef);

      if (rowData.frameIndexSpotList_ == null) {
         rowData.index();
      }
      int spotNr = 0;
      int frameNr = 0;
      while (spotNr < maxNrSpots && frameNr < maxNrFrames && frameNr < nrImages) {
         List<SpotData> frameSpots = rowData.frameIndexSpotList_.get(frameNr);
         if (frameSpots != null) {
            for (SpotData spot: frameSpots) {
               int x = (int) (factor * spot.getXCenter());
               int y = (int) (factor * spot.getYCenter());
               int index = (y * width) + x;
               if (index < size && index > 0) {
                  if (pixelsRef[index] != -1) {
                     pixelsRef[index] += 1;
                  }
               }
               spotNr++;
            }
         }
         frameNr++;
      }

      JitterDetector jd = new JitterDetector(ipRef);

      Point2D.Double fp = new Point2D.Double(0.0, 0.0);
      jd.getJitter(ipRef, fp);

      
      // Assemble images for all subsequent frames and calculate cross-correlation with the first image
      Point2D.Double com = new Point2D.Double(0.0, 0.0);
      ImageProcessor ipTest = new ByteProcessor(width, height);
      byte[] pixelsTest = new byte[width * height];
      ipTest.setPixels(pixelsTest);

      int estimatedNrTests;
      int testNr = 1;

      while (frameNr < nrImages) {
         estimatedNrTests = nrImages / (frameNr / testNr) + 1;
         ij.IJ.showStatus("Executing jitter correction..." + testNr
                 + "/" + estimatedNrTests);
         ij.IJ.showProgress(testNr, estimatedNrTests);
         for (int p = 0; p < size; p++) {
            ipTest.set(p, 0);
         }

         int tmpFrameNr = 0;
         spotNr = 0;

         while (spotNr < maxNrSpots && tmpFrameNr < maxNrFrames && frameNr < nrImages) {
            List<SpotData> frameSpots = rowData.frameIndexSpotList_.get(frameNr); 
            if (frameSpots != null) {
               for (SpotData spot : frameSpots) {
                  int x = (int) (factor * spot.getXCenter());
                  int y = (int) (factor * spot.getYCenter());
                  int index = (y * width) + x;
                  if (index < size && index > 0) {
                     if (pixelsTest[index] != -1) {
                        pixelsTest[index] += 1;
                     }
                  }
                  spotNr++;
               }
            }
            tmpFrameNr++;
            frameNr++;
         }

         jd.getJitter(ipTest, com);
         double x = (fp.x - com.x) / factor;
         double y = (fp.y - com.y) / factor;
         if (rowData.timePoints_ != null) {
            rowData.timePoints_.get(frameNr);
         }
         stagePos.add(new StageMovementData(new Point2D.Double(x, y),
                 new Point(frameNr - tmpFrameNr, frameNr - 1)));
         System.out.println("X: " + x + " Y: " + y);
         testNr++;
      }


      // Assemble stage movement data into a track
      try {
         List<SpotData> stageMovementData = new ArrayList<SpotData>();
         SpotData sm = new SpotData(null, 1, 1, 1, 1, 1, 1, 1);
         sm.setData(0, 0, 0, 0, 0.0, 0, 0, 0, 0);
         stageMovementData.add(sm);

         //stagePos = movingAverage(stagePos, 5);

         for (int i = 0; i < stagePos.size(); i++) {
            StageMovementData smd = stagePos.get(i);
            SpotData s =
                    new SpotData(null, 1, 1, i + 2, 1, 1, 1, 1);
            s.setData(0, 0, smd.pos_.x, smd.pos_.y, 0.0, 0, 0, 0, 0);
            stageMovementData.add(s);
         }


         // Add stage movement data to overview window
         // First try to copy the time points
         ArrayList<Double> timePoints = null;

         RowData newRow = new RowData(
                 rowData.name_ + "-Jitter", rowData.title_, "", rowData.width_,
                 rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
                 rowData.shape_, rowData.halfSize_, rowData.nrChannels_, 
                 stageMovementData.size(), 1, 1, stageMovementData.size(), 
                 stageMovementData, timePoints, true, Coordinates.NM, 
                 false, 0, 0);
         DataCollectionForm.getInstance().getRowData().add(newRow);
         DataCollectionForm.getInstance().fireRowAdded();
         
         ij.IJ.showStatus("Assembling jitter corrected dataset...");
         ij.IJ.showProgress(1);

         List<SpotData> correctedData = new ArrayList<SpotData>();
         Iterator it = rowData.spotList_.iterator();

         testNr = 0;
         StageMovementData smd = stagePos.get(0);
         while (it.hasNext()) {
            SpotData gs = (SpotData) it.next();
            int test;
            if (useSlices) {
               test = gs.getSlice();
            } else {
               test = gs.getFrame();
            }
            if (test != testNr) {
               testNr = test - 1;
            }
            boolean found = false;
            if (testNr >= smd.frameRange_.x && testNr <= smd.frameRange_.y) {
               found = true;
            }
            if (!found) {
               for (int i = 0; i < stagePos.size() && !found; i++) {
                  smd = stagePos.get(i);
                  if (testNr >= smd.frameRange_.x && testNr <= smd.frameRange_.y) {
                     found = true;
                  }
               }
            }
            if (found) {
               Point2D.Double point = new Point2D.Double(gs.getXCenter() - smd.pos_.x,
                       gs.getYCenter() - smd.pos_.y);
               SpotData gsn = new SpotData(gs);
               gsn.setXCenter(point.x);
               gsn.setYCenter(point.y);
               correctedData.add(gsn);
            } else {
               correctedData.add(gs);
            }
         }

         // Add transformed data to data overview window
         DataCollectionForm.getInstance().addSpotData(
                 rowData.name_ + "-Jitter-Correct", rowData.title_, "", rowData.width_,
                 rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
                 rowData.shape_, rowData.halfSize_, rowData.nrChannels_, 
                 rowData.nrFrames_, rowData.nrSlices_, 1, rowData.maxNrSpots_, 
                 correctedData, null, false, Coordinates.NM, rowData.hasZ_, 
                 rowData.minZ_, rowData.maxZ_);
         ij.IJ.showStatus("Finished jitter correction");
      } catch (OutOfMemoryError oom) {
         System.gc();
         ij.IJ.error("Out of Memory");
      }
   }

   

}
