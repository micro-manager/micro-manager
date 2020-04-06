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
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;

/**
 *
 * @author zack
 */
    public final class IlluminateInterface {
        
        // TODO : get these from json file
        String CMD_BF = "bf";
        String CMD_DF = "df";
        String CMD_DPC = "dpc.t";
        String CMD_FILL = "f";
        String CMD_CLEAR = "x";
        String CMD_COLOR = "sc";
    
        String ledValDelimitor = "-";
        private int command_wait_ms = 10;

        private CMMCore mmCore;
        private String deviceName;
        
        // Get LED Count
        public int led_count = 0;
        public int trigger_input_count = 0;
        public int trigger_output_count = 0;
        public int color_channel_count = 0;
        public int part_number = 0;
        public int serial_number = 0;
        public String mac_address = "";
        public int bit_depth = 0;
        public String type = "";
        public double [][] led_position_list;
        
        public IlluminateInterface(CMMCore mmCore, String deviceName) throws Exception {
            this.mmCore = mmCore;
            this.deviceName = deviceName;
            
            try {
                color_channel_count = Integer.parseInt(mmCore.getProperty(deviceName, "ColorChannelCount"));
                led_count = Integer.parseInt(mmCore.getProperty(deviceName, "LedCount"));
                trigger_input_count = Integer.parseInt(mmCore.getProperty(deviceName, "TriggerInputCount"));
                trigger_output_count = Integer.parseInt(mmCore.getProperty(deviceName, "TriggerOutputCount"));
                part_number = Integer.parseInt(mmCore.getProperty(deviceName, "PartNumber"));
                serial_number = Integer.parseInt(mmCore.getProperty(deviceName, "SerialNumber"));
                mac_address = mmCore.getProperty(deviceName, "MacAddress");
                bit_depth = Integer.parseInt(mmCore.getProperty(deviceName, "NativeBitDepth"));
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
            String led_indicies ="";
            for (int idx=0; idx<ledList.size(); idx++)
            {   
                // Append LED Index
                led_indicies = led_indicies + ledList.get(idx).toString();
                
                if (idx < ledList.size() - 1)
                    led_indicies = led_indicies + '.';
            }
            
            // Set LED indicies property
            mmCore.setProperty(deviceName, "ManualLedList", led_indicies);
            
            // Set command
            mmCore.setProperty(deviceName, "IlluminationPattern", "Manual LED Indicies");
        }

        public void fillArray() throws Exception {
            mmCore.setProperty(deviceName, "IlluminationPattern", "Fill");
        }

        public void clearArray() throws Exception {
            mmCore.setProperty(deviceName,"IlluminationPattern", "Clear");
        }
        
        public void setColor(Color myColor) throws Exception
        {
            // Get Colors
            int colorR = myColor.getRed();
            int colorG = myColor.getGreen();
            int colorB = myColor.getBlue();
            
            System.out.println("Color count is: ");
            System.out.println(this.color_channel_count);
            
            if (this.color_channel_count == 1)
            {
                int brightness = (int)(colorR + colorG + colorB) / 3;
                mmCore.setProperty(deviceName, "Brightness", brightness);
            }
            else
            {
                // Store Intensity
                mmCore.setProperty(deviceName, "ColorBalanceRed", colorR);
                mmCore.setProperty(deviceName, "ColorBalanceGreen", colorG);
                mmCore.setProperty(deviceName, "ColorBalanceBlue", colorB);
            }
        }
        
        public double getNa() throws Exception
        {
            return (double)Math.round(Float.parseFloat(mmCore.getProperty(deviceName, "NumericalAperture")) * 100d) / 100d;
        }
        
        public void setNa(double new_na) throws Exception
        {
            System.out.println(new_na);
            mmCore.setProperty(deviceName, "NumericalAperture", String.valueOf(new_na));
        }
        
        public float getArrayDistance() throws Exception
        {
            return Float.parseFloat(mmCore.getProperty(deviceName, "ArrayDistanceFromSample"));
        }
        
        public void setArrayDistance(float new_array_distance) throws Exception
        {
            mmCore.setProperty(deviceName, "ArrayDistanceFromSample", String.valueOf(new_array_distance));
        }
        
        public void sendCommand(String command) throws Exception
        {
            mmCore.setProperty(deviceName, "SerialCommand", command);
        }
        
        public void setBrightness(int brightness) throws Exception
        {
            mmCore.setProperty(deviceName, "Brightness", brightness);
        }
        
        /**
         *
         * @throws Exception
         */
        public void readLedPositions() throws Exception
        {

            // Initialize array
            led_position_list = new double [led_count][2];
            for (int i = 0; i < led_count; i++)
            {
                    led_position_list[i][0] = 0.0;
                    led_position_list[i][1] = 0.0;
            }
            int led_positions_read = 0;
            int led_batch_size = 20;
            int led_batch_count = (int) Math.ceil((double)led_count / (double)led_batch_size);
            JSONObject positions_json;
            JSONArray led_json;
            String command;
            for (int batch_index = 0; batch_index < led_batch_count; batch_index++)
            {   
                // Calculate LED Indicies
                int led_start = batch_index * led_batch_size;
                int led_end = Math.min((batch_index + 1) * led_batch_size, led_count - 1);
                
                // Send command
                command = "pledposna.";
                command += String.valueOf(led_start);
                command += ".";
                command += String.valueOf(led_end);          
                mmCore.setProperty(deviceName, "SerialCommand", command);

                // Seralize JSON
                positions_json = new JSONObject(mmCore.getProperty(deviceName, "SerialResponse")).getJSONObject("led_position_list_na");

                for (int led_index = led_start; led_index < led_end; led_index++) 
                {
                    led_json = positions_json.getJSONArray(String.valueOf(led_index));
                    led_position_list[led_index][0] = led_json.getDouble(0);
                    led_position_list[led_index][1] = led_json.getDouble(1);
                }
                System.out.println("Parsed " + String.valueOf(batch_index) + " of " + String.valueOf(led_batch_count) + " batches of LED positions.");
            }
        }
        
        
        public int[][] getLedIntensities() throws Exception
        {
            // Initialize array
            int [][] led_values = new int [led_count][3];

            int led_batch_size = 20;
            int led_batch_count = (int) Math.ceil((double)led_count / (double)led_batch_size);
            JSONObject positions_json;
            JSONArray led_json;
            String command;
            
            for (int batch_index = 0; batch_index < led_batch_count; batch_index++)
            {   
                // Calculate LED Indicies
                int led_start = batch_index * led_batch_size;
                int led_end = Math.min((batch_index + 1) * led_batch_size, led_count - 1);
                
                // Send command
                command = "pvals.";
                command += String.valueOf(led_start);
                command += ".";
                command += String.valueOf(led_end);          
                mmCore.setProperty(deviceName, "SerialCommand", command);

                // Seralize JSON
                positions_json = new JSONObject(mmCore.getProperty(deviceName, "SerialResponse")).getJSONObject("led_values");

                for (int led_index = led_start; led_index < led_end; led_index++) 
                {
                    led_json = positions_json.getJSONArray(String.valueOf(led_index));
                    for (int color_channel_index = 0; color_channel_index < this.color_channel_count; color_channel_index++)
                        led_values[led_index][color_channel_index] = led_json.getInt(color_channel_index);
                }
                System.out.println("Parsed " + String.valueOf(batch_index) + " of " + String.valueOf(led_batch_count) + " batches of LEDs.");
            }
           
            return led_values;
        }
    }
