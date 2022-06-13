///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
//               Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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

package org.micromanager;


import java.awt.geom.AffineTransform;


/**
 * Legacy interface that implements some methods from the old 1.4 API.
 * Available in the Beanshell scripting interface as "mm.compat()" or
 * "mm.getCompatibilityInterface()".
 */
public interface CompatibilityInterface {
   /**
    * Displays an error message and returns true if the run-time Micro-Manager
    * version is less than the one specified.
    * Versions in Micro-Manager are of the format:
    * major.minor.minute date
    * where ' date' can be omitted
    * Examples:
    * 1.4.6
    * 1.4.6 20110831
    * When a date is appended to a version number, it will be newer than the
    * same version without a date
    *
    * @param version - minimum version needen to run this code
    * @return true if the run-time Micro-Manager version is less than the
    *     one specified
    * @throws NumberFormatException if the version number is not in the format
    *                               expected.
    */
   boolean versionLessThan(String version) throws NumberFormatException;

   /**
    * Return the version of the running instance of Micro-Manager.
    *
    * @return the currently running Micro-Manager version
    */
   String getVersion();

   /**
    * Retrieve the affine transform describing how the camera image maps to
    * stage coordinates, for a given pixel size config. This method will pull
    * information from the profile if available, and failing that will look in
    * the Java Preferences. Will return null if no transform is found.
    *
    * @param config The configuration (per core.getCurrentPixelSizeConfig())
    *               to find the affine transform for.
    * @return The transform describing how the camera maps to the stage.
    * @deprecated - Use core.getPixelSizeAffineByID(config) instead
    */
   @Deprecated
   AffineTransform getCameraTransform(String config);

   /**
    * Set a new affine transform for describing how the camera image maps to
    * the stage coordinates. The value will be stored in the user's profile.
    *
    * @param transform The new transform to use.
    * @param config    The configuration (per core.getCurrentPixelSizeConfig())
    *                  to set the affine transform for.
    * @deprecated - Use core.setPixelSizeAffine(config, DoubleVector transform)
    *     instead
    */
   @Deprecated
   void setCameraTransform(AffineTransform transform, String config);
}
