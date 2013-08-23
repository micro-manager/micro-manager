#ifndef _SNAPSHOTCONTROL_H_
#define _SNAPSHOTCONTROL_H_

namespace andor {
class IDevice;
class IEnum;
class ICommand;
class IBufferControl;
}
class CEventsManager;

class SnapShotControl
{
public:
   SnapShotControl(andor::IDevice* cameraDevice, CEventsManager* _evMngr);
   ~SnapShotControl();

   static const unsigned int WAIT_DATA_TIMEOUT_BUFFER_MILLISECONDS = 500;

   void setupTriggerModeSilently();
   void poiseForSnapShot();
   void leavePoisedMode();
   bool takeSnapShot();
   void getData(unsigned char*& image_buffers);
   void resetCameraAcquiring();

   bool isPoised(){return is_poised_;};
   bool isInternal() {return set_internal_;}
   bool isExternal() {return in_external_;}
   bool isSoftware() {return in_software_;}
   bool isMono12Packed() {return mono12PackedMode_; };

private:
   int  getReadoutTime();
   int  getTransferTime();
   int  retrieveCurrentExposureTime();
   bool isGlobalShutter();
   void resetTriggerMode();

private:
   static const unsigned int EXT_TRIG_TIMEOUT_MILLISECONDS = 10000;
   static const int INVALID_EXP_TIME = -1;

   andor::IDevice* cameraDevice;
   andor::IEnum* triggerMode;
   andor::ICommand* startAcquisitionCommand;
   andor::ICommand* stopAcquisitionCommand;
   andor::IBufferControl* bufferControl;
   andor::ICommand* sendSoftwareTrigger;

   CEventsManager* eventsManager_;

   unsigned char* image_buffer_;

   bool is_poised_;
   bool set_internal_;
   bool in_software_;
   bool in_external_;
   bool mono12PackedMode_;
};

#endif /* _SNAPSHOTCONTROL_H_ */