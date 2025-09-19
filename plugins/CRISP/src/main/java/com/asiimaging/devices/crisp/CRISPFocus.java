/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2024, Applied Scientific Instrumentation
 */

package com.asiimaging.devices.crisp;

import com.asiimaging.crisp.plot.FocusData;
import com.asiimaging.crisp.plot.FocusDataSet;
import com.asiimaging.devices.zstage.ZStage;

/**
 * A helper class to generate a focus curve in software using CRISP and ZStage for
 * Tiger controllers.
 */
public class CRISPFocus {

   public static FocusDataSet getFocusCurveData(final CRISP crisp, final ZStage zStage) {
      final FocusDataSet data = new FocusDataSet();
      crisp.setOnlySendSerialCommandOnChange(false);

      crisp.sendSerialCommand("C Z?");
      final String[] rawCounts = crisp.getSerialResponse().split("=");
      final double countsMM = Double.parseDouble(rawCounts[1].split(" ")[0]);
      //System.out.println("countsMM = " + countsMM);

      // TODO: use "Calibration Range" property?
      crisp.sendSerialCommand("LR F?");
      final String[] rawCalRange = crisp.getSerialResponse().split("=");
      final double calRange = Double.parseDouble(rawCalRange[1]);
      //System.out.println("calRange = " + calRange);

      final int numSamples = 50;
      final double totalMoveRange = Math.round((calRange * countsMM) / 10.0);
      final double moveStep = totalMoveRange / numSamples;
      //System.out.println("totalMoveRange = " + totalMoveRange);

      // come back to this position after gathering data
      final double originalPosition = zStage.getPosition();

      // turn off backlash move
      final double backlash = zStage.getBacklash();
      zStage.setBacklash(0.0f);

      // move to initial position
      zStage.setRelativePosition(totalMoveRange * 0.5);
      zStage.waitForDevice();

      for (int i = 0; i < numSamples; i++) {
         final double position = zStage.getPosition();
         final double error = Double.parseDouble(crisp.getDitherError());
         zStage.setRelativePosition(-moveStep);
         zStage.waitForDevice();
         data.add(new FocusData(i, position, error));
         //System.out.println(position + " " + error);
      }

      // restore backlash move and position
      zStage.setPosition(originalPosition);
      zStage.setBacklash(backlash);
      return data;
   }

}
