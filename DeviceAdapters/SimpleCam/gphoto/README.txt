Tethered shooting using libgphoto2.
To debug and test:
1. Switch on debugging in micro-manager
   Tools > Options > Debug log enabled
   Debug log is in /Applications/Micro-Manager1.4/CoreLog.txt
   This switches on debug logging for micro-manager, this device driver and libgphoto2.

2. Using the test program 
   make -f Makefile.test
   ./test
   This tests this device driver and libgphoto2. Adapt test.cpp to your needs.

3. Using the gphoto2 command line program
   Build gphoto2 using macports:
   port install gphoto2
   gphoto2 --camera "Nikon DSC D40x (PTP mode)" --capture-image-and-download
   This tests libgphoto2. 
    

