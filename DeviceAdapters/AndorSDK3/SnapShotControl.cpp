#include "SnapShotControl.h"
#include "EventsManager.h"
#include "atcore++.h"
#include "DeviceUtils.h"
#include <string.h>

using namespace andor;
using namespace std;


SnapShotControl::SnapShotControl(IDevice * cameraDevice_, CEventsManager* _evMngr)
:  cameraDevice(cameraDevice_),
   eventsManager_(_evMngr),
   image_buffer_(NULL),
   is_poised_(false),
   mono12PackedMode_(true)
{
   triggerMode = cameraDevice->GetEnum(L"TriggerMode");
   bufferControl = cameraDevice->GetBufferControl();
   startAcquisitionCommand = cameraDevice->GetCommand(L"AcquisitionStart");
   stopAcquisitionCommand = cameraDevice->GetCommand(L"AcquisitionStop");
   sendSoftwareTrigger = cameraDevice->GetCommand(L"SoftwareTrigger");
}

SnapShotControl::~SnapShotControl()
{
   cameraDevice->Release(sendSoftwareTrigger);
   cameraDevice->Release(stopAcquisitionCommand);
   cameraDevice->Release(startAcquisitionCommand);
   cameraDevice->ReleaseBufferControl(bufferControl);
   cameraDevice->Release(triggerMode);
}

void SnapShotControl::setupTriggerModeSilently()
{
   std::wstring temp_ws = triggerMode->GetStringByIndex(triggerMode->GetIndex());
   if (temp_ws.compare(L"Internal") == 0)
   {
      triggerMode->Set(L"Software");
      set_internal_ = true;
      in_software_ = true;
      in_external_ = false;
   }
   else if (temp_ws.compare(L"External Start") == 0)
   {
      triggerMode->Set(L"External");
      set_internal_ = false;
      in_software_ = true;
      in_external_ = true;
   }
   else if (temp_ws.compare(L"Software") == 0)
   {
      set_internal_ = false;
      in_software_ = true;
      in_external_ = false;
   }
   else
   {
      set_internal_ = false;
      in_software_ = false;
      in_external_ = true;
   }
}

void SnapShotControl::resetTriggerMode()
{
   if (set_internal_)
   {
      triggerMode->Set(L"Internal");
      in_software_ = false;
   }
   else if (in_software_ && in_external_) //Ext Start mode
   {
      triggerMode->Set(L"External Start");
      in_software_ = false;
   }
}

int SnapShotControl::retrieveCurrentExposureTime()
{
   int i_retExposure_ms = INVALID_EXP_TIME;
   bool b_ret = false;
   double d_expTime = 0.015f;
   IFloat * expTime = cameraDevice->GetFloat(L"ExposureTime");
   try
   {
      if (expTime->IsImplemented() && expTime->IsReadable() )
      {
         d_expTime = expTime->Get();
         cameraDevice->Release(expTime);
         b_ret = true;
      }
   }
   catch (NotReadableException &)
   {
      cameraDevice->Release(expTime);
      b_ret = true;
   }
   catch (NoMemoryException &)
   {
      cameraDevice->Release(expTime);
      b_ret = false;
   }
   if (b_ret)
   {
      i_retExposure_ms = static_cast<int>(d_expTime*1000+0.99);
   }
   return i_retExposure_ms;
}

bool SnapShotControl::isGlobalShutter()
{
   bool b_ret = false;
   IEnum* gblShutr = cameraDevice->GetEnum(L"ElectronicShutteringMode");
   if (gblShutr->GetStringByIndex(gblShutr->GetIndex()).compare(L"Global") == 0)
   {
      b_ret = true;
   }
   cameraDevice->Release(gblShutr);
   return b_ret;
}

int SnapShotControl::getReadoutTime()
{
   int readoutTime_ms = 12;
   IFloat * readoutTime = cameraDevice->GetFloat(L"ReadoutTime");
   try
   {
      if (readoutTime->IsImplemented() && readoutTime->IsReadable() )
      {
         double d_roTime = readoutTime->Get();
         readoutTime_ms = static_cast<int>(d_roTime*1000);
      }
      cameraDevice->Release(readoutTime);
   }
   catch (NotReadableException &)
   {
      cameraDevice->Release(readoutTime);
   }
   return readoutTime_ms;
}

int SnapShotControl::getTransferTime()
{
   IFloat * maxrate = cameraDevice->GetFloat(L"MaxInterfaceTransferRate");
   int i_retTransferTime = 35;
   try
   {
      if (maxrate->IsImplemented() && maxrate->IsReadable() )
      {
         double d_transferTime = maxrate->Get();
         i_retTransferTime = static_cast<int>((1/d_transferTime)*1000 + 0.99);
      }
      cameraDevice->Release(maxrate);
   }
   catch (NotReadableException &)
   {
      cameraDevice->Release(maxrate);
   }

   if (isGlobalShutter())
   {
      i_retTransferTime += getReadoutTime();
   }
   return i_retTransferTime;
}

void SnapShotControl::poiseForSnapShot()
{
   is_poised_ = true;
   IEnum* cycleMode = cameraDevice->GetEnum(L"CycleMode");
   cycleMode->Set(L"Continuous");
   cameraDevice->Release(cycleMode);
   setupTriggerModeSilently();

   eventsManager_->ResetEvent(CEventsManager::EV_EXPOSURE_END_EVENT);

   IInteger* imageSizeBytes = cameraDevice->GetInteger(L"ImageSizeBytes");
   AT_64 ImageSize = imageSizeBytes->Get();
   cameraDevice->Release(imageSizeBytes);
   if (NULL == image_buffer_)
   {
      image_buffer_ = new unsigned char[static_cast<int>(ImageSize)];
      memset(image_buffer_, 0, static_cast<int>(ImageSize));
      bufferControl->Queue(image_buffer_, static_cast<int>(ImageSize));
   }
   startAcquisitionCommand->Do();
   mono12PackedMode_ = false;
   IEnum* pixelEncoding = cameraDevice->GetEnum(L"PixelEncoding");
   if (pixelEncoding->GetStringByIndex(pixelEncoding->GetIndex()).compare(L"Mono12Packed") == 0)
   {
      mono12PackedMode_ = true;
   }
   cameraDevice->Release(pixelEncoding);
}

bool SnapShotControl::takeSnapShot()
{
   bool b_ret = false;
   if (in_software_ && !in_external_)//in external start triggermode this will be ignored, and wait for an external trigger below
   {
      sendSoftwareTrigger->Do();
   }
   
   int exposure_ms = retrieveCurrentExposureTime();

   if (INVALID_EXP_TIME != exposure_ms)
   {
      if (eventsManager_->IsEventRegistered(CEventsManager::EV_EXPOSURE_END_EVENT) )
      {
         if (in_software_ && !in_external_)
         {
            // wait until event is set
            b_ret = eventsManager_->WaitForEvent(CEventsManager::EV_EXPOSURE_END_EVENT, AT_INFINITE);
         }
         else
         {
            // wait until event is set for longer as waiting on ext trigger
            b_ret = eventsManager_->WaitForEvent(CEventsManager::EV_EXPOSURE_END_EVENT, exposure_ms + EXT_TRIG_TIMEOUT_MILLISECONDS);
         }
      }
      else
      {
         CDeviceUtils::SleepMs(exposure_ms);
         b_ret = true;
      }
   }

   return b_ret;
}

void SnapShotControl::getData(unsigned char *& return_buffer)
{
   int buffer_size = 0;

   int timeout_ms = getTransferTime() + WAIT_DATA_TIMEOUT_BUFFER_MILLISECONDS;
   bool got_image = bufferControl->Wait(return_buffer, buffer_size, timeout_ms);
   if (got_image)
   {
      bufferControl->Queue(return_buffer, buffer_size);
   }
}

void SnapShotControl::resetCameraAcquiring()
{
   stopAcquisitionCommand->Do();
   bufferControl->Flush();
   resetTriggerMode();
}

void SnapShotControl::leavePoisedMode()
{
   is_poised_ = false;
   resetCameraAcquiring();
   delete [] image_buffer_;
   image_buffer_ = NULL;
}

