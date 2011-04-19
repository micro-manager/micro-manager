///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleCam.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Camera driver for gphoto2 cameras.
//                
// AUTHOR:        Koen De Vleeschauwer, www.kdvelectronics.eu, 2011
//
// COPYRIGHT:     (c) 2010, Koen De Vleeschauwer, www.kdvelectronics.eu
//
// LICENSE:       This file is distributed under the LGPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#include "SimpleCam.h"
#include <fcntl.h>
#include <assert.h>
#include <string.h>
#include <iostream>
#include <algorithm>
#include <gphoto2/gphoto2.h>
#include <gphoto2/gphoto2-version.h>
#include <gphoto2/gphoto2-port-version.h>

#ifdef __APPLE__
#include "CoreFoundation/CoreFoundation.h"
#include <sys/param.h>
#include <sys/stat.h>
#endif __APPLE__

using namespace std;

CSimpleCam::CSimpleCam()
{
   context_ = NULL;
   camera_ = NULL;
}

CSimpleCam::~CSimpleCam()
{
   disconnectCamera();
}

/* set paths to gphoto2 camera and i/o drivers, if needed. Used to package drivers within a Macosx application. */
int CSimpleCam::setLibPaths()
{
#ifdef __APPLE__
   /*
      load gphoto2 camera drivers from ./libgphoto2, and i/o drivers from ./libgphoto2_port, if these directories exist.
      unless the user decides otherwise by setting CAMLIBS or IOLIBS . See libgphoto2(3) man page.
      This allows packaging the gphoto2 drivers with the application.
   */
   char executablePath[MAXPATHLEN];
   uint32_t bufSize = sizeof(executablePath); 
   CFBundleRef mainBundle = CFBundleGetMainBundle();
   CFURLRef resourcesURL = CFBundleCopyResourcesDirectoryURL(mainBundle); // get path of bundle
   bool dirFound = CFURLGetFileSystemRepresentation(resourcesURL, TRUE, (UInt8 *)executablePath, MAXPATHLEN);
   CFRelease(resourcesURL);
   if (dirFound)
   
   {
      char realExecutablePath[MAXPATHLEN];
      strlcpy(realExecutablePath, executablePath, sizeof(realExecutablePath));
      if (realpath(executablePath, realExecutablePath) != NULL) // Clean up path

      {
         /* Move three directories up */
         for (int i=0; i<3; i++)
         {
            char *slash = strrchr(realExecutablePath, '/');
            if (slash && (slash != realExecutablePath)) 
               *slash='\0';
         }
         
         /*
          * If path_of_executable/libgphoto2 exists, load libgphoto2 drivers from there
          */
         char camlibsPath[MAXPATHLEN];
         strlcpy(camlibsPath, realExecutablePath, sizeof(camlibsPath));
         strlcat(camlibsPath, "/libgphoto2/libgphoto2", sizeof(camlibsPath)); // camlibsPath is now path_of_executable/libgphoto2/libgphoto2
         struct stat st;
         // Set CAMLIBS environment variable to path_of_executable/libgphoto2/libgphoto2 if directory exists
         if (stat(camlibsPath, &st) == 0)
         {
            gp_log(GP_LOG_DEBUG, "SimpleCam", "Setting CAMLIBS to '%s'", camlibsPath);
            setenv("CAMLIBS", camlibsPath, 0);
         }
         else
            gp_log(GP_LOG_DEBUG, "SimpleCam", "Directory '%s' not found. Not setting CAMLIBS. Using default libgphoto2 driver directory.", camlibsPath);

         /*
          * If path_of_executable/libgphoto2_port exists, load libgphoto2_port drivers from there
          */
         char iolibsPath[MAXPATHLEN];
         strlcpy(iolibsPath, realExecutablePath, sizeof(iolibsPath));
         strlcat(iolibsPath, "/libgphoto2/libgphoto2_port", sizeof(iolibsPath)); // iolibsPath is now path_of_executable/libgphoto2/libgphoto2_port
         // Set IOLIBS environment variable to path_of_executable/libgphoto2/libgphoto2_port if directory exists
         if (stat(iolibsPath, &st) == 0)
         {
            gp_log(GP_LOG_DEBUG, "SimpleCam", "Setting IOLIBS to '%s'", iolibsPath);
            setenv("IOLIBS", iolibsPath, 0);
         }
         else
            gp_log(GP_LOG_DEBUG, "SimpleCam", "Directory '%s' not found. Not setting IOLIBS. Using default libgphoto2_port driver directory.", iolibsPath);
      }
   }
   else
      gp_log(GP_LOG_ERROR, "SimpleCam", "Failed to get path of executable. Using default libgphoto2 and libgphoto2_port driver directory.");
#endif __APPLE__
return 0;
}


/* returns list of detected cameras */
bool CSimpleCam::listCameras(vector<string>& detected)
{
   int rc = GP_OK; // libgphoto2 result code
   detected.clear();

   setLibPaths(); // Set directory where gphoto2 camera and io drivers can be found
   
   GPContext *context;
   context = gp_context_new();
   assert(context != NULL);

   /* List cameras */
   CameraAbilitiesList *abilitiesList;
   CameraAbilities abilities;
   GPPortInfoList *portInfoList;
   GPPortInfo portInfo;
   CameraList *cameraList;

   if (rc >= GP_OK)
      rc = gp_abilities_list_new (&abilitiesList);
   
   if (rc >= GP_OK)
      rc = gp_abilities_list_load(abilitiesList, context);

   if (rc >= GP_OK)
      rc = gp_port_info_list_new(&portInfoList);

   if (rc >= GP_OK)
      rc = gp_port_info_list_load(portInfoList);

   // Create list of detected cameras
   if (rc >= GP_OK)
      rc = gp_list_new(&cameraList);

   if (rc >= GP_OK)
      rc = gp_abilities_list_detect(abilitiesList, portInfoList, cameraList, context);

   const char *camModel = NULL, *camPort = NULL;
   int numberOfCameras = 0;
   
   if (rc >= GP_OK)
   {
      rc = gp_list_count(cameraList);
      numberOfCameras = rc;
   }

   // Loop over list of cameras and add the cameras to the vector of detected cameras
   for (int i = 0; i < numberOfCameras; i++)
   {
      if (rc >= GP_OK)
         rc = gp_list_get_name(cameraList, i, &camModel);

      if (rc >= GP_OK)
         rc = gp_list_get_value(cameraList, i, &camPort);
   
      if (rc >= GP_OK)
         detected.push_back(camModel);
   }

   if (rc >= GP_OK)
      rc = gp_abilities_list_free(abilitiesList);
   
   if (rc >= GP_OK)
      rc = gp_port_info_list_free(portInfoList);
   
   gp_context_unref(context);
   
   sort(detected.begin(), detected.end());
   unique(detected.begin(), detected.end());

   return (rc >= GP_OK);
}

/* attempt to connect to the camera. cameraModelStr is one of the cameras detected by listCameras */
bool CSimpleCam::connectCamera(string cameraName)
{
   int rc = GP_OK; // libgphoto2 result code

   context_ = gp_context_new();
   assert(context_ != NULL);

   rc = gp_camera_new(&camera_);
   
   /* Choose camera model */
   CameraAbilitiesList *abilitiesList;
   CameraAbilities abilities;
   GPPortInfoList *portInfoList;
   GPPortInfo portInfo;
   CameraList *cameraList;

   if (rc >= GP_OK)
      rc = gp_abilities_list_new (&abilitiesList);
   
   if (rc >= GP_OK)
      rc = gp_abilities_list_load(abilitiesList, context_);

   if (rc >= GP_OK)
      rc = gp_port_info_list_new(&portInfoList);

   if (rc >= GP_OK)
      rc = gp_port_info_list_load(portInfoList);

   // Create list of detected cameras
   if (rc >= GP_OK)
      rc = gp_list_new(&cameraList);

   if (rc >= GP_OK)
      rc = gp_abilities_list_detect(abilitiesList, portInfoList, cameraList, context_);

   // Loop over list of detected cameras until we find one with the same name as "cameraName"
   const char *camModel = NULL, *camPort = NULL;
   bool found = false;
   int numberOfCameras = 0;
   
   if (rc >= GP_OK)
   {
      rc = gp_list_count(cameraList);
      numberOfCameras = rc;
   }

   for (int i = 0; i < numberOfCameras; i++)
   {
      if (rc >= GP_OK)
         rc = gp_list_get_name(cameraList, i, &camModel);

      if (rc >= GP_OK)
         rc = gp_list_get_value(cameraList, i, &camPort);
   
      if (rc >= GP_OK)
         found = !strcmp(cameraName.c_str(), camModel);

      if (found)
         break;
   }

   if (!found)
   {
      gp_log(GP_LOG_ERROR, "SimpleCam", "Camera %s not found.", cameraName.c_str());
      rc = GP_ERROR;
   }

   int cameraModel = 0;
   if (rc >= GP_OK)
   {
      rc  = gp_abilities_list_lookup_model(abilitiesList, camModel);
      cameraModel = rc;
   }

   if (rc >= GP_OK)
      rc = gp_abilities_list_get_abilities(abilitiesList, cameraModel, &abilities);

   if (rc >= GP_OK)
      rc = gp_abilities_list_free(abilitiesList);

   /* Set camera model */
   if (rc >= GP_OK)
      rc = gp_camera_set_abilities(camera_, abilities);

   int cameraPort = 0;
   if (rc >= GP_OK)
   {
      rc = gp_port_info_list_lookup_path(portInfoList, camPort);
      cameraPort = rc;
   }

   if (rc >= GP_OK)
      rc = gp_port_info_list_get_info(portInfoList, cameraPort, &portInfo);

   /* Set camera port */
   if (rc >= GP_OK)
      rc = gp_camera_set_port_info(camera_, portInfo); 

   if (rc >= GP_OK)
      rc = gp_port_info_list_free(portInfoList);

   /* connect to camera */
   if (rc >= GP_OK)
   {
      rc = gp_camera_init(camera_, context_);
#ifdef __APPLE__
      if (rc < GP_OK)
         gp_log(GP_LOG_ERROR, "SimpleCam", "Perhaps you forgot to kill the Mac OS X PTP daemon after switching on the camera, but before running the program?");
#endif
   }

   if (rc < GP_OK)
   {
      context_ = NULL;
      camera_ = NULL;
   }

   return (rc >= GP_OK);
}

/* disconnect from camera */
bool CSimpleCam::disconnectCamera()
{
   if (!(context_ && camera_))
      return false;

   int rc = GP_OK; 

   if (rc >= GP_OK)
      rc = gp_camera_exit(camera_, context_);

   if (rc >= GP_OK)
   {
      context_ = NULL;
      camera_ = NULL;
   }

   return (rc >= GP_OK);
}

/* true if camera is connected and ready */
bool CSimpleCam::isConnected()
{
   return (context_ && camera_);
}

/* utility to access shutter speed setting */
int CSimpleCam::getShutterSpeedWidget(CameraWidget* &rootConfig, CameraWidget* &shutterSpeedConfig)
{
   int rc = GP_OK; 

   if (rc >= GP_OK)
      rc = getWidget(rootConfig, shutterSpeedConfig, "main/capturesettings/shutterspeed");

   // widget type has to be GP_WIDGET_RADIO; check.
   CameraWidgetType shutterSpeedType;
   if (rc >= GP_OK)
      rc = gp_widget_get_type(shutterSpeedConfig, &shutterSpeedType);

   if (rc >= GP_OK)
   {  
      if (shutterSpeedType != GP_WIDGET_RADIO)
      {
         gp_log(GP_LOG_ERROR, "SimpleCam", "Only shutterspeed radio widget implemented.");
         rc = GP_ERROR;
      }
   }
      
   return rc;  
}

/* utility to access arbitrary setting.
   e.g. configName "main/capturesettings/shutterspeed" accesses shutter speed; 
   configName "/main/imgsettings/iso" accesses iso speed.  */
int CSimpleCam::getWidget(CameraWidget* &rootConfig, CameraWidget* &configWidget, const char* configName)
{
   CameraWidget *currentConfig;
   CameraWidget *childConfig;

   int rc = GP_OK; 
   if (rc >= GP_OK)
      rc = gp_camera_get_config(camera_, &rootConfig, context_);

   char *name = strdup(configName);
   if (!name)
      return GP_ERROR;

   char *part = name;
   currentConfig = rootConfig;

   while(1)
   {
      while(part[0] == '/')
         part++;
      char *slash = strchr (part,'/');
      if (slash)
         *slash='\0';
      rc = gp_widget_get_child_by_name (currentConfig, part, &childConfig);
      if (rc != GP_OK)
         break;
      currentConfig = childConfig;
      if (!slash) 
         break; /* end of path */
      part = slash+1;
   }
   
   if (rc >= GP_OK)
      configWidget = childConfig;

   if (name)
      free(name);

   return rc;
}

/* if connected to a camera, returns list of available shutter speeds */
bool CSimpleCam::listShutterSpeeds(std::vector<std::string>& shutterSpeeds)
{
   CameraWidget *rootConfig;
   CameraWidget *shutterSpeedConfig;

   shutterSpeeds.clear();
   
   if (!(context_ && camera_))
      return false;

   int rc = GP_OK;

   if (rc >= GP_OK)
      rc = getShutterSpeedWidget(rootConfig, shutterSpeedConfig);

   int numberOfChoices = 0;

   if (rc >= GP_OK)
   {
      rc = gp_widget_count_choices(shutterSpeedConfig);
      numberOfChoices = rc;
   }

   const char *widgetChoice;

   if (rc >= GP_OK)
   {
      for (int i = 0; i < numberOfChoices; i++)
      {
         if (rc >= GP_OK)
            rc = gp_widget_get_choice(shutterSpeedConfig, i, &widgetChoice);

         if (rc >= GP_OK)
            shutterSpeeds.push_back(widgetChoice);
      }
   }
   
   unique(shutterSpeeds.begin(), shutterSpeeds.end());

   return (rc >= GP_OK);
}

/* if connected to a camera, returns current shutter speed */
bool CSimpleCam::getShutterSpeed(std::string& currentShutterSpeed)
{
   CameraWidget *rootConfig;
   CameraWidget *shutterSpeedConfig;
   char* currentValue;

   currentShutterSpeed = "";

   if (!(context_ && camera_))
      return false;

   int rc = GP_OK;

   if (rc >= GP_OK)
      rc = getShutterSpeedWidget(rootConfig, shutterSpeedConfig);

   if (rc >= GP_OK)
      rc = gp_widget_get_value(shutterSpeedConfig, &currentValue);

   if (rc >= GP_OK)
      currentShutterSpeed = currentValue;

   if (rc >= GP_OK)
      rc = gp_widget_free(shutterSpeedConfig);

   return (rc >= GP_OK);
}

/* if connected to a camera, sets new shutter speed. 
   newShutterSpeed is one of the shutter speeds returned by listShutterSpeeds */
bool CSimpleCam::setShutterSpeed(std::string newShutterSpeed)
{
   CameraWidget *rootConfig;
   CameraWidget *shutterSpeedConfig;

   if (!(context_ && camera_))
      return false;

   int rc = GP_OK;

   if (rc >= GP_OK)
      rc = getShutterSpeedWidget(rootConfig, shutterSpeedConfig);
   
   if (rc >= GP_OK)
   {
      int readOnly;
      rc = gp_widget_get_readonly(shutterSpeedConfig, &readOnly);
      if ((rc >= GP_OK) && readOnly)
      {
         gp_log(GP_LOG_ERROR, "SimpleCam", "Shutterspeed is read-only. Perhaps wrong camera mode?");
         rc = GP_ERROR;
      }
   }

   if (rc >= GP_OK)
      rc = rc = gp_widget_set_value(shutterSpeedConfig, newShutterSpeed.c_str());

   if (rc >= GP_OK)
      rc = gp_camera_set_config(camera_, rootConfig, context_);

   if (rc >= GP_OK)
      rc = gp_widget_free(rootConfig);

   return (rc >= GP_OK);
}

/* if connected to a camera, takes a picture, and saves it to disk. return value is the filename of the picture */
string CSimpleCam::captureImage()
{
   CameraFile *camera_file;
   CameraFilePath camera_file_path;
   string imageFilename;
   string filename;

   /* Check whether connected to a camera */
   if (!(context_ && camera_))
   {
      gp_log(GP_LOG_ERROR, "SimpleCam", "Not connected.");
      return "";
   }

   int rc = GP_OK;
   if (rc >= GP_OK)
      rc = gp_camera_capture(camera_, GP_CAPTURE_IMAGE, &camera_file_path, context_);

   if (rc >= GP_OK)
   {
      /* create temporary file with same suffix as on-camera file */
      char tempname[4096];
      imageFilename = "/tmp/capture_XXXXXX_";
      imageFilename = imageFilename + camera_file_path.name;
      assert(sizeof(tempname) > imageFilename.size() + 1);
      strcpy(tempname, imageFilename.c_str());
      int len = mkstemps(tempname, strlen(camera_file_path.name)+1);
      assert(len >= 0);
      imageFilename = tempname;
   }

   // Create local file "imageFilename"
   int fdesc; /* file descriptor */
   if (rc >= GP_OK)
   {
      rc = open(imageFilename.c_str(), O_CREAT | O_WRONLY, 0644);
      fdesc = rc;
   }
   
   if (rc >= GP_OK)
      rc = gp_file_new_from_fd(&camera_file, fdesc);

   // Download image from camera to imageFilename
   if (rc >= GP_OK)
      rc = gp_camera_file_get(camera_, camera_file_path.folder, camera_file_path.name, GP_FILE_TYPE_NORMAL, camera_file, context_);

   // Delete image on camera
   if (rc >= GP_OK)
      rc = gp_camera_file_delete(camera_, camera_file_path.folder, camera_file_path.name, context_);

   if (rc >= GP_OK)
      rc = gp_file_free(camera_file);

   if (rc >= GP_OK)
      return imageFilename; 
   else 
      return "";
}


/* if connected to a camera, returns a viewfinder preview. 
 * return value is a FreeImagePlus bitmap; typically 320x240 pixels for Canon PowerShot, 640x426 pixels for Nikon DSLR, 768x512 to 1056x704 pixels for Canon DSLR; at 10-20 frames per second */
fipImage CSimpleCam::capturePreview()
{
   int rc = GP_OK;

   /* create a FreeImagePlus bitmap */
   fipImage previewImage;

   /* Check whether connected to a camera */
   if (!(context_ && camera_))
   {
      gp_log(GP_LOG_ERROR, "SimpleCam", "Not connected.");
      rc = GP_ERROR;
   }

   CameraFile* previewFile = NULL;

   if (rc >= GP_OK)
   {
      rc = gp_file_new(&previewFile);
   }

   if (rc >= GP_OK)
   {
      rc = gp_camera_capture_preview(camera_, previewFile, context_);
   }

   const char* previewData;
   unsigned long int previewSize;

   if (rc >= GP_OK)
   {
      rc = gp_file_get_data_and_size(previewFile, &previewData, &previewSize);
   }

   if (rc >= GP_OK)
   {
      fipMemoryIO previewBuf((BYTE *)previewData, previewSize);
      BOOL nRet =  previewImage.loadFromMemory(previewBuf, 0);
      rc = (nRet ? GP_OK : GP_ERROR);
   }

   if (previewFile)
   {
      gp_file_unref(previewFile);
      previewFile = NULL;
   }

   /* return value is FreeImagePlus bitmap */
   return previewImage;
}

// not truncated
