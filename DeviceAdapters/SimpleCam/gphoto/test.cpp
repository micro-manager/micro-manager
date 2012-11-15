// test application for SimpleCam

#include "SimpleCam.h"
#include <iostream>
#include <assert.h>
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
// cameraName = "Canon Digital IXUS 120 IS";
// cameraName = "Canon PowerShot G9 (PTP mode)";
// cameraName = "Canon EOS 5D Mark II";
// cameraName = "Nikon DSC D40x (PTP mode)";

// gp_log_add_func(GP_LOG_DEBUG, errordumper, NULL);
// gp_log_add_func(GP_LOG_ERROR, errordumper, NULL);

   vector<string> camList;
   rc = scam.listCameras(camList);  
   assert(rc);

   for (int i = 0; i < camList.size(); i++)
      cout << i << " '" << camList[i] << "'" << endl;

   if (scam.connectCamera(cameraName))
      cout << "connected to " << cameraName << endl;
   else
      cout << "not connected" << endl;

   /* test shutter speed */

   string shutterSpeed;
   if (scam.getShutterSpeed(shutterSpeed))
      cout << "Current shutter speed " << shutterSpeed << endl;

   vector<string> shutterSpeedList;
   rc = scam.listShutterSpeeds(shutterSpeedList);  

   for (int i = 0; i < shutterSpeedList.size(); i++)
      cout << i << " '" << shutterSpeedList[i] << "'" << endl;

// char *newShutterSpeed = shutterSpeedList[0];
// char *newShutterSpeed = "1/20";
   char *newShutterSpeed = "1/30";
// char *newShutterSpeed = "1/80";

   rc = scam.setShutterSpeed(newShutterSpeed);  
   if (rc) 
      cout << "Shutter speed " << newShutterSpeed <<" written" << endl;
   else
      cout << "Shutter speed " << newShutterSpeed <<" failed" << endl;

   if (scam.getShutterSpeed(shutterSpeed))
      cout << "Current shutter speed " << shutterSpeed << endl;

   /* test ISO */

   string iso;
   if (scam.getISO(iso))
      cout << "Current ISO " << iso << endl;
   else
      cout << "ISO read error" << endl;

   vector<string> isoList;
   rc = scam.listISOs(isoList);

   for (int i = 0; i < isoList.size(); i++)
      cout << i << " '" << isoList[i] << "'" << endl;

   cout << isoList.size() << endl;

// char newISO[] = "400";
   char newISO[] = "Auto";
   rc = scam.setISO(newISO);
   if (rc)
      cout << "ISO " << newISO <<" written" << endl;
   else
      cout << "ISO " << newISO <<" failed" << endl;
 
   if (scam.getISO(iso))
      cout << "Current ISO " << iso << endl;



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
