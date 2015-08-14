///////////////////////////////////////////////////////////////////////////////
// FILE:       DiskoveryListener.cpp
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
// AUTHOR:     Nico Stuurman
// COPYRIGHT:  Regents of the University of California, 2015
// 
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Adapter for the Spectral/Andor/Oxford Instruments Diskovery 1 spinning disk confocal
// microscope system
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
  
#include "Diskovery.h"

/**
 * Class that listens to the Diskovery1, interprets all incoming messages
 * and updates our internal model.  No other code should intercept communication
 * coming from the Diskovery 1
 */
DiskoveryListener::DiskoveryListener(
      MM::Device& device, 
      MM::Core& core, 
      std::string serialPort, 
      DiskoveryModel* model) :
   stop_(false),
   device_(device),
   core_(core),
   model_(model),
   port_(serialPort)
{
}

DiskoveryListener::~DiskoveryListener()
{
   Shutdown();
}

void DiskoveryListener::Shutdown()
{
   Stop();
   wait();
   core_.LogMessage(&device_, "Destructing MonitoringThread", true);
}


int DiskoveryListener::svc()
{
   core_.LogMessage(&device_, "Starting Diskovery Listener Thread", true);

   char answer[MM::MaxStrLength];
   std::string term = "\r\n";
   int ret = DEVICE_OK;
   while (!stop_)
   {
      ret = core_.GetSerialAnswer(&device_, port_.c_str(), MM::MaxStrLength, answer, term.c_str());
      // serial timeout should be set to a number larger than 5 seconds
      // and a timeout indicates that the device is not here.  
      // TODO: communicate this fact back to the adapter
      if (ret == DEVICE_SERIAL_TIMEOUT)
      {
      }
      if (ret == DEVICE_OK) {
         ParseMessage(answer);
      } else
      {
         // this can only be bad
         // TODO: communicate the bad situation and bail
      }

   }
   return 0;
}

void DiskoveryListener::Start()
{
   stop_ = false;
   activate();
}

void DiskoveryListener::ParseMessage(std::string message)
{
   uint16_t number;
   bool state;

   // split the message at the '=' sign
   // TODO: the manual states that some message can start with "MESSAGE="
   // My device does not answer like that.  Investigate....
   std::vector<std::string> tokens = split(message, '=');
   if (tokens.size() == 2) 
   {
      if (tokens[0] == "STATUS") 
      { 
         if (tokens[1] == "1") 
         {
            model_->SetDeviceBusy(false);
         } else
         {
            model_->SetDeviceBusy(true);
         }

      // Preset Spinning Disk
      } else if (tokens[0] == "PRESET_SD") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetPresetSD(number);

      // Preset Wide Field
      } else if (tokens[0] == "PRESET_WF") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetPresetWF(number);

      // Motor Running
      } else if (tokens[0] == "MOTOR_RUNNING_SD") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         state = false;
         if (number == 1) 
            state = true;
         model_->SetMotorRunningSD(state);

      // Preset Wide Field
      } else if (tokens[0] == "PRESET_WF") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetPresetWF(number);

      // Preset iris
      } else if (tokens[0] == "PRESET_IRIS") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetPresetIris(number);

      // Preset Filter
      } else if (tokens[0] == "PRESET_FILTER_W") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetPresetFilterW(number);

      // Preset Filter T
      } else if (tokens[0] == "PRESET_FILTER_T") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetPresetFilterT(number);

      // Preset PX (TIRF)
      } else if (tokens[0] == "PRESET_PX") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetPresetTIRF(number);

      // Hardware version
      } else if (tokens[0] == "VERSION_HW_MAJOR") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetHardwareVersionMajor(number);
      } else if (tokens[0] == "VERSION_HW_MINOR") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetHardwareVersionMinor(number);
      } else if (tokens[0] == "VERSION_HW_REVISION") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetHardwareVersionRevision(number);

      // Firmware version
      } else if (tokens[0] == "VERSION_FW_MAJOR") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetFirmwareVersionMajor(number);
      } else if (tokens[0] == "VERSION_FW_MINOR") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetFirmwareVersionMinor(number);
      } else if (tokens[0] == "VERSION_FW_REVISION") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetFirmwareVersionRevision(number);

      // manufacture date
      } else if (tokens[0] == "MANUFACTURE_YEAR") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetManufactureYear(number);
      } else if (tokens[0] == "MANUFACTURE_MONTH") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetManufactureMonth(number);
      } else if (tokens[0] == "MANUFACTURE_DAY") 
      {
         std::istringstream(tokens[1].c_str()) >> number;
         model_->SetManufactureDay(number);

      // Serial no.
      } else if (tokens[0] == "PRODUCT_SERIAL_NO") 
      {
         model_->SetSerialNumber(tokens[1]);
         
      // ------------------  Queries concerning capabililties --------
      // The model sets everything false by default
      // Only set to true if capability was found
      // Has WFX
      } else if (tokens[0] == "ACTIVE_WF_X") 
      {
         if (tokens[1] == "1")
            model_->SetHasWFX(true);

      // Has WFY
      } else if (tokens[0] == "ACTIVE_WF_Y") 
      {
         if (tokens[1] == "1")
            model_->SetHasWFY(true);

      // Has Spinning Disk
      } else if (tokens[0] == "ACTIVE_SD") 
      {
         if (tokens[1] == "1")
            model_->SetHasSD(true);

      // Has LIN
      } else if (tokens[0] == "ACTIVE_LIN") 
      {
         if (tokens[1] == "1")
            model_->SetHasLIN(true);

      // Has ROT
      } else if (tokens[0] == "ACTIVE_ROT") 
      {
         if (tokens[1] == "1")
            model_->SetHasROT(true);

      // Has P1 
      } else if (tokens[0] == "ACTIVE_P1") 
      {
         if (tokens[1] == "1")
            model_->SetHasP1(true);

      // Has P2 
      } else if (tokens[0] == "ACTIVE_P2") 
      {
         if (tokens[1] == "1")
            model_->SetHasP2(true);

      // Has Iris
      } else if (tokens[0] == "ACTIVE_IRIS") 
      {
         if (tokens[1] == "1")
            model_->SetHasIRIS(true);

      // Has FilterW
      } else if (tokens[0] == "ACTIVE_FILTER_W") 
      {
         if (tokens[1] == "1")
            model_->SetHasFilterW(true);

      // Has FilterT
      } else if (tokens[0] == "ACTIVE_FILTER_T") 
      {
         if (tokens[1] == "1")
            model_->SetHasFilterT(true);
      }
      // 
      else if (tokens[0].substr(0, 6) == "BUTTON") {
         std::vector<std::string> button = split(tokens[0], '_');
         if (button[1] == "WF") {
            model_->SetButtonWFLabel(atoi(button[2].c_str()), tokens[1].c_str());
         }
         else if (button[1] == "IRIS") {
            model_->SetButtonIrisLabel(atoi(button[2].c_str()), tokens[1].c_str());
         }
         else if (button[1] == "FILTER") {
            if (button[2] == "W") {
               model_->SetButtonFilterWLabel(atoi(button[3].c_str()), tokens[1].c_str());
            } else if (button[2] == "T") {
               model_->SetButtonFilterTLabel(atoi(button[3].c_str()), tokens[1].c_str());
            }
         }
      }


   }
} 


std::vector<std::string>& DiskoveryListener::split(const std::string &s, char delim, std::vector<std::string> &elems) {
    std::stringstream ss(s);                                                 
    std::string item;                                                        
    while (std::getline(ss, item, delim)) {                                  
        elems.push_back(item);                                               
    }                                                                        
    return elems;                                                            
}                                                                            
                                                                             
std::vector<std::string> DiskoveryListener::split(const std::string &s, char delim) {
    std::vector<std::string> elems;                                          
    split(s, delim, elems);                                                  
    return elems;                                                            
}
