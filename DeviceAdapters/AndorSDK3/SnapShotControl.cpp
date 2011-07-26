#include "SnapShotControl.h"

SnapShotControl::SnapShotControl(IDevice* cameraDevice_)
   : cameraDevice(cameraDevice_), is_poised_(false)
{
   imageSizeBytes = cameraDevice->GetInteger(L"ImageSizeBytes");
   triggerMode = cameraDevice->GetEnum(L"TriggerMode");
   cycleMode = cameraDevice->GetEnum(L"CycleMode");
   bufferControl = cameraDevice->GetBufferControl();
   startAcquisitionCommand = cameraDevice->GetCommand(L"AcquisitionStart");
   stopAcquisitionCommand = cameraDevice->GetCommand(L"AcquisitionStop");
   sendSoftwareTrigger = cameraDevice->GetCommand(L"SoftwareTrigger");
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
}


void SnapShotControl::poiseForSnapShot()
{
   cycleMode->Set(L"Continuous");
   std::wstring temp_ws = triggerMode->GetStringByIndex(triggerMode->GetIndex());
   if (temp_ws.compare(L"Internal") == 0) {
      triggerMode->Set(L"Software");
      set_internal_ = true;
      in_software_ = true;
      in_external_ = false;
   }
   else if (temp_ws.compare(L"Software") == 0) {
      set_internal_ = false;
      in_software_ = true;
      in_external_ = false;
   }
   else {
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
}

void SnapShotControl::takeSnapShot(unsigned char*& return_buffer)
{
   int buffer_size = NULL;
   
   if (in_software_) {
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
   delete [] second_image_buffer;

   if (set_internal_) {
      triggerMode->Set(L"Internal");
   }
}