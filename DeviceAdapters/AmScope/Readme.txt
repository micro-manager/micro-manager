AmScope camera driver for Micro-Manager

 DESCRIPTION:   Driver for AmScope MU series of USB 3 cameras
.

               Based on AmScope SDK and Micromanager DemoCamera example.
 
AUTHOR:        MaheshKumar Reddy Kakuturu 

YEAR:          2016
                
VERSION: 1.0      LICENSE:       This software is distributed under the BSD license.
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

 


Runtime requirements:

Windows:
 - The AmScope camera driver package must be installed (TWAIN).
   Follow the installation instructions of the manufacturer (http://www.amscope.com).



Compiling requirements:


Windows: 
 - Install the AmScope Software Suite with the SDK
 - The development files are in micro-manager1.4\AmScope within the installation directory. Additional includes are at 
\AmScope\includes and \AmScope\libs  For convenience this directory tree should be put into 3rdparty
public



Notes:

 - For color, currently only the modes "RGB32" and "GREY8" are supported.
   The "Frame rate" property will not necessarily affect the real frame rate in the live-mode for every camera model.
   It does, however, determine the maximal exposure time.

 - Gamma correction is deactivated.

 - If several cameras are connected, currently only the first camera in the list will be addressed.

