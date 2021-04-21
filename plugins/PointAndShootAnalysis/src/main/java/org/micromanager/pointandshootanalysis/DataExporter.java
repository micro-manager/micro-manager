package org.micromanager.pointandshootanalysis;

import java.awt.Color;
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
import org.ddogleg.optimization.OptimizationException;
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
import static org.micromanager.pointandshootanalysis.utils.ListUtils.xAvgLastN;

/** @author Nico */
public class DataExporter {
  public enum Type {
    BLEACH,
    PARTICLE,
    PARTICLE_AND_BLEACH
  }

  private final Type type_;
  private final Studio studio_;
  private final List<PASData> data_;
  private final Map<Integer, Instant> frameTimeStamps_;

  private static final Integer MSLIMIT = 2000;

  public DataExporter(
      Studio studio, List<PASData> data, Map<Integer, Instant> frameTimeStamps, Type type) {
    studio_ = studio;
    data_ = data;
    frameTimeStamps_ = frameTimeStamps;
    type_ = type;
  }

  /**
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
    int startFrame = d.framePasClicked();
    double startIntensity = Double.MAX_VALUE;
    // search for the frame with the minimum intensity in the n frames
    // after the bleach was reported.  Note that n is hard coded for now
    // n should be expressed in seconds (or ms) and be provided by the user
    final int n = 50;
    for (int frame = d.framePasClicked();
        frame < d.framePasClicked() + n && frame < frameTimeStamps_.size();
        frame++) {
      if (d.particleDataTrack().get(frame) != null) {
        Double intensity = null;
        switch (type) {
          case BLEACH:
            intensity = d.particleDataTrack().get(frame).getNormalizedBleachMaskAvg();
            break;
          case PARTICLE:
            intensity = d.particleDataTrack().get(frame).getNormalizedMaskAvg();
            break;
          case PARTICLE_AND_BLEACH:
            intensity = d.particleDataTrack().get(frame).getNormalizedMaskIncludingBleachAvg();
        }
        if (intensity != null && intensity < startIntensity) {
          startIntensity = intensity;
          startFrame = frame;
        }
      }
    }

    List<Point2D> dataAsList = new ArrayList<>();
    for (int frame = startFrame; frame < frameTimeStamps_.size(); frame++) {
      Double intensity = null;
      if (d.particleDataTrack().get(frame) != null) {
        switch (type) {
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
          double ms =
              frameTimeStamps_.get(frame).toEpochMilli()
                  - frameTimeStamps_.get(d.framePasClicked()).toEpochMilli();
          if (msLimit == null || ms < msLimit) {
            if (Double.isInfinite(ms)) System.out.println("ms is infinte");
            if (Double.isInfinite(intensity)) System.out.println("intensity is infinite");
            dataAsList.add(new Point2D.Double(ms, intensity));
          }
        }
      }
    }
    if (dataAsList.size() < 3) {
      return null;
    }
    double endIntensityEstimate = xAvgLastN(dataAsList, dataAsList.size() / 10);
    PASFunction func = null;
    if (fitFunction == SingleExpRecoveryFunc.class) {
      func = new SingleExpRecoveryFunc(dataAsList);
    } else if (fitFunction == LinearFunc.class) {
      func = new LinearFunc(dataAsList);
    }
    if (func == null) {
      return null;
    }
    UnconstrainedLeastSquares<DMatrixRMaj> optimizer =
        FactoryOptimization.levenbergMarquardt(null, true);
    optimizer.setFunction(func, null);
    // optimizer.setVerbose(System.out,0);
    double startTimeEstimate =
        frameTimeStamps_.get(startFrame).toEpochMilli()
            - frameTimeStamps_.get(d.framePasClicked()).toEpochMilli();
    if (fitFunction == SingleExpRecoveryFunc.class) {
      optimizer.initialize(
          new double[] {endIntensityEstimate, startTimeEstimate, 0.0005}, 1e-12, 1e-12);
    } else if (fitFunction == LinearFunc.class) {
      optimizer.initialize(new double[] {dataAsList.get(0).getY(), 0.001}, 1e-12, 1e-12);
    }
    UtilOptimize.process(optimizer, 50);
    double[] found = optimizer.getParameters();
    double rSquared = func.getRSquared(found);
    double yAtStart =
        func.calculate(
            found,
            frameTimeStamps_.get(startFrame).toEpochMilli()
                - frameTimeStamps_.get(d.framePasClicked()).toEpochMilli());
    double tAtStart =
        frameTimeStamps_.get(startFrame).toEpochMilli()
            - frameTimeStamps_.get(d.framePasClicked()).toEpochMilli();
    double yHalf = (found[0] - yAtStart) / 2.0 + yAtStart;
    double tHalf = func.calculateX(found, yHalf) - tAtStart;
    func.setParms(found);
    /*
    System.out.println("A: " + found[0] + ", b: " + found[1]);
    if (func.getParms().length > 2) {
       System.out.println(", k: " + found[2]);
    }
    System.out.println("RSquared: " + rSquared + ", t1/2: " + tHalf + " ms");
    */
    FitData fitData = new FitData(dataAsList, func.getClass(), type_, found, rSquared, tHalf);

    return fitData;
  }

  public void exportRaw(List<Integer> indices) {
    StringBuilder export = new StringBuilder();
    for (int index : indices) {
      PASData d = data_.get(index);
      if (d != null) {
        export.append(d.dataSetName()).append("\t\n");
        export.append(d.id()).append("\t\n");
        switch (type_) {
          case BLEACH:
            export.append("type: bleach\t\n");
            break;
          case PARTICLE:
            export.append("type: particle\t\n");
            break;
          case PARTICLE_AND_BLEACH:
            export.append("type: particle plus bleach\t\n");
            break;
        }
        export.append("ms\tintensity\n");
        Set<Map.Entry<Integer, ParticleData>> entrySet = d.particleDataTrack().entrySet();
        for (Entry<Integer, ParticleData> es : entrySet) {
          double ts =
              frameTimeStamps_.get(es.getKey()).toEpochMilli()
                  - frameTimeStamps_.get(d.framePasClicked()).toEpochMilli();
          Double val = null;
          if (es.getValue() != null) {
            switch (type_) {
              case BLEACH:
                val = es.getValue().getNormalizedBleachMaskAvg();
                break;
              case PARTICLE:
                val = es.getValue().getNormalizedMaskAvg();
                break;
              case PARTICLE_AND_BLEACH:
                val = es.getValue().getNormalizedMaskIncludingBleachAvg();
                break;
            }
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
    export.append("Bleach Spot yMax\tBleach Spot t1/2\tBleach Spot rSq\t");
    export.append("Particle k\tParticle y0\n");
    for (int index : indices) {
      PASData d = data_.get(index);
      if (d != null) {
        try {
          export.append(d.dataSetName()).append("\t").append(d.id()).append("\t");
          double avgPSize = TrackInfoCalculator.avgParticleSize(d);
          export.append(avgPSize).append("\t");
          FitData fitData = fit(index, Type.BLEACH, SingleExpRecoveryFunc.class, null);
          export.append(fitData.parms()[2]).append("\t"); // k
          export.append(fitData.parms()[0]).append("\t"); // A
          export.append(fitData.tHalf()).append("\t");
          export.append(fitData.rSquared()).append("\t");
          FitData particleFitData = fit(index, Type.PARTICLE_AND_BLEACH, LinearFunc.class, MSLIMIT);
          export.append(particleFitData.parms()[1]).append("\t"); // particle k
          export.append(particleFitData.parms()[0]).append("\n"); // particle y0:w

        } catch (java.lang.RuntimeException re) {
          export.append("Export Failure!\n");
        }
      }
    }
    StringSelection selection = new StringSelection(export.toString());
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(selection, selection);
  }

  public void plotFits(List<Integer> indices) {
    List<XYSeries> xySeries = new ArrayList<>(2 * indices.size());
    List<Integer> succesfullFits = new ArrayList<>();
    for (int index : indices) {
      Class fitClass = SingleExpRecoveryFunc.class;
      Integer msLimit = null;
      if (type_ == Type.PARTICLE_AND_BLEACH) {
        fitClass = LinearFunc.class;
        msLimit = MSLIMIT;
      }
      try {
        FitData fitData = fit(index, type_, fitClass, msLimit);
        // TODO: check which functions was used to fit and do the right one
        if (fitData != null) {
          PASFunction fitFunc = null;
          if (fitClass == SingleExpRecoveryFunc.class) {
            fitFunc = new SingleExpRecoveryFunc(fitData.data());
          } else if (fitClass == LinearFunc.class) {
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
            succesfullFits.add(index);
          }
        }
      } catch (OptimizationException oe) {
        String msg = "Fit failed for dataseries: " + data_.get(index).id();
        studio_.alerts().postAlert("Fit error", this.getClass(), msg);
        studio_.logs().logError(msg);
      }
    }
    String title = null;
    switch (type_) {
      case BLEACH:
        title = "Fit of Bleach";
        break;
      case PARTICLE:
        title = "Fit of Particle";
        break;
      case PARTICLE_AND_BLEACH:
        title = "Fit of Particle (+Bleach)";
        break;
    }
    PlotUtils pu = new PlotUtils(studio_.profile().getSettings(this.getClass()));
    List<Color> colorList = new ArrayList<>();
    for (Integer index : succesfullFits) {
      colorList.add(WidgetSettings.COLORS[index % WidgetSettings.COLORS.length]);
      colorList.add(WidgetSettings.COLORS[index % WidgetSettings.COLORS.length]);
    }
    pu.plotData(
        title,
        xySeries.toArray(new XYSeries[xySeries.size()]),
        "Time (ms)",
        "Normalized Intensity",
        "",
        1.3,
        colorList.toArray(new Color[colorList.size()]),
        null);
  }
}
