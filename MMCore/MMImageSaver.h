#include "../MMDevice/DeviceBase.h"
#include "MMCore.h"

class MMImageSaver:MMDeviceThreadBase
{
private:
   int svc() { Run(); return 0; }
   CMMCore * core_;
   void WriteNextImage(string filestem);
   void WriteImage(string filename, void * img, int width, int height, int depth, Metadata md);
   unsigned char * buffer_;
   long bufferLength_;
   unsigned char * SwapRedAndBlue(unsigned char * img, int width, int height, int depth);

public:
   MMImageSaver(CMMCore * core);
   void Run();
   void Start();
};