IDS uEye camera driver for Micro-Manager

DESCRIPTION:   Driver for IDS uEye series of USB cameras
               (also Thorlabs DCUxxxx USB, Edmund EO-xxxxM USB
                which are produced by IDS).

               Based on IDS uEye SDK and Micromanager DemoCamera example.
               Tested with SDK version 3.82, 4.02 and 4.20.

               This driver was developed within a project to enable
               laser beam profiling with ImageJ.
               The project is located at
               http://sourceforge.net/projects/beamprofiler/
                
AUTHOR:        Wenjamin Rosenfeld

YEAR:          2012, 2013
                
VERSION:       1.0     

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

