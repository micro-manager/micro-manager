#include "../MMDevice/DeviceBase.h"
#include "MMCore.h"

class MMImageSaver:MMDeviceThreadBase
{
private:
   int svc() { Run(); return 0; }
   void WriteNextImage(string filestem);
   void WriteImage(string filename, void * img, int width, int height, int depth, Metadata md);
   unsigned char * SwapRedAndBlue(unsigned char * img, int width, int height, int depth);
   string CreateFileName(string filestem, Metadata md);
   void WriteMetadata(Metadata md);

   CMMCore * core_;
   unsigned char * buffer_;
   long bufferLength_;
   ofstream metadataStream_;

public:
   MMImageSaver(CMMCore * core);
   void Run();
   void Start();
};