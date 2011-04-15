#ifndef _SNAPSHOTCONTROL_H_
#define _SNAPSHOTCONTROL_H_

#include "atcore++.h"

using namespace andor;

class SnapShotControl
{
public:
   SnapShotControl(IDevice* cameraDevice);
   ~SnapShotControl();

   void poiseForSnapShot();
   void leavePoisedMode();
   void takeSnapShot(unsigned char*& image_buffers);
   bool isPoised(){return is_poised_;};

private:
   IDevice* cameraDevice;
   IInteger* imageSizeBytes;
   IEnum* triggerMode;
   IEnum* cycleMode;
   ICommand* startAcquisitionCommand;
   ICommand* stopAcquisitionCommand;
   IBufferControl* bufferControl;
   ICommand* sendSoftwareTrigger;

   unsigned char* first_image_buffer;
   unsigned char* second_image_buffer;

   bool is_poised_;
};

#endif /* _SNAPSHOTCONTROL_H_ */