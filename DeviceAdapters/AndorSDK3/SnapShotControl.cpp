#include "SnapShotControl.h"
#include "atcore++.h"

using namespace andor;

SnapShotControl::SnapShotControl(IDevice * cameraDevice_)
: cameraDevice(cameraDevice_),
  is_poised_(false),
  mono12PackedMode_(true)
{
   imageSizeBytes = cameraDevice->GetInteger(L"ImageSizeBytes");
   triggerMode = cameraDevice->GetEnum(L"TriggerMode");
   cycleMode = cameraDevice->GetEnum(L"CycleMode");
   bufferControl = cameraDevice->GetBufferControl();
   startAcquisitionCommand = cameraDevice->GetCommand(L"AcquisitionStart");
   stopAcquisitionCommand = cameraDevice->GetCommand(L"AcquisitionStop");
   sendSoftwareTrigger = cameraDevice->GetCommand(L"SoftwareTrigger");
   pixelEncoding = cameraDevice->GetEnum(L"PixelEncoding");
}

SnapShotControl::~SnapShotControl()
{
   cameraDevice->Release(imageSizeBytes);
   cameraDevice->ReleaseBufferControl(bufferControl);
   cameraDevice->Release(startAcquisitionCommand);
   cameraDevice->Release(stopAcquisitionCommand);
   cameraDevice->Release(cycleMode);
   cameraDevice->Release(triggerMode);
   cameraDevice->Release(sendSoftwareTrigger);
   cameraDevice->Release(pixelEncoding);
}


void SnapShotControl::poiseForSnapShot()
{
   cycleMode->Set(L"Continuous");
   std::wstring temp_ws = triggerMode->GetStringByIndex(triggerMode->GetIndex());
   if (temp_ws.compare(L"Internal") == 0)
   {
      triggerMode->Set(L"Software");
      set_internal_ = true;
      in_software_ = true;
      in_external_ = false;
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
   first_image_buffer = new unsigned char[(int)imageSizeBytes->Get()];
   second_image_buffer = new unsigned char[(int)imageSizeBytes->Get()];
   bufferControl->Queue(first_image_buffer, (int)imageSizeBytes->Get());
   bufferControl->Queue(second_image_buffer, (int)imageSizeBytes->Get());
   startAcquisitionCommand->Do();
   is_poised_ = true;
   mono12PackedMode_ = false;
   if (pixelEncoding->GetStringByIndex(pixelEncoding->GetIndex()).compare(L"Mono12Packed") == 0)
   {
      mono12PackedMode_ = true;
   }
}

void SnapShotControl::takeSnapShot(unsigned char *& return_buffer)
{
   int buffer_size = 0;

   if (in_software_)
   {
      sendSoftwareTrigger->Do();
   }
   bufferControl->Wait(return_buffer, buffer_size, AT_INFINITE);
   bufferControl->Queue(return_buffer, buffer_size);
}

void SnapShotControl::leavePoisedMode()
{
   stopAcquisitionCommand->Do();
   bufferControl->Flush();
   is_poised_ = false;

   delete [] first_image_buffer;
   first_image_buffer = NULL;
   delete [] second_image_buffer;
   second_image_buffer = NULL;

   if (set_internal_)
   {
      triggerMode->Set(L"Internal");
   }
}

void SnapShotControl::prepareCamera()
{
   cycleMode->Set(L"Continuous");
   triggerMode->Set(L"Software");
   startAcquisitionCommand->Do();
   stopAcquisitionCommand->Do();
}
