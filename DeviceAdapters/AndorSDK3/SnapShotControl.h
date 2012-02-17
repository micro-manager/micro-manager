#ifndef _SNAPSHOTCONTROL_H_
#define _SNAPSHOTCONTROL_H_

namespace andor {
class IDevice;
class IInteger;
class IEnum;
class ICommand;
class IBufferControl;
}

class SnapShotControl
{
public:
   SnapShotControl(andor::IDevice* cameraDevice);
   ~SnapShotControl();

   void poiseForSnapShot();
   void leavePoisedMode();
   void takeSnapShot(unsigned char*& image_buffers);
   void prepareCamera();

   bool isPoised(){return is_poised_;};
   bool isInternal() {return set_internal_;}
   bool isExternal() {return in_external_;}
   bool isSoftware() {return in_software_;}
   bool isMono12Packed() {return mono12PackedMode_; };

private:
   andor::IDevice* cameraDevice;
   andor::IInteger* imageSizeBytes;
   andor::IEnum* triggerMode;
   andor::IEnum* cycleMode;
   andor::ICommand* startAcquisitionCommand;
   andor::ICommand* stopAcquisitionCommand;
   andor::IBufferControl* bufferControl;
   andor::ICommand* sendSoftwareTrigger;
   andor::IEnum* pixelEncoding;

   unsigned char* first_image_buffer;
   unsigned char* second_image_buffer;

   bool is_poised_;
   bool set_internal_;
   bool in_software_;
   bool in_external_;
   bool mono12PackedMode_;
};

#endif /* _SNAPSHOTCONTROL_H_ */