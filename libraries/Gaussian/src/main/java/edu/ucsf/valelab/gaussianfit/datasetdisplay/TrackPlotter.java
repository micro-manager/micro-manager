/*
 * Copyright (c) 2015-2017, Regents the University of California
 * Author: Nico Stuurman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package edu.ucsf.valelab.gaussianfit.datasetdisplay;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.algorithm.FFTUtils;
import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.utils.GaussianUtils;
import java.awt.Component;
import javax.swing.JOptionPane;
import org.jfree.data.xy.XYSeries;

/**
 * @author nico
 */
public class TrackPlotter {

   public static final String[] PLOTMODES = {"t-X", "t-Y", "t-dist", "t-Int", "X-Y"};

   /**
    * Plots Tracks using JFreeChart
    *
    * @param rowDatas
    * @param plotMode        - Index of plotMode in array {"t-X", "t-Y", "t-dist.", "t-Int.",
    *                        "X-Y"};
    * @param doLog
    * @param doPowerSpectrum
    * @param comp
    */
   public static void plotData(RowData[] rowDatas, int plotMode, final boolean doLog,
         final boolean doPowerSpectrum, Component comp) {
      String title = PLOTMODES[plotMode];
      boolean useShapes = true;
      if (doLog || doPowerSpectrum) {
         useShapes = false;
      }
      if (rowDatas.length == 1) {
         title = rowDatas[0].getName() + " " + PLOTMODES[plotMode];
      }

      XYSeries[] datas = new XYSeries[rowDatas.length];

      boolean useS = false;
      for (RowData row : rowDatas) {
         if (row.useSeconds()) {
            useS = true;
         }
      }
      boolean hasTimeInfo = rowDatas[0].hasTimeInfo();
      for (RowData row : rowDatas) {
         if (row.hasTimeInfo() != hasTimeInfo) {
            JOptionPane.showMessageDialog(comp,
                  "Some rows have time information whereas others have not.");
            return;
         }
      }

      String xAxis = null;

      switch (plotMode) {

         case (0): { // t-X
            if (doPowerSpectrum) {
               FFTUtils.calculatePSDs(rowDatas, datas, DataCollectionForm.PlotMode.X);
               xAxis = "Freq (Hz)";
               GaussianUtils.plotDataN(title + " PSD", datas, xAxis, "Strength",
                     0, 400, useShapes, doLog);

            } else {
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
               }

               for (int index = 0; index < rowDatas.length; index++) {
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     if (hasTimeInfo) {
                        double timePoint = rowDatas[index].timePoints_.get(i);
                        if (useS) {
                           timePoint /= 1000;
                        }
                        datas[index].add(timePoint, spot.getXCenter());
                     } else {
                        datas[index].add(i, spot.getXCenter());
                     }
                  }
                  xAxis = "Time (frameNr)";
                  if (hasTimeInfo) {
                     xAxis = "Time (ms)";
                     if (useS) {
                        xAxis = "Time(s)";
                     }
                  }
               }
               GaussianUtils.plotDataN(title, datas, xAxis, "X(nm)", 0, 400, useShapes, doLog);
            }
         }
         break;

         case (1): { // t-Y
            if (doPowerSpectrum) {
               FFTUtils.calculatePSDs(rowDatas, datas, DataCollectionForm.PlotMode.Y);
               xAxis = "Freq (Hz)";
               GaussianUtils.plotDataN(title + " PSD", datas, xAxis, "Strength",
                     0, 400, useShapes, doLog);
            } else {
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
               }
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     if (hasTimeInfo) {
                        double timePoint = rowDatas[index].timePoints_.get(i);
                        if (useS) {
                           timePoint /= 1000;
                        }
                        datas[index].add(timePoint, spot.getYCenter());
                     } else {
                        datas[index].add(i, spot.getYCenter());
                     }
                  }
                  xAxis = "Time (frameNr)";
                  if (hasTimeInfo) {
                     xAxis = "Time (ms)";
                     if (useS) {
                        xAxis = "Time(s)";
                     }
                  }
               }
               GaussianUtils.plotDataN(title, datas, xAxis, "Y(nm)", 0, 400, useShapes, doLog);
            }
         }
         break;

         case (2): { // t-dist.
            if (doPowerSpectrum) {
               /*
               FFTUtils.calculatePSDs(rowDatas, datas, PlotMode.Y);
               xAxis = "Freq (Hz)";
               GaussianUtils.plotDataN(title + " PSD", datas, xAxis, "Strength",
                       0, 400, useShapes, logLog);
                       * */
            } else {
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
               }
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
                  SpotData sp = rowDatas[index].spotList_.get(0);
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     double distX = (sp.getXCenter() - spot.getXCenter())
                           * (sp.getXCenter() - spot.getXCenter());
                     double distY = (sp.getYCenter() - spot.getYCenter())
                           * (sp.getYCenter() - spot.getYCenter());
                     double dist = Math.sqrt(distX + distY);
                     if (hasTimeInfo) {
                        double timePoint = rowDatas[index].timePoints_.get(i);
                        if (useS) {
                           timePoint /= 1000.0;
                        }
                        datas[index].add(timePoint, dist);
                     } else {
                        datas[index].add(i, dist);
                     }
                  }
                  xAxis = "Time (frameNr)";
                  if (hasTimeInfo) {
                     xAxis = "Time (ms)";
                     if (useS) {
                        xAxis = "Time (s)";
                     }
                  }
               }
               GaussianUtils
                     .plotDataN(title, datas, xAxis, " distance (nm)", 0, 400, useShapes, doLog);
            }
         }
         break;

         case (3): { // t-Int
            if (doPowerSpectrum) {
               JOptionPane.showMessageDialog(comp, "Function is not implemented");
            } else {
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
               }
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_);
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     if (hasTimeInfo) {
                        double timePoint = rowDatas[index].timePoints_.get(i);
                        if (useS) {
                           timePoint /= 1000;
                        }
                        datas[index].add(timePoint, spot.getIntensity());
                     } else {
                        datas[index].add(i, spot.getIntensity());
                     }
                  }
                  xAxis = "Time (frameNr)";
                  if (hasTimeInfo) {
                     xAxis = "Time (ms)";
                     if (useS) {
                        xAxis = "Time (s)";
                     }

                  }
               }
               GaussianUtils.plotDataN(title, datas, xAxis, "Intensity (#photons)",
                     0, 400, useShapes, doLog);
            }
         }
         break;

         case (4): { // X-Y
            if (doPowerSpectrum) {
               JOptionPane.showMessageDialog(comp, "Function is not implemented");
            } else {
               double minX = Double.MAX_VALUE;
               double minY = Double.MAX_VALUE;
               double maxX = Double.MIN_VALUE;
               double maxY = Double.MIN_VALUE;
               for (int index = 0; index < rowDatas.length; index++) {
                  datas[index] = new XYSeries(rowDatas[index].ID_, false, true);
                  for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                     SpotData spot = rowDatas[index].spotList_.get(i);
                     datas[index].add(spot.getXCenter(), spot.getYCenter());
                     minX = Math.min(minX, spot.getXCenter());
                     minY = Math.min(minY, spot.getYCenter());
                     maxX = Math.max(maxX, spot.getXCenter());
                     maxY = Math.max(maxY, spot.getYCenter());
                  }
               }
               double xDivisor = 1.0;
               double yDivisor = 1.0;
               String xAxisTitle = "X(nm)";
               String yAxisTitle = "Y(nm)";
               if (maxX - minX > 10000) {
                  xAxisTitle = "X(micron)";
                  xDivisor = 1000;
               }
               if (maxY - minY > 10000) {
                  yAxisTitle = "Y(micron)";
                  yDivisor = 1000;
               }
               if (xDivisor != 1.0 || yDivisor != 1.0) {
                  for (int index = 0; index < rowDatas.length; index++) {
                     datas[index] = new XYSeries(rowDatas[index].ID_, false, true);
                     for (int i = 0; i < rowDatas[index].spotList_.size(); i++) {
                        SpotData spot = rowDatas[index].spotList_.get(i);
                        datas[index].add(spot.getXCenter() / xDivisor,
                              spot.getYCenter() / yDivisor);
                     }
                  }
               }

               GaussianUtils
                     .plotDataN(title, datas, xAxisTitle, yAxisTitle, 0, 400, useShapes, doLog);
            }
         }
         break;
      }
   }


}
