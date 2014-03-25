///////////////////////////////////////////////////////////////////////////////
// FILE:          CanonEDSDK.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
// ----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Canon EDSDK 
// COPYRIGHT:     USCF, 2010
// AUTHOR:        Nico Stuurman
// License:       LGPL
//
//

#ifdef __APPLE__
#define __MACOS__ 1
#include "/Developer/SDKs/MacOSX10.4u.sdk/Developer/Headers/FlatCarbon/MacTypes.h"
#endif

#include "CanonEDSDK.h"

#include "../../../3rdparty/trunk/Canon/EDSDK2.13/Mac/EDSDK/Header/EDSDK.h"
#include "../../../3rdparty/trunk/Canon/EDSDK2.13/Mac/EDSDK/Header/EDSDKErrors.h"
#include "../../../3rdparty/trunk/Canon/EDSDK2.13/Mac/EDSDK/Header/EDSDKTypes.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
#define ERR_NO_CAMERA      200001
#define ERR_DRIVER         200002

/////////////////////////////////////////////////////////////////////////////
// Global strings

const char* g_CameraDeviceName = "Canon SLR";

//////////////////////////////////////////////////////////////////////////////
// Module Interface

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CameraDeviceName, MM::CameraDevice, "Canon SLR");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)                                                                                                                           
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraDeviceName) == 0)
   {
      // create camera
      return new CanonEDCamera();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


//////////////////////////////////////////////////////////////////////////////
//  CanonEDCamera class

void* CanonEDCamera::g_Self;

CanonEDCamera::CanonEDCamera() :
   nComponents_(4),
   isSDKLoaded_(false),
   isLegacy_(false),
   camera_(NULL)
{
   SetErrorText(ERR_NO_CAMERA, "No Canon SLR found");
   SetErrorText(ERR_DRIVER, "Canon driver reported an error");

}


CanonEDCamera::~CanonEDCamera()
{
   Shutdown();
}

void CanonEDCamera::GetName(char* name) const
{
   // We just return the name we use for referring to this device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

int CanonEDCamera::Shutdown()
{
   // save images on the memory card
   if (isLegacy_)
      EdsSendStatusCommand (camera_,  kEdsCameraStatusCommand_UILock, 0);
   EdsSaveTo toCamera = kEdsSaveTo_Camera;
   EdsError err = EdsSetPropertyData(camera_,  kEdsPropID_SaveTo, 0 , sizeof(EdsSaveTo),  &toCamera);
   if (isLegacy_)
      EdsSendStatusCommand (camera_,  kEdsCameraStatusCommand_UIUnLock, 0);

   // Close session with camera 
   err = EdsCloseSession(camera_); 
    
   // Release camera 
   if(camera_ != NULL) 
   { 
      EdsRelease(camera_); 
   } 
    
   // Terminate SDK 
   if(isSDKLoaded_) 
   { 
      EdsTerminateSDK(); 
   }

   return DEVICE_OK;
}

int CanonEDCamera::Initialize()
{
   EdsError err = EDS_ERR_OK; 
    
   // Initialize SDK 
   err = EdsInitializeSDK(); 
   if(err == EDS_ERR_OK) 
   { 
     isSDKLoaded_ = true; 
   } 
    
   // Get first camera 
   if(err == EDS_ERR_OK) 
   { 
      err = getFirstCamera (&camera_); 
   } 

   if (err == EDS_ERR_DEVICE_NOT_FOUND)
      return ERR_NO_CAMERA;
    

   // event handlers are static functions that need a reference to this camera object to function
   // provide that static ref here.  This approach will fail with multiple cameras.  In that case, 
   // consider making a map with cameras and references to our CanonEDCamera objects
   g_Self = this;

   // Set event handler 
   if(err == EDS_ERR_OK) 
   { 
      err = EdsSetObjectEventHandler(camera_,  kEdsObjectEvent_All, &CanonEDCamera::handleObjectEvent,  (EdsVoid*) this); 
   } 
    
   // Set event handler 
   if(err == EDS_ERR_OK) 
   { 
      err = EdsSetPropertyEventHandler(camera_,  kEdsPropertyEvent_All, &CanonEDCamera::handlePropertyEvent,  NULL); 
   } 
    
   // Set event handler 
   if(err == EDS_ERR_OK) 
   { 
      err = EdsSetCameraStateEventHandler(camera_,  kEdsStateEvent_All, &CanonEDCamera::handleStateEvent,  NULL); 
   } 

//#ifdef TARGET_OSX
   // NSAutoreleasePool * pool = [[NSAutoreleasePool alloc] init];
//#elif TARGET_WIN32
//   CoInitializeEx( NULL, 0x0);// OINIT_MULTITHREADED );
//#endif 

   if(err == EDS_ERR_OK) 
   { 
      err = EdsOpenSession(camera_); 
   } 
     
   // Camera name
   if (err == EDS_ERR_OK)
   {
      EdsDeviceInfo  deviceInfo;
      err = EdsGetDeviceInfo(camera_, &deviceInfo);
      if (err == EDS_ERR_OK)
      {
         std::ostringstream os;
         os << "Canon SLR device subtype: " << deviceInfo.deviceSubType;
         LogMessage(os.str().c_str());
         if (deviceInfo.deviceSubType == 0)
            isLegacy_ = true;
         cameraModel_ = deviceInfo.szDeviceDescription;
         int nRet = CreateProperty("Model", cameraModel_.c_str(), MM::String, true);
         if (nRet != DEVICE_OK)
            return nRet;
      }

      // Set and Get various camera properties

      // lock the UI before setting properties - only needed on legacy cameras
      if (isLegacy_)
         EdsSendStatusCommand (camera_,  kEdsCameraStatusCommand_UILock, 0);
   
      // Save images directly to the computer
      EdsSaveTo toPC = kEdsSaveTo_Host;
      err = EdsSetPropertyData(camera_,  kEdsPropID_SaveTo, 0 , sizeof(EdsSaveTo),  &toPC);
//      EdsSaveTo toBoth = kEdsSaveTo_Both;
//      err = EdsSetPropertyData(camera_,  kEdsPropID_SaveTo, 0 , sizeof(toBoth),  &toBoth);

      if (isLegacy_)
         EdsSendStatusCommand (camera_, kEdsCameraStatusCommand_UIUnLock, 0);
   }

   if (err == EDS_ERR_OK)
   {
      CPropertyAction* pAct = new CPropertyAction(this, &CanonEDCamera::OnBinning);
      int nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
      if (nRet != DEVICE_OK)
         return nRet;
      AddAllowedValue(MM::g_Keyword_Binning, "1");
   }

   if (err == EDS_ERR_OK)
   {
      return DEVICE_OK;
   }

   std::ostringstream os;
   os << "Error during initialization: " << std::hex << err;
   LogMessage(os.str().c_str());

   return err;
}


EdsError  CanonEDCamera::getFirstCamera(EdsCameraRef  *camera) 
{ 
    EdsError err = EDS_ERR_OK; 
    EdsCameraListRef  cameraList = NULL; 
    EdsUInt32  count = 0; 
    
    // Get camera list 
    err = EdsGetCameraList(&cameraList); 
    
    // Get number of cameras 
    if(err == EDS_ERR_OK) 
    { 
       err = EdsGetChildCount(cameraList,  &count); 
       if(count == 0) 
       { 
          err = EDS_ERR_DEVICE_NOT_FOUND; 
       } 
    } 

    // Get first camera retrieved 
   if(err == EDS_ERR_OK) 
   {  
       err = EdsGetChildAtIndex(cameraList ,  0 ,  camera); 
   } 
 
   // Release camera list 
   if(cameraList != NULL) 
   {  
      EdsRelease(cameraList); 
      cameraList = NULL; 
   } 

   return err;

}


int CanonEDCamera::EdsToMMError(EdsError err)
{
   if (err == EDS_ERR_OK)
      return DEVICE_OK;

   std::ostringstream os;
   os << "Canon driver reported error: " << std::hex << err;
   LogMessage(os.str().c_str());

   return ERR_DRIVER;
}

EdsError EDSCALLBACK CanonEDCamera::handleObjectEvent( EdsObjectEvent event, EdsBaseRef  object, EdsVoid * context) 
{ 
   printf ("Object Event triggered\n");

   CanonEDCamera* mmCanon = (CanonEDCamera*) g_Self;
  
   switch(event) 
   { 
      case kEdsObjectEvent_DirItemRequestTransfer: 
         {
            EdsError err = EDS_ERR_OK;
            EdsStreamRef stream = NULL;

            EdsDirectoryItemInfo dirItemInfo;
            err = EdsGetDirectoryItemInfo(object, &dirItemInfo);

            // do we need to notify the camera?
            /*
             if (err == EDS_ERR_OK)
             {
             CameraEvent e("DownloadStart");
             _model->notifyObservers(&e);
             }
             */

            if (err == EDS_ERR_OK)
            {
               //err = EdsCreateFileStream(dirItemInfo.szFileName, kEdsFileCreateDisposition_CreateAlways, kEdsAccess_ReadWrite, &stream);
               err = EdsCreateMemoryStream(0, &stream);
            }

            // Set Progress Callback???

            // Download Image
            if (err == EDS_ERR_OK)
            {
               err = EdsDownload(object,  dirItemInfo.size, stream);
            }

            if (err == EDS_ERR_OK)
            {
               err = EdsDownloadComplete(object);
            }

            EdsImageRef imageRef = NULL;

            if (err == EDS_ERR_OK)
            {
               err = EdsCreateImageRef(stream, &imageRef);
            }

            EdsImageInfo imageInfo;

            if (err == EDS_ERR_OK)
            {
               err = EdsGetImageInfo(imageRef, kEdsImageSrc_FullView, &imageInfo);
            }
            if (err == EDS_ERR_OK)
            {
               printf ("Image Width: %d\n", imageInfo.width);
            }


         }
         break; 
        
         default: 
          
         break; 
      } 
    
 
   // Object must be released 
   if(object) 
   { 
      EdsRelease(object); 
   } 
} 


EdsError EDSCALLBACK  CanonEDCamera::handlePropertyEvent (EdsPropertyEvent event, EdsPropertyID  property, EdsUInt32 param, EdsVoid * context) 
{ 
   printf ("Property Event triggered, event: %d, ID: %d\n", event, property);
   CanonEDCamera* mmCanon = (CanonEDCamera*) g_Self;
    // do something 
} 
 
EdsError EDSCALLBACK  CanonEDCamera::handleStateEvent (EdsStateEvent event, EdsUInt32 parameter, EdsVoid * context) 
{ 
   printf ("Camera State Event triggered\n");
   CanonEDCamera* mmCanon = (CanonEDCamera*) g_Self;
   // do something 
} 


// MMCamera API
int CanonEDCamera::SnapImage()
{
   // TODO: accurate estimate of capacity
   EdsCapacity capacity;
   capacity.reset = 1;
   capacity.numberOfFreeClusters = 1000000;
   capacity.bytesPerSector = 512;
   EdsError err  = EdsSetCapacity(camera_, capacity);
   if (err != EDS_ERR_OK)
      return EdsToMMError(err);

    // err = EdsSetObjectEventHandler(camera_,  kEdsObjectEvent_All, &CanonEDCamera::handleObjectEvent,  NULL); 

printf ("Sending Snap command to the camera\n");

      if (isLegacy_)
         EdsSendStatusCommand (camera_,  kEdsCameraStatusCommand_UILock, 0);
   err = EdsSendCommand(camera_, kEdsCameraCommand_TakePicture , 0); 
   if (err != EDS_ERR_OK)
      return EdsToMMError(err);
      if (isLegacy_)
         EdsSendStatusCommand (camera_,  kEdsCameraStatusCommand_UIUnLock, 0);

   return DEVICE_OK;
}

const unsigned char* CanonEDCamera::GetImageBuffer()
{
   
   return 0;
}

unsigned CanonEDCamera::GetImageWidth() const
{
   return 0;
}

unsigned CanonEDCamera::GetImageHeight() const
{
   return 0;
}

unsigned CanonEDCamera::GetImageBytesPerPixel() const
{
   return 0;
}

unsigned CanonEDCamera::GetBitDepth() const
{
   return 0;
}

long CanonEDCamera::GetImageBufferSize() const
{
   return 0;
}

double CanonEDCamera::GetExposure() const
{
   return 0;
}

void CanonEDCamera::SetExposure(double exp)
{
}

int CanonEDCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   return DEVICE_OK;
}

int CanonEDCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   return DEVICE_OK;
}

int CanonEDCamera::ClearROI()
{
   return DEVICE_OK;
}

int CanonEDCamera::StartSequenceAcquisition(double interval)
{
   return DEVICE_OK;
}

int CanonEDCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   return DEVICE_OK;
}

int CanonEDCamera::StopSequenceAcquisition()
{
   return DEVICE_OK;
}

int CanonEDCamera::InsertImage()
{
   return DEVICE_OK;
}

/*
int CanonEDCamera::ThreadRun()
{
   return DEVICE_OK;
}
*/

bool CanonEDCamera::IsCapturing()
{
   return false;
}

int CanonEDCamera::GetBinning() const
{
   return 1;                                                   
}

int CanonEDCamera::SetBinning(int bS)
{
   return DEVICE_OK;
} 

// action interface
int CanonEDCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

int CanonEDCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

int CanonEDCamera::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

int CanonEDCamera::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}

int CanonEDCamera::OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType )
{
   return DEVICE_OK;
}

int CanonEDCamera::OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType )
{
   return DEVICE_OK;
}

