/*
Copyright (c) 2019, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL REGENTS OF THE UNIVERSITY OF CALIFORNIA BE LIABLE 
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.wallerlab.illuminate;

import java.awt.Color;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;

/**
 * @author zack
 */
public final class IlluminateInterface {

   // TODO : get these from json file
   String cmdBf = "bf";
   String cmdDf = "df";
   String cmdDpc = "dpc.t";
   String cmdFill = "f";
   String cmdClear = "x";
   String cmdColor = "sc";

   String ledValDelimitor = "-";
   private int commandWaitMs = 10;

   private CMMCore mmCore;
   private String deviceName;

   // Get LED Count
   public int ledCount = 0;
   public int triggerInputCount = 0;
   public int triggerOutputCount = 0;
   public int colorChannelCount = 0;
   public int partNumber = 0;
   public int serialNumber = 0;
   public String macAddress = "";
   public int bitDepth = 0;
   public String type = "";
   public double[][] ledPositionList;

   public IlluminateInterface(CMMCore mmCore, String deviceName) throws Exception {
      this.mmCore = mmCore;
      this.deviceName = deviceName;

      try {
         colorChannelCount =
               Integer.parseInt(mmCore.getProperty(deviceName, "ColorChannelCount"));
         ledCount = Integer.parseInt(mmCore.getProperty(deviceName, "LedCount"));
         triggerInputCount =
               Integer.parseInt(mmCore.getProperty(deviceName, "TriggerInputCount"));
         triggerOutputCount =
               Integer.parseInt(mmCore.getProperty(deviceName, "TriggerOutputCount"));
         partNumber = Integer.parseInt(mmCore.getProperty(deviceName, "PartNumber"));
         serialNumber = Integer.parseInt(mmCore.getProperty(deviceName, "SerialNumber"));
         macAddress = mmCore.getProperty(deviceName, "MacAddress");
         bitDepth = Integer.parseInt(mmCore.getProperty(deviceName, "NativeBitDepth"));
         type = mmCore.getProperty(deviceName, "Type");
      } catch (Exception ex) {
         Logger.getLogger(IlluminatePlugin.class.getName()).log(Level.SEVERE, null, ex);
      }
      System.out.println("Finished pulling device parameters.");

      // Read LED positions
      this.readLedPositions();
   }

   public void drawDpc(String orientation) throws Exception {
      mmCore.setProperty(deviceName, "IlluminationPatternOrientation", orientation);
      mmCore.setProperty(deviceName, "IlluminationPattern", "DPC");
   }

   public void drawBrightfield() throws Exception {
      mmCore.setProperty(deviceName, "IlluminationPattern", "Brightfield");
   }

   public void drawDarkfield() throws Exception {
      mmCore.setProperty(deviceName, "IlluminationPattern", "Annulus");
   }

   public void drawCenter() throws Exception {
      mmCore.setProperty(deviceName, "IlluminationPattern", "Center LED");
   }

   public void drawColorDpc() throws Exception {
      mmCore.setProperty(deviceName, "IlluminationPattern", "Color DPC");
   }

   public void drawColorDarkfield() throws Exception {
      mmCore.setProperty(deviceName, "IlluminationPattern", "Color Darkfield");
   }

   public void drawLedList(List<Integer> ledList) throws Exception {
      // Set LED indicies
      String ledIndicies = "";
      for (int idx = 0; idx < ledList.size(); idx++) {
         // Append LED Index
         ledIndicies = ledIndicies + ledList.get(idx).toString();

         if (idx < ledList.size() - 1) {
            ledIndicies = ledIndicies + '.';
         }
      }

      // Set LED indicies property
      mmCore.setProperty(deviceName, "ManualLedList", ledIndicies);

      // Set command
      mmCore.setProperty(deviceName, "IlluminationPattern", "Manual LED Indicies");
   }

   public void fillArray() throws Exception {
      mmCore.setProperty(deviceName, "IlluminationPattern", "Fill");
   }

   public void clearArray() throws Exception {
      mmCore.setProperty(deviceName, "IlluminationPattern", "Clear");
   }

   public void setColor(Color myColor) throws Exception {
      // Get Colors
      int colorR = myColor.getRed();
      int colorG = myColor.getGreen();
      int colorB = myColor.getBlue();

      System.out.println("Color count is: ");
      System.out.println(this.colorChannelCount);

      if (this.colorChannelCount == 1) {
         int brightness = (int) (colorR + colorG + colorB) / 3;
         mmCore.setProperty(deviceName, "Brightness", brightness);
      } else {
         // Store Intensity
         mmCore.setProperty(deviceName, "ColorBalanceRed", colorR);
         mmCore.setProperty(deviceName, "ColorBalanceGreen", colorG);
         mmCore.setProperty(deviceName, "ColorBalanceBlue", colorB);
      }
   }

   public double getNa() throws Exception {
      return (double) Math.round(
            Float.parseFloat(mmCore.getProperty(deviceName, "NumericalAperture")) * 100d) / 100d;
   }

   public void setNa(double newNa) throws Exception {
      System.out.println(newNa);
      mmCore.setProperty(deviceName, "NumericalAperture", String.valueOf(newNa));
   }

   public float getArrayDistance() throws Exception {
      return Float.parseFloat(mmCore.getProperty(deviceName, "ArrayDistanceFromSample"));
   }

   public void setArrayDistance(float newArrayDistance) throws Exception {
      mmCore.setProperty(deviceName, "ArrayDistanceFromSample", String.valueOf(newArrayDistance));
   }

   public void sendCommand(String command) throws Exception {
      mmCore.setProperty(deviceName, "SerialCommand", command);
   }

   public void setBrightness(int brightness) throws Exception {
      mmCore.setProperty(deviceName, "Brightness", brightness);
   }

   /**
    * @throws Exception
    */
   public void readLedPositions() throws Exception {

      // Initialize array
      ledPositionList = new double[ledCount][2];
      for (int i = 0; i < ledCount; i++) {
         ledPositionList[i][0] = 0.0;
         ledPositionList[i][1] = 0.0;
      }
      int ledPositionsRead = 0;
      int ledBatchSize = 20;
      int ledBatchCount = (int) Math.ceil((double) ledCount / (double) ledBatchSize);
      JSONObject positionsJson;
      JSONArray ledJson;
      String command;
      for (int batchIndex = 0; batchIndex < ledBatchCount; batchIndex++) {
         // Calculate LED Indicies
         int ledStart = batchIndex * ledBatchSize;
         final int ledEnd = Math.min((batchIndex + 1) * ledBatchSize, ledCount - 1);

         // Send command
         command = "pledposna.";
         command += String.valueOf(ledStart);
         command += ".";
         command += String.valueOf(ledEnd);
         mmCore.setProperty(deviceName, "SerialCommand", command);

         // Seralize JSON
         positionsJson =
               new JSONObject(mmCore.getProperty(deviceName, "SerialResponse")).getJSONObject(
                     "led_position_list_na");

         for (int ledIndex = ledStart; ledIndex < ledEnd; ledIndex++) {
            ledJson = positionsJson.getJSONArray(String.valueOf(ledIndex));
            ledPositionList[ledIndex][0] = ledJson.getDouble(0);
            ledPositionList[ledIndex][1] = ledJson.getDouble(1);
         }
         System.out.println(
               "Parsed " + String.valueOf(batchIndex) + " of " + String.valueOf(ledBatchCount)
                     + " batches of LED positions.");
      }
   }


   public int[][] getLedIntensities() throws Exception {
      // Initialize array
      int[][] ledValues = new int[ledCount][3];

      int ledBatchSize = 20;
      int ledBatchCount = (int) Math.ceil((double) ledCount / (double) ledBatchSize);
      JSONObject positionsJson;
      JSONArray ledJson;
      String command;

      for (int batchIndex = 0; batchIndex < ledBatchCount; batchIndex++) {
         // Calculate LED Indicies
         int ledStart = batchIndex * ledBatchSize;
         final int ledEnd = Math.min((batchIndex + 1) * ledBatchSize, ledCount - 1);

         // Send command
         command = "pvals.";
         command += String.valueOf(ledStart);
         command += ".";
         command += String.valueOf(ledEnd);
         mmCore.setProperty(deviceName, "SerialCommand", command);

         // Seralize JSON
         positionsJson =
               new JSONObject(mmCore.getProperty(deviceName, "SerialResponse")).getJSONObject(
                     "ledValues");

         for (int ledIndex = ledStart; ledIndex < ledEnd; ledIndex++) {
            ledJson = positionsJson.getJSONArray(String.valueOf(ledIndex));
            for (int colorChannelIndex = 0; colorChannelIndex < this.colorChannelCount;
                  colorChannelIndex++) {
               ledValues[ledIndex][colorChannelIndex] = ledJson.getInt(colorChannelIndex);
            }
         }
         System.out.println(
               "Parsed " + String.valueOf(batchIndex) + " of " + String.valueOf(ledBatchCount)
                     + " batches of LEDs.");
      }

      return ledValues;
   }
}
