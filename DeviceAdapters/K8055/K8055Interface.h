///////////////////////////////////////////////////////////////////////////////
// FILE:          K8055Hub.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Velleman K8055 DAQ board
// COPYRIGHT:     University of California, San Francisco, 2007
// LICENSE:       LGPLi
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 11/02/2007
//
//

#ifndef _K8055Interface_H_
#define _K8055Interface_H_

//#include "/usr/local/include/usb.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

class K8055Interface
{
public:
   K8055Interface();
   ~K8055Interface();

   int OpenDevice();
   int CloseDevice(MM::Device& device, MM::Core& core);
   long ReadAnalogChannel(MM::Device& device, MM::Core& core, long Channelno);
   int ReadAllAnalog(MM::Device& device, MM::Core& core, long* data1, long* data2);
   int OutputAnalogChannel(MM::Device& device, MM::Core& core, long channel, long data);
   int OutputAllAnalog(MM::Device& device, MM::Core& core, long data1,long data2);
   int ClearAllAnalog(MM::Device& device, MM::Core& core);
   int ClearAnalogChannel(MM::Device& device, MM::Core& core, long channel);
   int SetAnalogChannel(MM::Device& device, MM::Core& core, long channel);
   int SetAllAnalog(MM::Device& device, MM::Core& core);
   int WriteAllDigital(MM::Device& device, MM::Core& core, long data);
   int ClearDigitalChannel(MM::Device& device, MM::Core& core, long channel);
   int ClearAllDigital(MM::Device& device, MM::Core& core);
   int SetDigitalChannel(MM::Device& device, MM::Core& core, long channel);
   int SetAllDigital(MM::Device& device, MM::Core& core);
   int ReadDigitalChannel(MM::Device& device, MM::Core& core, long channel);
   long ReadAllDigital(MM::Device& device, MM::Core& core);
   int ResetCounter(MM::Device& device, MM::Core& core, long counternr);
   long ReadCounter(MM::Device& device, MM::Core& core, long counterno);
   int SetCounterDebounceTime(MM::Device& device, MM::Core& core, long counterno, long debouncetime);
   int WriteK8055Data(MM::Device& device, MM::Core& core, unsigned char cmd);
   int ReadK8055Data(MM::Device& device, MM::Core& core);

   std::string port_;
   bool initialized_;

private:
   long boardAddress_;
   unsigned char data_in[9], data_out[9];
};

#endif // _K8055Interface_H_
