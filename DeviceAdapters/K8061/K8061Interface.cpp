/*
 *  K8061Interface.cpp
 *  
 *
 *  Created by Nico Stuurman on 3/1/08.
 *  Copyright 2008 UCSF. All rights reserved.
 *
 */

/*
 * Command structure of the K8061:
 * send_buf unsigned char[50]
 * byte 0 holds the command:
 * 0 - READ_ANALOG_CH
 * 1 - READ_ALL_ANALOG
 * 2 - OUT_ANALOG_CH
 * 3 - OUT_ALL_ANALOG
 * 4 - OUT_PWM
 * 5 - READ_DIGITAL_BYTE
 * 6 - OUT_DIGITAL_BYTE
 * 7 - Clear Digital Channel
 * 8 -
 * 9 - READ_Counters
 * 10 - Reset Counters
 * 11 - Read Version
 * 12 -
 * 13 - Power status
 * 14 - READ_DIGITAL_OUT
 * 15 - READ_ANALOG_OUT
 * 16 - READ_PWM_OUT
 Remaining bytes are data bytes.  Content depends on command
 
 */


#include "K8061.h"
#include "K8061Interface.h"
#include "../../MMDevice/ModuleInterface.h"
#include <cstdio>

K8061Interface::K8061Interface() :
   initialized_ (false)
{
}

K8061Interface::~K8061Interface()
{
}

int K8061Interface::OpenDevice()
{
   if (!initialized_)
      return ERR_BOARD_NOT_FOUND;

   std::string vellemanName = "Velleman K8061-";
   if (port_.find(vellemanName) != 0)
     return ERR_BOARD_NOT_FOUND;

   return DEVICE_OK;
}


int K8061Interface::OutIn(MM::Device& device, MM::Core& core, int Tx, int Rx)
{
   int ret = core.WriteToSerial(&device, port_.c_str(), data_send, Tx);
   if (ret != DEVICE_OK)
      return ret;
      
   unsigned long read;
   ret = core.ReadFromSerial(&device, port_.c_str(), (unsigned char*) data_receive, (unsigned long) Rx, read);
   return ret;
}

int K8061Interface::ReadAnalogChannel(MM::Device& device, MM::Core& core, unsigned char channel, long& value)
{
   data_send[0] = 0;
   if (channel > 8)
      channel = 8;
   if (channel < 1)
      channel = 1;
   data_send[1] = (unsigned char) channel -1;
   int ret = OutIn(device, core, 2, 4);
   if (ret != DEVICE_OK)
      return ret;
   value = (long) (data_receive[2] + 256 * data_receive[3]);

   return DEVICE_OK;
}

int K8061Interface::OutputAnalogChannel(MM::Device& device, MM::Core& core, unsigned char channel, unsigned char data)
{
   data_send[0] = 2;
   if (channel > 8)
      channel = 8;
   if (channel < 1)
      channel = 1;
   data_send[1] = channel -1;
   data_send[2] = data;
   return OutIn(device, core, 3, 4);
}

int K8061Interface::SetDigitalChannel(MM::Device& device, MM::Core& core, unsigned char channel)
{
   // TODO: check, Velleman code does round(Exp((channel-1)*ln(2)));
   data_send[0] = 8;
   data_send[1] = channel;
   return OutIn(device, core, 2, 3);
}

int K8061Interface::ClearDigitalChannel(MM::Device& device, MM::Core& core, unsigned char channel)
{
   data_send[0] = 7;
   data_send[1] = channel;
   return OutIn(device, core, 2, 3);
}

int K8061Interface::ClearAllDigitalChannel(MM::Device& device, MM::Core& core)
{
   data_send[0] = 6;
   data_send[1] = 0;
   return OutIn(device, core, 2, 3);
}
 
int K8061Interface::OutputAllDigital(MM::Device& device, MM::Core& core, unsigned char data)
{
   data_send[0] = 6;
   data_send[1] = data;
   return OutIn(device, core, 2, 3);
}


int K8061Interface::ReadAllDigital(MM::Device& device, MM::Core& core, unsigned char& result)
{
   data_send[0] = 5;
   int ret = OutIn(device, core, 1, 2);
   if (ret != DEVICE_OK)
      return ret;
   result = data_receive[1];
   return DEVICE_OK;
}
   
int K8061Interface::PowerGood(MM::Device& device, MM::Core& core, bool& good)
{
   data_send[0] = 13;
   int ret = OutIn(device, core, 1, 2);
   if (ret != DEVICE_OK)
      return ret;
   good = data_receive[1] == 0 ? false : true;
   return DEVICE_OK;
}

