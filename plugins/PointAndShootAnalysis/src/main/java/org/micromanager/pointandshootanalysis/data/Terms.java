///////////////////////////////////////////////////////////////////////////////
//FILE:          PTerms.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     PointAndShoot Analysis plugin
//-----------------------------------------------------------------------------
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

/**
 *
 * @author nico
 */
public class Terms {
   public final static String LOCATIONSFILENAME = "LocationFileName";
   public final static String[] FILESUFFIXES = {"txt", "tproj", "log"};
   public final static String RADIUS = "Radius";
   public final static String CORERECEIVEDTIMEKEY = "TimeReceivedByCore";
   public final static String NRFRAMESBEFORE = "NrFramesBefore";
   public final static String NRFRAMESAFTER = "NrFramesAfter";  
   public final static String MAXDISTANCE = "MaxDistance";
   public final static String CAMERAOFFSET = "CameraOffset";   
   public final static String FIXBLEACHINPARTICLE = "FixBleachInParticle";
   public final static String NRFRAMESTOFIXBLEACH = "nrFramesToFixBleach";
}
