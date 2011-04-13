These are driver sources to use DSLR cameras in micro-manager.
Information about micro-manager is available from http://www.micro-manager.org

The micro-manager driver consists of two parts:
 - CCameraFrontend, the operating system and camera independent part, which uses libfreeimage for image manipulation.
 - CSimpleCam, the operating system and camera dependent part, which does image capture using libgphoto2.

If you wish to use this code for a new camera driver:
The example1 directory contains an example of a SimpleCam driver where the captureImage() method returns a bitmap file.
The example2 directory contains an example of a SimpleCam driver where the captureImage() method returns an in-memory bitmap.

needs: libgphoto2-2.4.10.1 or higher, FreeImage 3.15 or higher.

libgphoto2:
On Mac, libgphoto2 is available from macports.

freeimage:
Patch FreeImage 3.15.0 with libfreeimage_raw_halfsize.patch to add support for raw images without color interpolation. Later versions of FreeImage already have the patch applied.
patch -p0 < patches/libfreeimage_raw_halfsize.patch

You need to add support for both FreeImage and FreeImagePlus.

On Windows, link with both FreeImage.lib and FreeImagePlus.lib, and copy FreeImage.dll and FreeImagePlus.dll to the micro-manager directory.

On Mac, you need to compile both FreeImage and FreeImagePlus
- change line 5 of Makefile.osx from "include Makefile.srcs" to "include fipMakefile.srcs".
- run make

On Linux, you need to compile both FreeImage and FreeImagePlus
make -f Makefile.fip

You may have to copy FreeImagePlus.h to /usr/local/include manually.

Koen De Vleeschauwer, www.kdvelectronics.eu
Nico Stuurman
