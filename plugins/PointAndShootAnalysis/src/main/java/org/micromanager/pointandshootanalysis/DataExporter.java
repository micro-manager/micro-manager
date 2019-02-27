
package org.micromanager.pointandshootanalysis;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.Point2D;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.ddogleg.optimization.FactoryOptimization;
import org.ddogleg.optimization.UnconstrainedLeastSquares;
import org.ddogleg.optimization.UtilOptimize;
import org.ejml.data.DMatrixRMaj;
import org.jfree.data.xy.XYSeries;
import org.micromanager.Studio;
import org.micromanager.pointandshootanalysis.algorithm.LinearFunc;
import org.micromanager.pointandshootanalysis.algorithm.PASFunction;
import org.micromanager.pointandshootanalysis.algorithm.SingleExpRecoveryFunc;
import org.micromanager.pointandshootanalysis.data.FitData;
import org.micromanager.pointandshootanalysis.data.PASData;
import org.micromanager.pointandshootanalysis.data.ParticleData;
import org.micromanager.pointandshootanalysis.data.TrackInfoCalculator;
import org.micromanager.pointandshootanalysis.display.WidgetSettings;
import org.micromanager.pointandshootanalysis.plot.PlotUtils;

/**
 *
 * @author Nico
 */
public class DataExporter {
   public enum Type {BLEACH, PARTICLE, PARTICLE_AND_BLEACH}
   
   final private Type type_;
   final private Studio studio_;
   final private List<PASData> data_;
   final private Map<Integer, Instant> frameTimeStamps_;
   
   public DataExporter(Studio studio, List<PASData> data, Map<Integer, 
               Instant> frameTimeStamps, Type type) {
      studio_ = studio;
      data_ = data;
      frameTimeStamps_ = frameTimeStamps;
      type_ = type;
   }
   
   /**
    * 
    * @param index
    * @param type
    * @param fitFunction
    * @param msLimit
    * @return 
    */
   public FitData fit(int index, Type type, Class fitFunction, Integer msLimit) {
      PASData d = data_.get(index);
      if (d == null) {
         return null; // TODO: throw error?
      }
      // look for the first datapoint where intensity went up
      // consider that to be the first bleach point
      boolean startFound = false;
      boolean endReached = false;
      int startFrame = d.framePasClicked();
      double startIntensity = Double.MAX_VALUE;
      while (!startFound && !endReached) {
         Double intensity = null;
         switch(type) {
            case BLEACH: 
               intensity = d.particleDataTrack().get(startFrame).getNormalizedBleachMaskAvg();
               break;
            case PARTICLE:
               intensity = d.particleDataTrack().get(startFrame).getNormalizedMaskAvg();
               break;
            case PARTICLE_AND_BLEACH:
               intensity = d.particleDataTrack().get(startFrame).getNormalizedMaskIncludingBleachAvg();
         }
         if (intensity != null && intensity > startIntensity && intensity < 1.0) {
            startFound = true;
         } else {
            startFrame++;
            if (startFrame >= frameTimeStamps_.size()) {
               endReached = true;
            }
            if (intensity != null) {
               startIntensity = intensity;
            }
         }
      }
      if (endReached) {
         return null; // TODO: report?
      }
      
      List<Point2D> dataAsList = new ArrayList<>();
      for (int frame = startFrame; frame < frameTimeStamps_.size(); frame++) {
         Double intensity = null;
         switch(type) {
            case BLEACH: 
               intensity = d.particleDataTrack().get(frame).getNormalizedBleachMaskAvg();
               break;
            case PARTICLE:
               intensity = d.particleDataTrack().get(frame).getNormalizedMaskAvg();
               break;
            case PARTICLE_AND_BLEACH:
               intensity = d.particleDataTrack().get(frame).getNormalizedMaskIncludingBleachAvg();
         }
         if (intensity != null) {
            double ms = frameTimeStamps_.get(frame).toEpochMilli()
                          - frameTimeStamps_.get(d.framePasClicked()).toEpochMilli();
            if (msLimit == null || ms < msLimit) {
               dataAsList.add( new Point2D.Double(ms, intensity) );
            }
         }
      }
      PASFunction func = null;
      if (fitFunction == SingleExpRecoveryFunc.class) {
         func = new SingleExpRecoveryFunc(dataAsList);
      } else if (fitFunction == LinearFunc.class) {
         func = new LinearFunc(dataAsList);
      }
      if (func == null) {
         return null;
      }
      UnconstrainedLeastSquares<DMatrixRMaj> optimizer = FactoryOptimization.levenbergMarquardt(null, true);
      optimizer.setFunction(func, null);
      //optimizer.setVerbose(System.out,0);
      double startTimeEstimate = frameTimeStamps_.get(startFrame).toEpochMilli() - 
              frameTimeStamps_.get(d.framePasClicked()).toEpochMilli();
      if (fitFunction == SingleExpRecoveryFunc.class) {
         optimizer.initialize(new double[]{0.8, startTimeEstimate, 0.002}, 1e-12, 1e-12);
      } else if (fitFunction == LinearFunc.class) {
         optimizer.initialize(new double[]{dataAsList.get(0).getY(), 0.001}, 1e-12, 1e-12);
      }
      UtilOptimize.process(optimizer, 50);
      double[] found = optimizer.getParameters();
      double rSquared = func.getRSquared(found);
      double yAtStart = func.calculate(found, frameTimeStamps_.get(startFrame).toEpochMilli()
                          - frameTimeStamps_.get(d.framePasClicked()).toEpochMilli() );
      double tAtStart = frameTimeStamps_.get(startFrame).toEpochMilli()
                          - frameTimeStamps_.get(d.framePasClicked()).toEpochMilli();
      double yHalf = (found[0] - yAtStart) / 2.0 + yAtStart;
      double tHalf = func.calculateX(found, yHalf) - tAtStart;
      func.setParms(found);
      System.out.println("A: " + found[0] + ", b: " + found[1]);
      if (func.getParms().length > 2) {
         System.out.println(", k: " + found[2]);
      }
      System.out.println("RSquared: " + rSquared + ", t1/2: " + tHalf + " ms");
      FitData fitData = new FitData(dataAsList, 
              SingleExpRecoveryFunc.class, 
              type_, 
              found, 
              rSquared, 
              tHalf);
      
      return fitData;
   }
   
   public void exportRaw(List<Integer> indices) {
      StringBuilder export = new StringBuilder();
      for (int index : indices) {
         PASData d = data_.get(index);
         if (d != null) {
            export.append(d.dataSetName()).append("\t\n");
            export.append(d.id()).append("\t\n");
            switch(type_) {
                  case BLEACH: export.append("type: bleach\t\n"); break;
                  case PARTICLE: export.append("type: particle\t\n"); break;
                  case PARTICLE_AND_BLEACH: export.append("type: particle plus bleach\t\n"); break;
               }
            export.append("ms\tintensity\n");
            Set<Map.Entry<Integer, ParticleData>> entrySet = d.particleDataTrack().entrySet();
            for (Entry<Integer, ParticleData> es : entrySet) {
               double ts = frameTimeStamps_.get(es.getKey()).toEpochMilli()
                          - frameTimeStamps_.get(d.framePasClicked()).toEpochMilli();
               Double val = null;
               switch(type_) {
                  case BLEACH: val = es.getValue().getNormalizedBleachMaskAvg(); break;
                  case PARTICLE: val = es.getValue().getNormalizedMaskAvg(); break;
                  case PARTICLE_AND_BLEACH: val = es.getValue().getNormalizedMaskIncludingBleachAvg(); break;
               }
               if (val != null) {
                  export.append(ts).append("\t").append(val).append("\n");
               }
            }
         }
      }
      StringSelection selection = new StringSelection(export.toString());
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(selection, selection);
   }
   
   public void exportSummary(List<Integer> indices) {
      StringBuilder export = new StringBuilder();
      export.append("title\tID\tParticle Size\tBleach Spot k\t");
      export.append("Bleach Spot yMax\tBleach Spot t1/2\tBleach Spot rSq\n");
      for (int index : indices) {
         PASData d = data_.get(index);
         if (d != null) {
            try {
            export.append(d.dataSetName()).append("\t").append(d.id()).append("\t");
            double avgPSize = TrackInfoCalculator.avgParticleSize(d);
            export.append(avgPSize).append("\t");
            FitData fitData = fit(index, Type.BLEACH, SingleExpRecoveryFunc.class, null);
            export.append(fitData.parms()[2]).append("\t");  // k
            export.append(fitData.parms()[0]).append("\t");  // A
            export.append(fitData.tHalf()).append("\t");
            export.append(fitData.rSquared()).append("\n");
            } catch (java.lang.RuntimeException re) {
               export.append("Fit Failure!\n");
            }
         }
      }
      StringSelection selection = new StringSelection(export.toString());
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(selection, selection);
   }

   public void plotFits(List<Integer> indices) {
      List<XYSeries> xySeries = new ArrayList<>(2 * indices.size());
      for (int index : indices) {
         Class fitClass = SingleExpRecoveryFunc.class;
         Integer msLimit = null;
         if (type_ == Type.PARTICLE_AND_BLEACH) {
            fitClass = LinearFunc.class;
            msLimit = 5000;
         }
         FitData fitData = fit(index, type_, fitClass, msLimit);
         // TODO: check which functions was used to fit and do the right one
         PASFunction fitFunc = null;
         if (fitData.fitType() == SingleExpRecoveryFunc.class) {
            fitFunc = new SingleExpRecoveryFunc(fitData.data());
         } else if (fitData.fitType() == LinearFunc.class) {
            fitFunc = new LinearFunc(fitData.data());
         }
         if (fitFunc != null) {
            XYSeries plotData = new XYSeries(data_.get(index).id(), false, false);
            for (Point2D d : fitFunc.getData()) {
               plotData.add(d.getX(), d.getY());
            }
            xySeries.add(plotData);
            XYSeries fittedXY = new XYSeries("f" + data_.get(index).id(), false, false);
            for (Point2D d : fitFunc.getFittedData(fitData.parms())) {
               fittedXY.add(d.getX(), d.getY());
            }
            xySeries.add(fittedXY);
         }
      }
      String title = null;
      switch(type_) {
         case BLEACH: title = "Fit of Bleach"; break;
         case PARTICLE: title = "Fit of Particle"; break;
         case PARTICLE_AND_BLEACH: title = "Fit of Particle (+Bleach)"; break;
      }
      PlotUtils pu = new PlotUtils(studio_.profile().getSettings(this.getClass()));
            pu.plotData(title, 
                    xySeries.toArray(new XYSeries[xySeries.size()]),
                    "Time (ms)",
                    "Normalized Intensity", "", 
                    1.3, 
                    WidgetSettings.COLORS, 
                    null);
   }
   
}
