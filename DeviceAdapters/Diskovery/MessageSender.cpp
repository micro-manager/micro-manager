///////////////////////////////////////////////////////////////////////////////
// FILE:       MessageSender.cpp
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
 * Class that takes commands out of a BlockingQueue and sends them to 
 * the Diskovery controller (but only when it is not busy)
 */
MessageSender::MessageSender(
      MM::Device& device, 
      MM::Core& core, 
      std::string serialPort, 
      BlockingQueue<std::string>& blockingQueue,
      DiskoveryModel* model) :
   stop_(false),
   device_(device),
   core_(core),
   port_(serialPort),
   blockingQueue_(blockingQueue),
   model_(model)
{
}

MessageSender::~MessageSender()
{
   Shutdown();
}

void MessageSender::Shutdown()
{
   Stop();
   // send the magic command to finish off the sender thread
   blockingQueue_.push("END");
   wait();
}


int MessageSender::svc()
{
   while (!stop_) 
   {
      std::string command;
      blockingQueue_.wait_and_pop(command);
      if (command == "END")
         return 0;
      model_->WaitForDeviceBusy();
      core_.SetSerialCommand(&device_, port_.c_str(), command.c_str(), "\n");
      model_->SetDeviceBusy(true);
   }
   return 0;
}

void MessageSender::Start()
{
   stop_ = false;
   activate();
}

