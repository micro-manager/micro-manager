///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIHub.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI serial communication class (generic "hub")
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 09/2013
//
// BASED ON:      ASIStage.h
//

#ifndef _ASIHub_H_
#define _ASIHub_H_

#include "ASIDevice.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>

using namespace std;

////////////////////////////////////////////////////////////////
// *********** generic ASI comm class *************************
// implements a "hub" device with communication abilities
// also acts like a device itself
////////////////////////////////////////////////////////////////

class ASIHub : public HubBase<ASIHub>, public ASIDevice
{
public:
	ASIHub();
	~ASIHub() { }

	// Property handlers
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);

	// Communication base functions
   int ClearComPort();

   // SendCommand doesn't look for response
	int SendCommand(const char *command);
   int SendCommand(const string &command) { return SendCommand(command.c_str()); }

   // QueryCommand also gets the response (optional 2nd parameter is the response's termination string)
   int QueryCommand(const char *command, const char *replyTerminator); // all variants call this
   int QueryCommand(const char *command) { return QueryCommand(command, g_SerialTerminatorDefault); }
   int QueryCommand(const string &command) { return QueryCommand(command.c_str(), g_SerialTerminatorDefault); }
   int QueryCommand(const string &command, const string &replyTerminator) { return QueryCommand(command.c_str(), replyTerminator.c_str()); }

   // QueryCommandVerify gets the response and makes sure the first characters match expectedReplyPrefix
   int QueryCommandVerify(const char *command, const char *expectedReplyPrefix, const char *replyTerminator); // all variants call this
   int QueryCommandVerify(const char *command, const char *expectedReplyPrefix)
      { return QueryCommandVerify(command, expectedReplyPrefix, g_SerialTerminatorDefault); }
   int QueryCommandVerify(const string &command, const string &expectedReplyPrefix)
      { return QueryCommandVerify(command.c_str(), expectedReplyPrefix.c_str(), g_SerialTerminatorDefault); }
   int QueryCommandVerify(const string &command, const string &expectedReplyPrefix, const string &replyTerminator)
      { return QueryCommandVerify(command.c_str(), expectedReplyPrefix.c_str(), replyTerminator.c_str()); }

   // accessing serial commands and answers
   string LastSerialAnswer() const { return serialAnswer_; }
   string LastSerialCommand() const { return serialCommand_; }
   void SetLastSerialAnswer(string s) { serialAnswer_ = s; }  // used to parse subsets of full answer for commands like PZINFO using "Split" functions

	// Interpreting serial response
	double ParseAnswerAfterEquals() const;  // finds next number after equals sign and returns as float
   double ParseAnswerAfterColon() const;  // finds next number after colon and returns as float
	double ParseAnswerAfterPosition(unsigned int pos) const;  // finds next number after character position specified and returns as float
   vector<string> SplitAnswerOnDelim(string delim) const;  // splits answer on arbitrary delimeter list (any of included characters will split)
	vector<string> SplitAnswerOnCR() const { return SplitAnswerOnDelim("\r"); }
   vector<string> SplitAnswerOnSpace() const { return SplitAnswerOnDelim(" "); }

   // action/property handlers
   int OnSerialTerminator           (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialCommand              (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialCommandRepeatDuration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialCommandRepeatPeriod  (MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   string port_;         // port to use for communication

private:
	int ParseErrorReply() const;
	string EscapeControlCharacters(const string v ) const;
	string UnescapeControlCharacters(const string v0 ) const;

   string serialAnswer_;      // the last answer received
   string serialCommand_;     // the last command sent, or can be set for calling commands without args
   string serialTerminator_;  // only used when parsing command sent via OnSerialCommand action handler
   long serialRepeatDuration_; // for how long total time the command is repeatedly sent
   long serialRepeatPeriod_;  // how often in ms the command is sent
};



#endif //_ASIHub_H_
