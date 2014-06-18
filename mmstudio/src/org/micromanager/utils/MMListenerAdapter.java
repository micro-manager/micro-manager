///////////////////////////////////////////////////////////////////////////////
//FILE:          MMListenerAdapter.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, August 24, 2010
//
// COPYRIGHT:    University of California, San Francisco, 2010
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
//

package org.micromanager.utils;

import org.micromanager.api.MMListenerInterface;


public class MMListenerAdapter implements MMListenerInterface {
   @Override
   public void propertiesChangedAlert(){
   };
   @Override
   public void propertyChangedAlert(String device, String property, String value){
   };
   @Override
   public void configGroupChangedAlert(String groupName, String newConfig){
   };
   @Override
   public void systemConfigurationLoaded() {
   }
   @Override
   public void pixelSizeChangedAlert(double newPixelSizeUm){
   };
   @Override
   public void stagePositionChangedAlert(String deviceName, double pos){
   };
   @Override
   public void xyStagePositionChanged(String deviceName, double xPos, double yPos){
   };
   @Override
   public void exposureChanged(String cameraName, double newExposure){
   };
   @Override
   public void slmExposureChanged(String cameraName, double newExposure){
   };
}
