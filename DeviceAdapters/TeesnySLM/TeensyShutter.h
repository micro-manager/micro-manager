#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#define ERR_COMMAND_SUCCESS_MISSING 101 


class TeensyShutter : public CShutterBase<TeensyShutter>
{
public:
   TeensyShutter();
   ~TeensyShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy(){return false;}

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnDevice1Name(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDevice2Name(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	char deviceName1_[MM::MaxStrLength];
     char deviceName2_[MM::MaxStrLength];
   bool initialized_;
};