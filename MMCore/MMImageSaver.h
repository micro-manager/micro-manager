#include "../MMDevice/DeviceBase.h"
#include "MMCore.h"

class MMImageSaver:MMDeviceThreadBase
{
private:
   int svc() { Run(); return 0; }
   CMMCore * core_;
   void WriteNextImage(string filename);

public:
   MMImageSaver(CMMCore * core);
   void Run();
   void Start();
};