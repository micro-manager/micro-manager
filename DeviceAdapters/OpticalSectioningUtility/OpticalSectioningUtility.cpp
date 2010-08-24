///////////////////////////////////////////////////////////////////////////////
// FILE:          OpticalSectioningUtility.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Combines an SLM and a Camera to produce optical sections.
//
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 4/6/2010
// COPYRIGHT:     University of California, San Francisco, 2010
// LICENSE:       This file is distributed under the BSD license.
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
#include "OpticalSectioningUtility.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/MMDevice.h"
#include <math.h>

#define PI 3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include <math.h>

const char* g_OpticalSectioningUtility = "OpticalSectioningUtility";
const char* g_CameraProperty = "Camera";
const char * g_SLMProperty = "SLM";
const char * g_StripeWidth = "StripeWidth";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_OpticalSectioningUtility, "Combine a physical camera and an SLM to produce an optical sectioning virtual camera.");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_OpticalSectioningUtility) == 0) { 
      return new OpticalSectioningUtility();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}

///////////////////////////////////////////////////////////////////////////////
// Optical Sectioning Utility implementation
///////////////////////////////////////////////////////////////////////////////

OpticalSectioningUtility::OpticalSectioningUtility():
physicalCamera_(0),
slm_(0),
physicalCameraName_(""),
slmName_(""),
lambda_(60)

{
   
}

OpticalSectioningUtility::~OpticalSectioningUtility()
{

}

int OpticalSectioningUtility::Initialize()
{
   std::vector<std::string>::iterator iter;

   InitializeDefaultErrorMessages();

   std::vector<std::string> availableCameras ;
   availableCameras.clear();
   char deviceName[MM::MaxStrLength];
   unsigned int deviceIterator = 0;
   for(;;)
   {
      GetLoadedDeviceOfType(MM::CameraDevice, deviceName, deviceIterator++);
      if( 0 < strlen(deviceName))
      {
         availableCameras.push_back(std::string(deviceName));
      }
      else
         break;
   }

   CPropertyAction* pAct1 = new CPropertyAction (this, &OpticalSectioningUtility::OnPhysicalCamera);
   CreateProperty(g_CameraProperty,"",MM::String, false, pAct1);

   AddAllowedValue(g_CameraProperty,"");
   for (iter = availableCameras.begin(); iter != availableCameras.end(); iter++ ) {
      AddAllowedValue(g_CameraProperty,(*iter).c_str());
   }

   std::vector<std::string> availableSLMs;
   availableSLMs.clear();
   deviceIterator = 0;
   for(;;)
   {
      GetLoadedDeviceOfType(MM::SLMDevice, deviceName, deviceIterator++);
      if( 0 < strlen(deviceName))
      {
         availableSLMs.push_back(std::string(deviceName));
      }
      else
         break;
   }


   CPropertyAction* pAct2 = new CPropertyAction (this, &OpticalSectioningUtility::OnSLM);
   CreateProperty(g_SLMProperty,"",MM::String, false, pAct2);

   AddAllowedValue(g_SLMProperty,"");
   for (iter = availableSLMs.begin(); iter != availableSLMs.end(); iter++ ) {
      AddAllowedValue(g_SLMProperty,(*iter).c_str());
   }

   CPropertyAction* pAct3 = new CPropertyAction (this, &OpticalSectioningUtility::OnStripeWidth);
   CreateProperty(g_StripeWidth,"10",MM::Integer, false, pAct3);

   SetPropertyLimits(g_StripeWidth,6,300);

   return DEVICE_OK;
}

int OpticalSectioningUtility::Shutdown()
{
   return DEVICE_OK;
}

void OpticalSectioningUtility::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_OpticalSectioningUtility);
}

int OpticalSectioningUtility::SnapImage()
{
   std::vector<unsigned short *> raw;

   long w = physicalCamera_->GetImageWidth();
   long h = physicalCamera_->GetImageHeight();
   long numPixels = w * h;
   unsigned short * pixels;

   for (int i=0;i<3;++i)
   {
      pixels = new unsigned short[numPixels];
      acquireOneFrame(i, pixels, numPixels);
      raw.push_back(pixels);
   }

   slm_->SetPixelsTo(0);

   if (numPixels_ != numPixels)
   {
      outPixels_ = new unsigned short[numPixels];
      numPixels_ = numPixels;
   }

   float d01, d12, d20;

   for (long j = 0; j<numPixels; ++j) {
      d01 = (float) (raw[0][j] - raw[1][j]);
      d12 = (float) (raw[1][j] - raw[2][j]);
      d20 = (float) (raw[2][j] - raw[0][j]);
      outPixels_[j] = (unsigned short) sqrt(d01*d01 + d12*d12 + d20*d20);
   }
   
   for (int i=0;i<3;++i)
      delete raw[i];

   return DEVICE_OK;
}

void OpticalSectioningUtility::acquireOneFrame(int frameIndex, unsigned short * pixels, long numPixels)
{
   slm_->SetImage(slmImages_[frameIndex]);
   slm_->DisplayImage();
   physicalCamera_->SnapImage();

   long bpp = physicalCamera_->GetImageBytesPerPixel();
   if (bpp == 2)
      memcpy(pixels, physicalCamera_->GetImageBuffer(), numPixels*2);

}

const unsigned char* OpticalSectioningUtility::GetImageBuffer()
{
   return (unsigned char *) outPixels_;
}

unsigned OpticalSectioningUtility::GetImageWidth() const
{
   return physicalCamera_->GetImageWidth();
}

unsigned OpticalSectioningUtility::GetImageHeight() const
{
   return physicalCamera_->GetImageHeight();
}


unsigned OpticalSectioningUtility::GetImageBytesPerPixel() const
{
   return physicalCamera_->GetImageBytesPerPixel();
}


unsigned OpticalSectioningUtility::GetBitDepth() const
{
   return physicalCamera_->GetBitDepth();
}


long OpticalSectioningUtility::GetImageBufferSize() const
{
   return physicalCamera_->GetImageBufferSize();
}


double OpticalSectioningUtility::GetExposure() const
{
   return physicalCamera_->GetExposure();
}


void OpticalSectioningUtility::SetExposure(double exp)
{
   physicalCamera_->SetExposure(exp);
}


int OpticalSectioningUtility::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   return physicalCamera_->SetROI(x, y, xSize, ySize);
}


int OpticalSectioningUtility::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   return physicalCamera_->GetROI(x, y, xSize, ySize);
}


int OpticalSectioningUtility::ClearROI()
{
   return physicalCamera_->ClearROI();
}


double OpticalSectioningUtility::GetPixelSizeUm() const
{
   return physicalCamera_->GetPixelSizeUm();
}


int OpticalSectioningUtility::GetBinning() const
{
   return physicalCamera_->GetBinning();
}


int OpticalSectioningUtility::SetBinning(int bS)
{
   return physicalCamera_->SetBinning(bS);
}

int OpticalSectioningUtility::OnStripeWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {  
      pProp->Set(lambda_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(lambda_);
   }
   return DEVICE_OK;
}

int OpticalSectioningUtility::OnPhysicalCamera(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {  
      pProp->Set(physicalCameraName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(physicalCameraName_);
      if (physicalCameraName_.size() == 0)
      {
         physicalCamera_ = NULL;
      }
      else
      {
         MM::Camera * physicalCamera = (MM::Camera*) GetDevice(physicalCameraName_.c_str());
         if (physicalCamera != 0)
         {
            physicalCamera_ = physicalCamera;
         }
         else
         {
            return ERR_INVALID_DEVICE_NAME;
         }
      }
   }

   return DEVICE_OK;
}

int OpticalSectioningUtility::OnSLM(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {  
      pProp->Set(slmName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(slmName_);
      if (slmName_.size() == 0)
      {
         slm_ = NULL;
      }
      else
      {
         MM::SLM * slm = (MM::SLM*) GetDevice(slmName_.c_str());
         if (slm != 0)
         {
            slm_ = slm;
         }
         else
         {
            return ERR_INVALID_DEVICE_NAME;
         }
      }
   }

   SetupSLMImages();
   
   return DEVICE_OK;
}

int OpticalSectioningUtility::SetupSLMImages()
{
   if (slm_ == 0)
      return DEVICE_NOT_CONNECTED;


   int slmWidth = slm_->GetWidth();
   int slmHeight = slm_->GetHeight();

   const long imgArraySize = slmWidth*slmHeight;
   slmImages_.push_back(new unsigned char[imgArraySize]);
   slmImages_.push_back(new unsigned char[imgArraySize]);
   slmImages_.push_back(new unsigned char[imgArraySize]);
   double lambda = (double) lambda_;

   for(int x=0;x<slmWidth;++x)
   {
      for(int y=0;y<slmHeight;++y)
      {
         slmImages_[0][x + slmWidth*y] = (unsigned char) (255*0.5*(1 + cos(2.*PI*(x/lambda + 0./3.))));
         slmImages_[1][x + slmWidth*y] = (unsigned char) (255*0.5*(1 + cos(2.*PI*(x/lambda + 1./3.))));
         slmImages_[2][x + slmWidth*y] = (unsigned char) (255*0.5*(1 + cos(2.*PI*(x/lambda + 2./3.))));
      }
   }

   return DEVICE_OK;
}