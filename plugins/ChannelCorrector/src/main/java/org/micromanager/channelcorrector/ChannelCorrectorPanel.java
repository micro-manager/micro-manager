///////////////////////////////////////////////////////////////////////////////
//FILE:          ChannelCorrectorPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     ChannelCorrector plugin
//
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2020
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.channelcorrector;


import edu.ucsf.valelab.gaussianfit.algorithm.FindLocalMaxima;
import edu.ucsf.valelab.gaussianfit.algorithm.GaussianFit;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.datasettransformations.CoordinateMapper;
import edu.ucsf.valelab.gaussianfit.spotoperations.NearestPoint2D;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.display.DataViewer;
import org.micromanager.propertymap.MutablePropertyMapView;

import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;


public class ChannelCorrectorPanel extends JPanel {
   private AffineTransform affineTransform_;
   private boolean useAllPositions_;
   private final Studio studio_;
   private final DataViewer dataViewer_;
   private final int ch2nr_;
   private final List<JFormattedTextField> ftfs = new ArrayList<>(6);

   private final int distance_ = 16; // minimum distance between local maxima
   private final int halfSize_ = distance_ / 2;
   private final int threshold_ = 100;
   private final int maxIterations_ = 100; // maximum number of iterations for the Gaussian fit
   private final int fitmode_ = 2; // Use Nelder Mead to perform the minimization
   private final int maxPairDistance_ = 10; // max distance  in pixels between spots in both channels
   // anything larger can not match

   public ChannelCorrectorPanel(Studio studio, DataViewer dataViewer, int ch2nr,
                                ExecutorService executor, boolean useAllPositions) {
      studio_ = studio;
      dataViewer_ = dataViewer;
      DataProvider dataProvider = dataViewer_.getDataProvider();
      ch2nr_ = ch2nr;
      useAllPositions_ = useAllPositions;
      String channelGroup = dataProvider.getSummaryMetadata().getChannelGroup();
      List<String> channels = dataProvider.getSummaryMetadata().getChannelNameList();
      final MutablePropertyMapView settings = studio.profile().getSettings(this.getClass());
      final String key = channelGroup + "-" + channels.get(0) + "-" + channels.get(ch2nr_);
      affineTransform_ = settings.getAffineTransform(key, new AffineTransform());
      super.setLayout(new MigLayout("flowx, fill, insets 8"));

      super.add(new JLabel(channels.get(0) + "-" + channels.get(ch2nr_)), "span 3, split 2, growx");
      JButton calculateButton = new JButton("Calculate from Image");
      calculateButton.addActionListener((ActionEvent ae) -> {
         executor.submit(new Runnable() {
            @Override
            public void run() {
               try {
                  SwingUtilities.invokeLater(() -> {
                     studio_.alerts().postAlert("ChannelCorrector", this.getClass(),
                             "Calculating " + channels.get(0) + "-" + channels.get(ch2nr_));
                  });
                  AffineTransform af = calculateTransform();
                  if (af != null) {
                     affineTransform_.setTransform(af);
                     double[] flatAffine = new double[6];
                     affineTransform_.getMatrix(flatAffine);
                     SwingUtilities.invokeLater(() -> {
                        int i = 0;
                        for (int row = 0; row < 2; row++) {
                           for (int col = 0; col < 3; col++) {
                              ftfs.get(i).setValue(flatAffine[row + col * 2]);
                              i++;
                           }
                        }
                        studio_.alerts().postAlert("ChannelCorrector", this.getClass(),
                                "Finished " +  channels.get(0) + "-" + channels.get(ch2nr_));
                     });
                  }
               } catch (IOException ioe) {
                  studio_.logs().showError("Failed to get image data");
               }
            }
         });
      });
      super.add(calculateButton, "right, wrap");
      double[] flatAffine = new double[6];
      affineTransform_.getMatrix(flatAffine);
      NumberFormat affFormat = NumberFormat.getInstance();
      affFormat.setMinimumFractionDigits(5);
      for (int row = 0; row < 2; row++) {
         for (int col = 0; col < 3; col++) {
            final int r = row;
            final int c = col;
            JFormattedTextField ftf = new JFormattedTextField(affFormat);
            ftf.setValue(flatAffine[row + col * 2]);
            ftf.addPropertyChangeListener("value", evt -> {
               if (ftf.getValue() instanceof Double) {
                  flatAffine[r + c * 2] = (Double) ftf.getValue();
               }
               else if (ftf.getValue() instanceof Long) {
                  flatAffine[r + c * 2] = (Long) ftf.getValue();
               }
               affineTransform_.setTransform(flatAffine[0], flatAffine[1], flatAffine[2],
                       flatAffine[3], flatAffine[4], flatAffine[5]);
               settings.putAffineTransform(key, affineTransform_);
            });
            String wrap = "";
            if (col == 2) {
               wrap = ", wrap";
            }
            ftfs.add(ftf);
            super.add(ftf, "width 100" + wrap);
         }
      }
   }

   public void updateValues() {
      for (JFormattedTextField ftf : ftfs) {
         try {
            ftf.commitEdit();
         } catch (ParseException e) {
            //e.printStackTrace();
         }
      }
   }

   public void setUseAllPositions(boolean value) {
      useAllPositions_ = value;
   }

   public AffineTransform getAffineTransform() {
      return affineTransform_;
   }

   public AffineTransform calculateTransform() throws IOException {
      CoordinateMapper.PointMap points = new CoordinateMapper.PointMap();
      GaussianFit gf = new GaussianFit(1, fitmode_);

      DataProvider dataProvider = dataViewer_.getDataProvider();
      int p = dataViewer_.getDisplayPosition().getP();
      List<Integer> ps = new ArrayList<>();
      if (useAllPositions_) {
         for (p = 0; p < dataProvider.getNextIndex(Coords.P); p++) {
            ps.add(p);
         }
      } else {
         ps.add(p);
      }
      for (Integer pp : ps) {
         Coords.Builder cb = Coordinates.builder().c(0).t(0).p(pp).z(0);
         Image img1 = dataProvider.getImage(cb.build());
         Image img2 = dataProvider.getImage(cb.c(ch2nr_).build());
         ArrayList<Point2D.Double> xyPointsCh1 = detectPoints(img1, gf);
         ArrayList<Point2D.Double> xyPointsCh2 = detectPoints(img2, gf);

         // Find matching points in the two ArrayLists
         NearestPoint2D np = new NearestPoint2D(xyPointsCh2, maxPairDistance_);
         for (Point2D.Double pCh1 : xyPointsCh1) {
            Point2D.Double pCh2 = np.findKDWSE(pCh1);
            if (pCh2 != null) {
               points.put(pCh1, pCh2);
            }
         }
      }

      if (points.size() < 4) {
         JOptionPane.showMessageDialog(null,
                 "Found less than 4 matching points.  Can not calculate affine transform",
                 "Error", JOptionPane.ERROR_MESSAGE);
         return null;
      }

      CoordinateMapper c2t = new CoordinateMapper(points, 1, 2, false);

      AffineTransform af = c2t.getAffineTransform();
      try {
         af = af.createInverse();
      } catch (NoninvertibleTransformException ex) {
         JOptionPane.showMessageDialog(null, "Inexplicably failed to invert the affine transform",
                 "Annoying Error!", JOptionPane.ERROR_MESSAGE);
      }

      return af;
   }

   private ArrayList<Point2D.Double> detectPoints(Image img, GaussianFit gf) {
      ArrayList<Point2D.Double> pointsList = new ArrayList<>();
      ImageProcessor siProc = studio_.data().ij().createProcessor(img);
      ImagePlus siPlus = new ImagePlus("noname", siProc);
      Polygon maxima = FindLocalMaxima.FindMax(siPlus, distance_, threshold_,
              FindLocalMaxima.FilterType.NONE);

      int[][] sC = new int[maxima.npoints][2];
      for (int j = 0; j < maxima.npoints; j++) {
         sC[j][0] = maxima.xpoints[j];
         sC[j][1] = maxima.ypoints[j];
      }

      Arrays.sort(sC, new SpotSortComparator());

      for (int j = 0; j < sC.length; j++) {
         // filter out spots too close to the edge
         if (sC[j][0] > halfSize_ && sC[j][0] < siPlus.getWidth() - halfSize_
                 && sC[j][1] > halfSize_ && sC[j][1] < siPlus.getHeight() - halfSize_) {
            ImageProcessor sp = SpotData.getSpotProcessor(siProc,
                    halfSize_, sC[j][0], sC[j][1]);
            if (sp == null) {
               continue;
            }

            GaussianFit.Data gfData =  gf.dogaussianfit(sp, maxIterations_);
            double[] paramsOut = gfData.getParms();
            if (paramsOut.length > 3) {
               double x = sC[j][0] - halfSize_ + paramsOut[2];
               double y = sC[j][1] - halfSize_ + paramsOut[3];
               pointsList.add(new Point2D.Double(x, y));
            }
         }
      }
      return pointsList;
   }


   private static class SpotSortComparator implements Comparator {

      // Return the result of comparing the two row arrays
      @Override
      public int compare(Object o1, Object o2) {
         int[] p1 = (int[]) o1;
         int[] p2 = (int[]) o2;
         if (p1[0] < p2[0]) {
            return -1;
         }
         if (p1[0] > p2[0]) {
            return 1;
         }
         if (p1[0] == p2[0]) {
            if (p1[1] < p2[1]) {
               return -1;
            }
            if (p1[1] > p2[1]) {
               return 1;
            }
         }
         return 0;
      }
   }

}
