// test application for SimpleCam

#include "SimpleCam.h"
#include <iostream>
#include <gphoto2/gphoto2.h>

using namespace std;

static void errordumper(GPLogLevel level, const char *domain, const char *format, va_list args, void *data) 
{
#if 1
   vfprintf (stderr, format, args);
   fprintf  (stderr, "\n");
   fflush   (stderr);
#endif
#if 0
   char *ret;
   int len = vasprintf(&ret, format, args);
   assert(len >= 0);
   if (ret && callback_)
   {
   fprintf  (stderr, "here1\n");
      callback_(callbackData_, ret);
   fprintf  (stderr, "here2\n");
      free(ret);
   }
   return;
#endif
}

// not truncated

main ()
{
   CSimpleCam scam;
   bool rc;
   string cameraName;
   cameraName = "Canon PowerShot A80 (PTP)";
// cameraName = "Nikon DSC D40x (PTP mode)";

// gp_log_add_func(GP_LOG_DEBUG, errordumper, NULL);
   gp_log_add_func(GP_LOG_ERROR, errordumper, NULL);


   vector<string> camList;
   rc = scam.listCameras(camList);  
   assert(rc);

   for (int i = 0; i < camList.size(); i++)
      cout << i << " '" << camList[i] << "'" << endl;

   if (scam.connectCamera(cameraName))
      cout << "connected to " << cameraName << endl;
   else
      cout << "not connected" << endl;

   string shutterSpeed;
   if (scam.getShutterSpeed(shutterSpeed))
      cout << "Current shutter speed " << shutterSpeed << endl;

   vector<string> shutterSpeedList;
   rc = scam.listShutterSpeeds(shutterSpeedList);  

   for (int i = 0; i < shutterSpeedList.size(); i++)
      cout << i << " '" << shutterSpeedList[i] << "'" << endl;

// char *newShutterSpeed = shutterSpeedList[0];
   char *newShutterSpeed = "1/20";

   rc = scam.setShutterSpeed(newShutterSpeed);  
   if (rc) 
      cout << "Shutter speed " << newShutterSpeed <<" written" << endl;
   else
      cout << "Shutter speed " << newShutterSpeed <<" failed" << endl;

   if (scam.getShutterSpeed(shutterSpeed))
      cout << "Current shutter speed " << shutterSpeed << endl;

   string fname;
   fname = scam.captureImage();
   cout << "captured to" << fname << endl;

   fname = scam.captureImage();
   cout << "captured to" << fname << endl;

   fipImage preview = scam.capturePreview();
   if (preview.isValid())
      preview.save("/tmp/preview.jpg");

   scam.disconnectCamera();
}
//not truncated
