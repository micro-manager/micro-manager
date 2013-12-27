IDS uEye camera driver for Micro-Manager

DESCRIPTION:   Driver for IDS uEye series of USB cameras
               (also Thorlabs DCUxxxx USB, Edmund EO-xxxxM USB
                with IDS hardware).

               Based on IDS uEye SDK and Micromanager DemoCamera example.
               Tested with SDK version 3.82, 4.02, 4.20 and 4.30.

               This driver was developed within a project to enable
               laser beam profiling with ImageJ.
               The project is located at
               http://sourceforge.net/projects/beamprofiler/
                
AUTHOR:        Wenjamin Rosenfeld

YEAR:          2012, 2013
                
VERSION:       1.1

LICENSE:       This software is distributed under the BSD license.
               License text is included with the source distribution.

               This software is distributed in the hope that it will be useful,
               but WITHOUT ANY WARRANTY; without even the implied warranty
               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.



Runtime requirements:

Linux/Windows:
 - The IDS camera driver package must be installed. Follow the installation
instructions of the manufacturer (http://ids-imaging.com).



Compiling requirements:

Linux:
 - The IDS SDK components are part of the IDS Software Suite and are automatically installed with the drivers.

Windows: 
 - Install the IDS Software Suite with the SDK
 - The development files are in ...\IDS\uEye\Develop within the installation directory
   For convenience this directory tree should be put into 3rdpartypublic



Notes:
 - The "frame rate" parameter is mainly for the internal live mode of the camera which is not used,
   as for micro manager the pictures are acquired one by one. It does, however, determine
   the maximal exposure time (inverse of the frame rate).
   For some camera models it will also limit the real frame rate in "live" acquisition mode of micro manager.
