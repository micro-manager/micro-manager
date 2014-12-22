package edu.valelab.GaussianFit.datasetdisplay;

import edu.valelab.GaussianFit.DataCollectionForm;
import edu.valelab.GaussianFit.ResultsTableListener;
import edu.valelab.GaussianFit.Terms;
import edu.valelab.GaussianFit.data.GsSpotPair;
import edu.valelab.GaussianFit.data.RowData;
import edu.valelab.GaussianFit.data.SpotData;
import edu.valelab.GaussianFit.spotOperations.NearestPoint2D;
import edu.valelab.GaussianFit.spotOperations.NearestPointGsSpotPair;
import edu.valelab.GaussianFit.utils.GaussianUtils;
import edu.valelab.GaussianFit.utils.ListUtils;
import edu.valelab.GaussianFit.utils.ReportingUtils;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Arrow;
import ij.gui.ImageWindow;
import ij.gui.MessageDialog;
import ij.gui.StackWindow;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.Color;
import java.awt.Frame;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import org.jfree.data.xy.XYSeries;

/**
 *
 * @author nico
 */
public class ParticlePairLister {

   /**
    * Cycles through the spots of the selected data set and finds the most
    * nearby spot in channel 2. It will list this as a pair if the two spots are
    * within MAXMATCHDISTANCE nm of each other.
    *
    * Once all pairs are found, it will go through all frames and try to build
    * up tracks. If the spot is within MAXMATCHDISTANCE between frames, the code
    * will consider the particle to be identical.
    *
    * All "tracks" of particles will be listed
    *
    * In addition, it will list the average distance, and average distance in x
    * and y for each frame.
    *
    * spots in channel 2 that are within MAXMATCHDISTANCE of
    *
    *
    * @param rows int array indicating rows selected in table
    * @param maxDistanceNm maximum distance in nm for two spots in different
    * channels to be considered a pair
    */
   public static void listParticlePairTracks(final int[] rows, final double maxDistanceNm) {

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {

            ArrayList<RowData> rowData = DataCollectionForm.getInstance().getRowData();
            // Show Particle List as linked Results Table
            ResultsTable rt = new ResultsTable();
            rt.reset();
            rt.setPrecision(2);

            // Show Particle Summary as Linked Results Table
            ResultsTable rt2 = new ResultsTable();
            rt2.reset();
            rt2.setPrecision(1);

            for (int row : rows) {
               ArrayList<ArrayList<GsSpotPair>> spotPairsByFrame
                       = new ArrayList<ArrayList<GsSpotPair>>();

               ij.IJ.showStatus("Creating Pairs...");

               // First go through all frames to find all pairs
               int nrSpotPairsInFrame1 = 0;
               for (int frame = 1; frame <= rowData.get(row).nrFrames_; frame++) {
                  ij.IJ.showProgress(frame, rowData.get(row).nrFrames_);
                  spotPairsByFrame.add(new ArrayList<GsSpotPair>());

                  // Get points from both channels in first frame as ArrayLists        
                  ArrayList<Point2D.Double> xyPointsCh1 = new ArrayList<Point2D.Double>();
                  ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
                  for (SpotData gs : rowData.get(row).spotList_) {
                     if (gs.getFrame() == 1) {
                        Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        if (gs.getChannel() == 1) {
                           xyPointsCh1.add(point);
                        } else if (gs.getChannel() == 2) {
                           xyPointsCh2.add(point);
                        }
                     }
                  }

                  if (xyPointsCh2.isEmpty()) {
                     ReportingUtils.logError(
                             "Pairs function in Localization plugin: no points found in second channel in frame "
                             + frame);
                     continue;
                  }

                  // Find matching points in the two ArrayLists
                  Iterator it2 = xyPointsCh1.iterator();
                  NearestPoint2D np = new NearestPoint2D(xyPointsCh2, maxDistanceNm);
                  while (it2.hasNext()) {
                     SpotData gs = (SpotData) it2.next();
                     Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                     Point2D.Double pCh2 = np.findKDWSE(pCh1);
                     if (pCh2 != null) {
                        GsSpotPair pair = new GsSpotPair(gs, pCh1, pCh2);
                        //spotPairs.add(pair);
                        spotPairsByFrame.get(frame - 1).add(pair);
                     }
                  }
               }

               // We have all pairs, assemble in tracks
               ij.IJ.showStatus("Assembling tracks...");

               // prepare NearestPoint objects to speed up finding closest pair 
               ArrayList<NearestPointGsSpotPair> npsp = new ArrayList<NearestPointGsSpotPair>();
               for (int frame = 1; frame <= rowData.get(row).nrFrames_; frame++) {
                  npsp.add(new NearestPointGsSpotPair(
                          spotPairsByFrame.get(frame - 1), maxDistanceNm));
               }

               ArrayList<ArrayList<GsSpotPair>> tracks = new ArrayList<ArrayList<GsSpotPair>>();

               Iterator<GsSpotPair> iSpotPairs = spotPairsByFrame.get(0).iterator();
               int i = 0;
               while (iSpotPairs.hasNext()) {
                  ij.IJ.showProgress(i++, nrSpotPairsInFrame1);
                  GsSpotPair spotPair = iSpotPairs.next();
                  // for now, we only start tracks at frame number 1
                  if (spotPair.getGSD().getFrame() == 1) {
                     ArrayList<GsSpotPair> track = new ArrayList<GsSpotPair>();
                     track.add(spotPair);
                     int frame = 2;
                     while (frame <= rowData.get(row).nrFrames_) {

                        GsSpotPair newSpotPair = npsp.get(frame - 1).findKDWSE(
                                new Point2D.Double(spotPair.getfp().getX(), spotPair.getfp().getY()));
                        if (newSpotPair != null) {
                           spotPair = newSpotPair;
                           track.add(spotPair);
                        }
                        frame++;
                     }
                     tracks.add(track);
                  }
               }

               if (tracks.isEmpty()) {
                  MessageDialog md = new MessageDialog(DataCollectionForm.getInstance(),
                          "No Pairs found", "No Pairs found");
                  continue;
               }

               Iterator<ArrayList<GsSpotPair>> itTracks = tracks.iterator();
               int spotId = 0;
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  Iterator<GsSpotPair> itTrack = track.iterator();
                  while (itTrack.hasNext()) {
                     GsSpotPair spot = itTrack.next();
                     rt.incrementCounter();
                     rt.addValue("Spot ID", spotId);
                     rt.addValue(Terms.FRAME, spot.getGSD().getFrame());
                     rt.addValue(Terms.SLICE, spot.getGSD().getSlice());
                     rt.addValue(Terms.CHANNEL, spot.getGSD().getSlice());
                     rt.addValue(Terms.XPIX, spot.getGSD().getX());
                     rt.addValue(Terms.YPIX, spot.getGSD().getY());
                     rt.addValue("Distance", Math.sqrt(
                             NearestPoint2D.distance2(spot.getfp(), spot.getsp())));
                     rt.addValue("Orientation (sine)",
                             NearestPoint2D.orientation(spot.getfp(), spot.getsp()));
                  }
                  spotId++;
               }
               TextPanel tp;
               TextWindow win;

               String rtName = rowData.get(row).name_ + " Particle List";
               rt.show(rtName);
               ImagePlus siPlus = ij.WindowManager.getImage(rowData.get(row).title_);
               Frame frame = WindowManager.getFrame(rtName);
               if (frame != null && frame instanceof TextWindow && siPlus != null) {
                  win = (TextWindow) frame;
                  tp = win.getTextPanel();

                  // TODO: the following does not work, there is some voodoo going on here
                  for (MouseListener ms : tp.getMouseListeners()) {
                     tp.removeMouseListener(ms);
                  }
                  for (KeyListener ks : tp.getKeyListeners()) {
                     tp.removeKeyListener(ks);
                  }

                  ResultsTableListener myk = new ResultsTableListener(siPlus,
                          rt, win, rowData.get(row).halfSize_);
                  tp.addKeyListener(myk);
                  tp.addMouseListener(myk);
                  frame.toFront();
               }

               siPlus = ij.WindowManager.getImage(rowData.get(row).title_);
               if (siPlus != null && siPlus.getOverlay() != null) {
                  siPlus.getOverlay().clear();
               }
               Arrow.setDefaultWidth(0.5);

               itTracks = tracks.iterator();
               spotId = 0;
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  ArrayList<Double> distances = new ArrayList<Double>();
                  ArrayList<Double> orientations = new ArrayList<Double>();
                  ArrayList<Double> xDiff = new ArrayList<Double>();
                  ArrayList<Double> yDiff = new ArrayList<Double>();
                  for (GsSpotPair pair : track) {
                     distances.add(Math.sqrt(
                             NearestPoint2D.distance2(pair.getfp(), pair.getsp())));
                     orientations.add(NearestPoint2D.orientation(pair.getfp(),
                             pair.getsp()));
                     xDiff.add(pair.getfp().getX() - pair.getsp().getX());
                     yDiff.add(pair.getfp().getY() - pair.getsp().getY());
                  }
                  GsSpotPair pair = track.get(0);
                  rt2.incrementCounter();
                  rt2.addValue("Row ID", rowData.get(row).ID_);
                  rt2.addValue("Spot ID", spotId);
                  rt2.addValue(Terms.FRAME, pair.getGSD().getFrame());
                  rt2.addValue(Terms.SLICE, pair.getGSD().getSlice());
                  rt2.addValue(Terms.CHANNEL, pair.getGSD().getSlice());
                  rt2.addValue(Terms.XPIX, pair.getGSD().getX());
                  rt2.addValue(Terms.YPIX, pair.getGSD().getY());
                  rt2.addValue("n", track.size());

                  double avg = ListUtils.listAvg(distances);
                  rt2.addValue("Distance-Avg", avg);
                  rt2.addValue("Distance-StdDev", ListUtils.listStdDev(distances, avg));
                  double oAvg = ListUtils.listAvg(orientations);
                  rt2.addValue("Orientation-Avg", oAvg);
                  rt2.addValue("Orientation-StdDev",
                          ListUtils.listStdDev(orientations, oAvg));

                  double xDiffAvg = ListUtils.listAvg(xDiff);
                  double yDiffAvg = ListUtils.listAvg(yDiff);
                  double xDiffAvgStdDev = ListUtils.listStdDev(xDiff, xDiffAvg);
                  double yDiffAvgStdDev = ListUtils.listStdDev(yDiff, yDiffAvg);
                  rt2.addValue("Dist.Vect.Avg", Math.sqrt(
                          (xDiffAvg * xDiffAvg) + (yDiffAvg * yDiffAvg)));
                  rt2.addValue("Dist.Vect.StdDev", Math.sqrt(
                          (xDiffAvgStdDev * xDiffAvgStdDev)
                          + (yDiffAvgStdDev * yDiffAvgStdDev)));


                  /* draw arrows in overlay */
                  double mag = 100.0;  // factor that sets magnification of the arrow
                  double factor = mag * 1 / rowData.get(row).pixelSizeNm_;  // factor relating mad and pixelSize
                  int xStart = track.get(0).getGSD().getX();
                  int yStart = track.get(0).getGSD().getY();

                  Arrow arrow = new Arrow(xStart, yStart,
                          xStart + (factor * xDiffAvg),
                          yStart + (factor * yDiffAvg));
                  arrow.setHeadSize(3);
                  arrow.setOutline(false);
                  if (siPlus != null && siPlus.getOverlay() == null) {
                     siPlus.setOverlay(arrow, Color.yellow, 1, Color.yellow);
                  } else if (siPlus != null && siPlus.getOverlay() != null) {
                     siPlus.getOverlay().add(arrow);
                  }

                  spotId++;
               }
               if (siPlus != null) {
                  siPlus.setHideOverlay(false);
               }

               rtName = rowData.get(row).name_ + " Particle Summary";
               rt2.show(rtName);
               siPlus = ij.WindowManager.getImage(rowData.get(row).title_);
               frame = WindowManager.getFrame(rtName);
               if (frame != null && frame instanceof TextWindow && siPlus != null) {
                  win = (TextWindow) frame;
                  tp = win.getTextPanel();

                  // TODO: the following does not work, there is some voodoo going on here
                  for (MouseListener ms : tp.getMouseListeners()) {
                     tp.removeMouseListener(ms);
                  }
                  for (KeyListener ks : tp.getKeyListeners()) {
                     tp.removeKeyListener(ks);
                  }

                  ResultsTableListener myk = new ResultsTableListener(siPlus,
                          rt2, win, rowData.get(row).halfSize_);
                  tp.addKeyListener(myk);
                  tp.addMouseListener(myk);
                  frame.toFront();
               }

               ij.IJ.showStatus("");

            }
         }
      };

      (new Thread(doWorkRunnable)).start();

   }

   /**
    * Cycles through the spots of the selected data set and finds the most
    * nearby spot in channel 2. It will list this as a pair if the two spots are
    * within MAXMATCHDISTANCE nm of each other. In addition, it will list the
    * average distance, and average distance in x and y for each frame.
    *
    * spots in channel 2 that are within MAXMATCHDISTANCE of
    *
    * @param row
    * @param maxDistance
    */
   public static void ListParticlePairs(final int row, final double maxDistance) {

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {
            ArrayList<RowData> rowData = DataCollectionForm.getInstance().getRowData();
            ResultsTable rt = new ResultsTable();
            rt.reset();
            rt.setPrecision(2);
            ResultsTable rt2 = new ResultsTable();
            rt2.reset();
            rt2.setPrecision(2);
            int width = rowData.get(row).width_;
            int height = rowData.get(row).height_;
            double factor = rowData.get(row).pixelSizeNm_;
            boolean useS = DataCollectionForm.getInstance().useSeconds(rowData.get(row));
            ij.ImageStack stack = new ij.ImageStack(width, height);

            ImagePlus sp = new ImagePlus("Errors in pairs");

            XYSeries xData = new XYSeries("XError");
            XYSeries yData = new XYSeries("YError");

            ij.IJ.showStatus("Creating Pairs...");

            for (int frame = 1; frame <= rowData.get(row).nrFrames_; frame++) {
               ij.IJ.showProgress(frame, rowData.get(row).nrFrames_);
               ImageProcessor ip = new ShortProcessor(width, height);
               short pixels[] = new short[width * height];
               ip.setPixels(pixels);

               // Get points from both channels in each frame as ArrayLists        
               ArrayList<SpotData> gsCh1 = new ArrayList<SpotData>();
               ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
               for (SpotData gs : rowData.get(row).spotList_) {
                  if (gs.getFrame() == frame) {
                     if (gs.getChannel() == 1) {
                        gsCh1.add(gs);
                     } else if (gs.getChannel() == 2) {
                        Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        xyPointsCh2.add(point);
                     }
                  }
               }

               if (xyPointsCh2.isEmpty()) {
                  ReportingUtils.logError("Pairs function in Localization plugin: no points found in second channel in frame " + frame);
                  continue;
               }

               // Find matching points in the two ArrayLists
               Iterator it2 = gsCh1.iterator();
               NearestPoint2D np = new NearestPoint2D(xyPointsCh2,
                       maxDistance);
               ArrayList<Double> distances = new ArrayList<Double>();
               ArrayList<Double> errorX = new ArrayList<Double>();
               ArrayList<Double> errorY = new ArrayList<Double>();
               while (it2.hasNext()) {
                  SpotData gs = (SpotData) it2.next();
                  Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                  Point2D.Double pCh2 = np.findKDWSE(pCh1);
                  if (pCh2 != null) {
                     rt.incrementCounter();
                     rt.addValue(Terms.POSITION, gs.getPosition());
                     rt.addValue(Terms.FRAME, gs.getFrame());
                     rt.addValue(Terms.SLICE, gs.getSlice());
                     rt.addValue(Terms.CHANNEL, gs.getSlice());
                     rt.addValue(Terms.XPIX, gs.getX());
                     rt.addValue(Terms.YPIX, gs.getY());
                     rt.addValue("X1", pCh1.getX());
                     rt.addValue("Y1", pCh1.getY());
                     rt.addValue("X2", pCh2.getX());
                     rt.addValue("Y2", pCh2.getY());
                     double d2 = NearestPoint2D.distance2(pCh1, pCh2);
                     double d = Math.sqrt(d2);
                     rt.addValue("Distance", d);
                     rt.addValue("Orientation (sine)",
                             NearestPoint2D.orientation(pCh1, pCh2));
                     distances.add(d);
                     
                     ip.putPixel((int) (pCh1.x / factor), (int) (pCh1.y / factor), (int) d);
                     
                     double ex = pCh2.getX() - pCh1.getX();
                     //double ex = (pCh1.getX() - pCh2.getX()) * (pCh1.getX() - pCh2.getX());
                     //ex = Math.sqrt(ex);
                     errorX.add(ex);
                     //double ey = (pCh1.getY() - pCh2.getY()) * (pCh1.getY() - pCh2.getY());
                     //ey = Math.sqrt(ey);
                     double ey = pCh2.getY() - pCh1.getY();
                     errorY.add(ey);
                     
                  }
               }
               Double avg = ListUtils.listAvg(distances);
               Double stdDev = ListUtils.listStdDev(distances, avg);
               Double avgX = ListUtils.listAvg(errorX);
               Double stdDevX = ListUtils.listStdDev(errorX, avgX);
               Double avgY = ListUtils.listAvg(errorY);
               Double stdDevY = ListUtils.listStdDev(errorY, avgY);
               rt2.incrementCounter();
               rt2.addValue("Frame Nr.", frame);
               rt2.addValue("Avg. distance", avg);
               rt2.addValue("StdDev distance", stdDev);
               rt2.addValue("X", avgX);
               rt2.addValue("StdDev X", stdDevX);
               rt2.addValue("Y", avgY);
               rt2.addValue("StdDevY", stdDevY);
               stack.addSlice("frame: " + frame, ip);
               double timePoint = frame;
               if (rowData.get(row).timePoints_ != null) {
                  timePoint = rowData.get(row).timePoints_.get(frame);
                  if (useS) {
                     timePoint /= 1000;
                  }
               }
               xData.add(timePoint, avgX);
               yData.add(timePoint, avgY);
            }

            if (rt.getCounter() == 0) {
               MessageDialog md = new MessageDialog(DataCollectionForm.getInstance(),
                       "No Pairs found", "No Pairs found");
               return;
            }

            // show summary in resultstable
            rt2.show("Summary of Pairs found in " + rowData.get(row).name_);

            //  show Pairs panel and attach listener
            TextPanel tp;
            TextWindow win;

            String rtName = "Pairs found in " + rowData.get(row).name_;
            rt.show(rtName);
            ImagePlus siPlus = ij.WindowManager.getImage(rowData.get(row).title_);
            Frame frame = WindowManager.getFrame(rtName);
            if (frame != null && frame instanceof TextWindow && siPlus != null) {
               win = (TextWindow) frame;
               tp = win.getTextPanel();

               // TODO: the following does not work, there is some voodoo going on here
               for (MouseListener ms : tp.getMouseListeners()) {
                  tp.removeMouseListener(ms);
               }
               for (KeyListener ks : tp.getKeyListeners()) {
                  tp.removeKeyListener(ks);
               }

               ResultsTableListener myk = new ResultsTableListener(siPlus,
                       rt, win, rowData.get(row).halfSize_);
               tp.addKeyListener(myk);
               tp.addMouseListener(myk);
               frame.toFront();
            }

            String xAxis = "Time (frameNr)";
            if (rowData.get(row).timePoints_ != null) {
               xAxis = "Time (ms)";
               if (useS) {
                  xAxis = "Time (s)";
               }
            }
            GaussianUtils.plotData2("Error in " + rowData.get(row).name_,
                    xData, yData, xAxis, "Error(nm)", 0, 400);

            ij.IJ.showStatus("");

            sp.setOpenAsHyperStack(true);
            sp.setStack(stack, 1, 1, rowData.get(row).nrFrames_);
            sp.setDisplayRange(0, 20);
            sp.setTitle(rowData.get(row).title_);

            ImageWindow w = new StackWindow(sp);
            w.setTitle("Error in " + rowData.get(row).name_);

            w.setImage(sp);
            w.setVisible(true);

         }
      };

      (new Thread(doWorkRunnable)).start();

   }
}