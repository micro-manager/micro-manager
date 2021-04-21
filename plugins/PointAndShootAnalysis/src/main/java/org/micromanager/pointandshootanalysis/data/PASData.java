///////////////////////////////////////////////////////////////////////////////
// FILE:          PASData.java
// PROJECT:       PointAndShootAnalysis
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2018
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

package org.micromanager.pointandshootanalysis.data;

import java.awt.Point;
import java.time.Instant;
import java.util.Map;

/**
 * Note PAS is short for "Point And Shoot"
 *
 * @author nico
 */
public class PASData {
  private final String dataSetName_;
  private final String id_;
  private final Instant pasClicked_;
  private final Instant tsOfFrameBeforePas_;
  private final int framePasClicked_;
  private final Point pasIntended_;
  private final Point pasActual_;
  private final int[] pasFrames_;
  private final Map<Integer, ParticleData> particleDataTrack_;
  private final FitData fitData_;

  public static class Builder {

    private String dataSetName_;
    private String id_;
    private Instant pasClicked_;
    private Instant tsOfFrameBeforePas_;
    private int framePasClicked_;
    private Point pasIntended_;
    private Point pasActual_;
    private int[] pasFrames_;
    private Map<Integer, ParticleData> particleDataTrack_;
    private FitData fitData_;

    private Builder copy(
        String dataSetName,
        String id,
        Instant pasClicked,
        Instant tsOfFrameBeforePas,
        int framePasClicked,
        Point pasIntended,
        Point pasActual,
        int[] pasFrames,
        Map<Integer, ParticleData> particleData,
        FitData fitData) {
      dataSetName_ = dataSetName;
      id_ = id;
      pasClicked_ = pasClicked;
      tsOfFrameBeforePas_ = tsOfFrameBeforePas;
      framePasClicked_ = framePasClicked;
      pasIntended_ = pasIntended;
      pasActual_ = pasActual;
      pasFrames_ = pasFrames;
      particleDataTrack_ = particleData;
      fitData_ = fitData;
      return this;
    }

    public Builder dataSetName(String n) {
      dataSetName_ = n;
      return this;
    }

    public Builder id(String id) {
      id_ = id;
      return this;
    }

    public Builder pasClicked(Instant inst) {
      pasClicked_ = inst;
      return this;
    }

    public Builder tsOfFrameBeforePas(Instant inst) {
      tsOfFrameBeforePas_ = inst;
      return this;
    }

    public Builder framePasClicked(int f) {
      framePasClicked_ = f;
      return this;
    }

    public Builder pasIntended(Point p) {
      pasIntended_ = p;
      return this;
    }

    public Builder pasActual(Point p) {
      pasActual_ = p;
      return this;
    }

    public Builder pasFrames(int[] pasFrames) {
      pasFrames_ = pasFrames;
      return this;
    }

    public Builder particleDataTrack(Map<Integer, ParticleData> particleDataTrack) {
      particleDataTrack_ = particleDataTrack;
      return this;
    }

    public Builder fitData(FitData fitData) {
      fitData_ = fitData;
      return this;
    }

    public PASData build() {
      return new PASData(
          dataSetName_,
          id_,
          pasClicked_,
          tsOfFrameBeforePas_,
          framePasClicked_,
          pasIntended_,
          pasActual_,
          pasFrames_,
          particleDataTrack_,
          fitData_);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * PAS is short for "Point And Shoot"
   *
   * @param pasClicked Instant when PAS was Clicked (as recorded by the Projector plugin
   * @param tsOfFrameBeforePas Instant of frame during which PAS was clicked
   * @param framePasClicked frame number when point and shoot was clicked 0-based (as in MM, unlike
   *     ImageJ)
   * @param pasIntended X Y coordinates (in pixels) where Point And Shoot was aimed
   * @param pasActual X Y coordinates (in pixels where Point And Shoot actually happened
   * @param pasFrames Frames during which the bleach laser was on 0-based (as in MM, unlike ImageJ)
   */
  private PASData(
      String dataSetName,
      String id,
      Instant pasClicked,
      Instant tsOfFrameBeforePas,
      int framePasClicked,
      Point pasIntended,
      Point pasActual,
      int[] pasFrames,
      Map<Integer, ParticleData> particleDataTrack,
      FitData fitData) {
    dataSetName_ = dataSetName;
    id_ = id;
    pasClicked_ = pasClicked;
    tsOfFrameBeforePas_ = tsOfFrameBeforePas;
    framePasClicked_ = framePasClicked;
    pasIntended_ = pasIntended;
    pasActual_ = pasActual;
    pasFrames_ = pasFrames;
    particleDataTrack_ = particleDataTrack;
    fitData_ = fitData;
  }

  public String dataSetName() {
    return dataSetName_;
  }

  public String id() {
    return id_;
  }

  public Instant pasClicked() {
    return pasClicked_;
  }

  public Instant tsOfFrameBeforePas() {
    return tsOfFrameBeforePas_;
  }

  public int framePasClicked() {
    return framePasClicked_;
  }

  public Point pasIntended() {
    return pasIntended_;
  }

  public Point pasActual() {
    return pasActual_;
  }

  public int[] pasFrames() {
    return pasFrames_;
  }

  public Map<Integer, ParticleData> particleDataTrack() {
    return particleDataTrack_;
  }

  public FitData fitData() {
    return fitData_;
  }

  public void normalizeBleachSpotIntensities(
      final int findMinFramesBefore,
      final int cameraOffset,
      final Map<Integer, Double> controlAvgIntensity) {
    if (particleDataTrack() != null && id() != null) {
      double preSum = 0.0;
      int count = 0;
      for (int frame = framePasClicked() - findMinFramesBefore;
          frame <= framePasClicked();
          frame++) {
        if (particleDataTrack().get(frame) != null
            && particleDataTrack().get(frame).getMaskAvg() != null) {
          double value = particleDataTrack().get(frame).getMaskAvg() - cameraOffset;
          double normalizedValue = value / controlAvgIntensity.get(frame);
          preSum += normalizedValue;
          count++;
        }
      }
      if (count > 0.0 && preSum > 0.0) { // prevent divisions by zero
        double preBleachAvg = (preSum / count);
        for (int frame = 0; frame < particleDataTrack().size(); frame++) {
          if (particleDataTrack().get(frame) != null
              && particleDataTrack().get(frame).getBleachMaskAvg() != null) {
            double value = particleDataTrack().get(frame).getBleachMaskAvg() - cameraOffset;
            double normalizedValue = value / controlAvgIntensity.get(frame);
            particleDataTrack()
                .get(frame)
                .setNormalizedBleachMaskAvg(normalizedValue / preBleachAvg);
          }
        }
      }
    }
  }

  public void normalizeParticleIncludingBleachIntensities(
      final int findMinFramesBefore,
      final int cameraOffset,
      final Map<Integer, Double> controlAvgIntensity) {
    if (particleDataTrack() == null) {
      System.out.println("Particle track: " + id() + "had no data to be normalized");
      return;
    }
    double preSum = 0.0;
    int count = 0;

    for (int frame = framePasClicked() - findMinFramesBefore; frame <= framePasClicked(); frame++) {
      if (particleDataTrack().get(frame) != null
          && particleDataTrack().get(frame).getMaskIncludingBleachAvg() != null) {
        double value = particleDataTrack().get(frame).getMaskIncludingBleachAvg() - cameraOffset;
        double normalizedValue = value / controlAvgIntensity.get(frame);
        preSum += normalizedValue;
        count++;
      }
    }
    double preBleachAvg = (preSum / count);
    for (int frame = 0; frame < particleDataTrack().size(); frame++) {
      if (particleDataTrack().get(frame) != null
          && particleDataTrack().get(frame).getMaskIncludingBleachAvg() != null) {
        double value = particleDataTrack().get(frame).getMaskIncludingBleachAvg() - cameraOffset;
        double normalizedValue = value / controlAvgIntensity.get(frame);
        particleDataTrack()
            .get(frame)
            .setNormalizedMaskIncludingBleachAvg(normalizedValue / preBleachAvg);
      }
    }
  }

  public Builder copyBuilder() {
    Builder b = new Builder();
    b.copy(
        dataSetName_,
        id_,
        pasClicked_,
        tsOfFrameBeforePas_,
        framePasClicked_,
        pasIntended_,
        pasActual_,
        pasFrames_,
        particleDataTrack_,
        fitData_);
    return b;
  }
}
