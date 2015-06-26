#ifndef _SPECTRALLMM5INTERFACE_H_
#define _SPECTRALLMM5INTERFACE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <stdint.h>

struct availableLines {
   int lineNr;
   double waveLength;
   bool present;
   std::string name;
};

class SpectralLMM5Interface
{
public:
   SpectralLMM5Interface(std::string port, MM::PortType);
   ~SpectralLMM5Interface();

   int ExecuteCommand(MM::Device& device, MM::Core& core, unsigned char* buf, unsigned long bufLen, unsigned char* answer, unsigned long answerLen, unsigned long& read);
   int DetectLaserLines(MM::Device& device, MM::Core& core);

   // Access functions
   void SetPort(std::string port) {port_ = port;}
   int SetTransmission(MM::Device& device, MM::Core& core, long laserLine, double transmission);
   int GetTransmission(MM::Device& device, MM::Core& core, long laserLine, double& transmission);
   int SetShutterState(MM::Device& device, MM::Core& core, int state);
   int GetShutterState(MM::Device& device, MM::Core& core, int& state);
   int SetExposureConfig(MM::Device& device, MM::Core& core, std::string config);
   int GetExposureConfig(MM::Device& device, MM::Core& core, std::string& config);
   int SetTriggerOutConfig(MM::Device& device, MM::Core& core, unsigned char * config);
   int GetTriggerOutConfig(MM::Device& device, MM::Core& core, unsigned char *);
   int GetFirmwareVersion(MM::Device& device, MM::Core& core, std::string& version);
   int GetFLICRAvailable(MM::Device& device, MM::Core& core, bool& available);
   int GetNrLines() { return nrLines_;}

   const static int maxLines_ = 8;
   availableLines* getAvailableLaserLines() { return laserLines_;}


private:
   unsigned char majorFWV_, minorFWV_;
   availableLines laserLines_[maxLines_];
   std::string port_;
   bool initialized_;
   bool laserLinesDetected_;
   MM::PortType portType_;
   int nrLines_;
};
#endif  // _SPECTRALLMM5INTERFACE_H_


