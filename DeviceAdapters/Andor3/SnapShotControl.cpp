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
   triggerMode->Set(L"Software");
   first_image_buffer = new unsigned char[imageSizeBytes->Get()];
   second_image_buffer = new unsigned char[imageSizeBytes->Get()];
   bufferControl->Queue(first_image_buffer, imageSizeBytes->Get());
   bufferControl->Queue(second_image_buffer, imageSizeBytes->Get());
   startAcquisitionCommand->Do();
   is_poised_ = true;
}

void SnapShotControl::takeSnapShot(unsigned char*& return_buffer)
{
   int buffer_size;
   sendSoftwareTrigger->Do();
   bufferControl->Wait(return_buffer, buffer_size, AT_INFINITE);
   bufferControl->Queue(return_buffer, buffer_size);
}

void SnapShotControl::leavePoisedMode()
{
   stopAcquisitionCommand->Do();
   bufferControl->Flush();
   is_poised_ = false;

   delete first_image_buffer;
   delete second_image_buffer;
}