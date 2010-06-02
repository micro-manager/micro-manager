#ifndef MMIMAGESAVER_H
#define MMIMAGESAVER_H

#include "../MMDevice/DeviceBase.h"
#include "MMAcquisition.h"
#include "MMCore.h"


class MMImageSaver:MMDeviceThreadBase
{
private:
   int svc() { Run(); return 0; }
   void WriteNextImage(string filestem);
   void WriteImage(string filename, void * img, int width, int height, int depth, Metadata md);
   unsigned char * SwapRedAndBlue(unsigned char * img, int width, int height, int depth);
   string CreateFileName(string filestem, Metadata md);
   void WriteMetadata(Metadata md, string title);
   void CreateDirectory(string path);
   bool DirectoryExists(string path);

   CMMCore * core_;
   MMAcquisitionEngine * eng_;
   
   unsigned char * buffer_;
   long bufferLength_;
   ofstream metadataStream_;
   string root_;
   string prefix_;
   bool firstElement_;

public:
   MMImageSaver(CMMCore * core, MMAcquisitionEngine * eng);
   void Run();
   void Start();
   void SetPaths(string root, string prefix);
   
};

#endif