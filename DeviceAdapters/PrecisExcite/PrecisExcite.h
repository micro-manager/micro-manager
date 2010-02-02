///////////////////////////////////////////////////////////////////////////////
// FILE:          PrecisExcite.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PrecisExcite controller adapter
// COPYRIGHT:     University of California, San Francisco, 2009
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
//
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 3/17/2009
//
//

#ifndef _PRECISEXCITE_H_
#define _PRECISEXCITE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
//#include <iostream>
#include <vector>
using namespace std;

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_POSITION         10002
#define ERR_PORT_CHANGE_FORBIDDEN    10004

enum TriggerType {OFF, RISING_EDGES, FALLING_EDGES, BOTH_EDGES, FOLLOW_PULSE};
string TriggerLabels[] = {"Off","RisingEdges","FallingEdges","BothEdges","FollowPulse"};
char TriggerCmd[] = {'Z', '+', '-', '*', 'X'};

class Controller : public CShutterBase<Controller>
{
public:
   Controller(const char* name);
   ~Controller();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   
   // action interface
   // ----------------
   int OnAddress(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   //int OnChannelLetter(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannelLabel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnConnectionType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReceivedData(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerSequence(MM::PropertyBase* pProp, MM::ActionType eAct);



   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);


private:

   long intensity_;
   long state_;
   int error_;

   bool initialized_;
   std::string name_;
   std::string port_;
   char currentChannelLetter_;
   string currentChannelLabel_;
   long currentChannel_;
   unsigned char buf_[1000];
   string buf_string_;
   vector<string> buf_tokens_;
   unsigned long buf_bytes_;
   long armState_;
   TriggerType triggerMode_;

   bool busy_;
   double answerTimeoutMs_;
   vector<char> channelLetters_;
   vector<string> channelLabels_;
   string triggerSequence_;
   string triggerMessage_;
   string trigger_;
   MM::MMTime changedTime_;


   void SetIntensity(long intensity, long index);
   void GetIntensity(long& intensity, long index);
   void ReadGreeting();
   int ReadChannelLabels();
   void GeneratePropertyIntensity();
   void GenerateChannelChooser();
   void GeneratePropertyTrigger();
   void GeneratePropertyTriggerSequence();
   void GeneratePropertyState();
   void SetState(long state);
   void GetState(long &state);
   void Illuminate();
   void SetTrigger();
   void StripString(string& StringToModify);
   void Send(string cmd);
   void ReceiveOneLine();
   void Purge();
   int HandleErrors();
   

   Controller& operator=(Controller& /*rhs*/) {assert(false); return *this;}
};


#endif // _PRECISEXCITE_H_