/*
 *  K8061Interface.h
 *  
 *
 *  Created by Nico Stuurman on 3/1/08.
 *  Copyright 2008 UCSF. All rights reserved.
 *
 */

#ifndef _K8061Interface_H_
#define _K8061Interface_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"


class K8061Interface
{
public:
   K8061Interface();
   ~K8061Interface();
   
   int OpenDevice();
   int ReadDigitalChannel(MM::Device& device, MM::Core& core, unsigned char channel, bool& value);
   int OutputAnalogChannel(MM::Device& device, MM::Core& core, unsigned char channel, unsigned char data);
   int SetDigitalChannel(MM::Device& device, MM::Core& core, unsigned char channel);
   int ClearDigitalChannel(MM::Device& device, MM::Core& core, unsigned char channel);
   int ClearAllDigitalChannel(MM::Device& device, MM::Core& core);
   int OutputAllDigital(MM::Device& device, MM::Core& core, unsigned char data);
   int ReadAllDigital(MM::Device& device, MM::Core& core, unsigned char& result);
   int PowerGood(MM::Device& device, MM::Core& core, bool& good);
   int ReadAnalogChannel(MM::Device& device, MM::Core& core, unsigned char channel, long& value);
   void SetPort(std::string port) {port_ = port;}
   
   bool initialized_;
   
private:
   int OutIn(MM::Device& device, MM::Core& core, int Tx, int Rx);
   std::string port_;
   unsigned char data_send[50];
   unsigned char data_receive[50];
};      

#endif
