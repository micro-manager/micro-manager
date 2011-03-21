These are driver sources to use DSLR cameras in micro-manager.
Information about micro-manager is available from http://www.micro-manager.org

The micro-manager driver consists of two parts:
 - CCameraFrontend, the operating system and camera independent part, which uses libfreeimage for image manipulation.
 - CSimpleCam, the operating system and camera dependent part, which does image capture using libgphoto2.

needs: libgphoto2-2.4.10.1 or higher, FreeImage 3.15 or higher.

Koen De Vleeschauwer, www.kdvelectronics.eu
