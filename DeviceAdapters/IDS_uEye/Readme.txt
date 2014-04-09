IDS uEye camera driver for Micro-Manager

DESCRIPTION:   Driver for IDS uEye series of USB cameras
               (also Thorlabs DCUxxxx USB, Edmund EO-xxxxM USB
                with IDS hardware).

               Based on IDS uEye SDK and Micromanager DemoCamera example.
               Tested with SDK version 4.30.

               This driver was developed within a project to enable
               laser beam profiling with ImageJ.
               The project is located at
               http://sourceforge.net/projects/beamprofiler/
                
AUTHOR:        Wenjamin Rosenfeld

YEAR:          2012 - 2014
                
VERSION:       1.3

LICENSE:       This software is distributed under the BSD license.
               License text is included with the source distribution.

               This software is distributed in the hope that it will be useful,
               but WITHOUT ANY WARRANTY; without even the implied warranty
               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.




Supported features:

 - Monochrome and color cameras. The available modes/bit depths can be selected in the properties browser ("Pixel Type").

 - Gain.

 - Binning.

 - ROI.

 - External/internal triggerring.


Runtime requirements:

Linux/Windows:
 - The IDS camera driver package must be installed (version 4.30 or higher).
   Follow the installation instructions of the manufacturer (http://ids-imaging.com).



Compiling requirements:

Linux:
 - The IDS SDK components are part of the IDS Software Suite and are automatically installed with the drivers.


Windows: 
 - Install the IDS Software Suite with the SDK
 - The development files are in ...\IDS\uEye\Develop within the installation directory
   For convenience this directory tree should be put into 3rdpartypublic



Notes:

 - For color, currently only the modes "BGRA8" and "RGBA8" are supported.
   The "Frame rate" property will not necessarily affect the real frame rate in the live-mode for every camera model.
   It does, however, determine the maximal exposure time.

 - Gamma correction is deactivated.

 - If several cameras are connected, currently only the first camera in the list will be addressed.

 - With Linux and OpenJDK, using the color modes may cause problems (monochrome modes work), with Sun/Oracle JDK all modes work. 
