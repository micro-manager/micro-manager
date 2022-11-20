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
   public static final String LOCATIONSFILENAME = "LocationFileName";
   public static final String[] FILESUFFIXES = {"txt", "tproj", "log"};
   public static final String RADIUS = "Radius";
   public static final String CORERECEIVEDTIMEKEY = "TimeReceivedByCore";
   public static final String NRFRAMESBEFORE = "NrFramesBefore";
   public static final String NRFRAMESAFTER = "NrFramesAfter";
   public static final String MAXDISTANCE = "MaxDistance";
   public static final String CAMERAOFFSET = "CameraOffset";
   public static final String FIXBLEACHINPARTICLE = "FixBleachInParticle";
   public static final String NRFRAMESTOFIXBLEACH = "nrFramesToFixBleach";
}
