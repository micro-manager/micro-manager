These are driver sources to use DSLR cameras in micro-manager.
Information about micro-manager is available from http://www.micro-manager.org

The micro-manager driver consists of two parts:
 - CCameraFrontend, the operating system and camera independent part, which uses libfreeimage for image manipulation.
 - CSimpleCam, the operating system and camera dependent part, which does image capture using libgphoto2.

If you wish to use this code for a new camera driver:
The example1 directory contains an example of a SimpleCam driver where the captureImage() method returns a bitmap file.
The example2 directory contains an example of a SimpleCam driver where the captureImage() method returns an in-memory bitmap.

needs: libgphoto2-2.4.10.1 or higher, FreeImage 3.15 or higher.

libgphoto:
To compile a static libgphoto, drop support for ax203, jl2005a, jl2005c, st2205, and topfield drivers:
./configure --enable-static --without-libexif --disable-nls --with-drivers=adc65,agfa_cl20,aox,barbie,canon,casio_qv,clicksmart310,digigr8,digita,dimera3500,directory,enigma13,fuji,gsmart300,hp215,iclick,jamcam,jd11,kodak_dc120,kodak_dc210,kodak_dc240,kodak_dc3200,kodak_ez200,konica,konica_qm150,largan,lg_gsm,mars,dimagev,mustek,panasonic_coolshot,panasonic_l859,panasonic_dc1000,panasonic_dc1580,pccam300,pccam600,polaroid_pdc320,polaroid_pdc640,polaroid_pdc700,ptp2,ricoh,ricoh_g3,samsung,sierra,sipix_blink2,sipix_web2,smal,sonix,sony_dscf1,sony_dscf55,soundvision,spca50x,sq905,stv0674,stv0680,sx330z,toshiba_pdrm11

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

ToDo:
- Implement LiveView using capturePreview() (fast 20 fps 320x240 pixel viewfinder image) instead of using captureImage() (slow, high-resolution camera image)
