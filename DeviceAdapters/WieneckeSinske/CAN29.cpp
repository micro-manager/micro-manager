///////////////////////////////////////////////////////////////////////////////
// FILE:          CAN29.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Wienecke & Sinske CAN29 communication
//             
//
// AUTHOR:        S3L GmbH, info@s3l.de, www.s3l.de,  11/21/2017
// COPYRIGHT:     S3L GmbH, Rosdorf, 2017
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "CAN29.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

#include <assert.h>


///////////////////////////////////////////////////////////////////////////////
// CAN29Component
//
CAN29Component::CAN29Component(CAN29UByte canAddress, CAN29UByte devID, CAN29* can29):
canAddress_(canAddress),
	devID_(devID),
	can29_(can29)
{
	can29_->AddReceiveMessageHandler(this);
}

CAN29Component::~CAN29Component()
{
	can29_->RemoveReceiveMessageHandler(this);
}



///////////////////////////////////////////////////////////////////////////////
// Message
//
Message::Message(unsigned char canDst, unsigned char canSrc, unsigned char cmdCls, unsigned char cmdNr, unsigned char procID, unsigned char subID, unsigned char* data, int dataLen): 
CanSrc(canSrc),
	CanDst(canDst),
	CmdCls(cmdCls),
	CmdNr(cmdNr),
	ProcID(procID),
	SubID(subID),
	Data(data, data+dataLen)
{	
};





///////////////////////////////////////////////////////////////////////////////
// MessageTools:  Class containing CAN29 message tools
//
int MessageTools::GetString(std::vector<unsigned char>& data, int startindex, std::string& result)
{
	result = "";
	for (int i=startindex; i < data.size(); i++)
		result += data[i];

	return DEVICE_OK;
}


int MessageTools::GetULong(std::vector<unsigned char>& data, int startindex, CAN29ULong& result)
{
	result = 0;

	// are there enough data bytes
	if(startindex + CAN29LongSize > data.size())
		return ERR_INVALID_MESSAGE_DATA;

	CAN29ULong tmp;
	memcpy (&tmp, &data.at(startindex), CAN29LongSize);
	result =  (CAN29ULong) ntohl(tmp);

	return DEVICE_OK;
}

int MessageTools::GetLong(std::vector<unsigned char>& data, int startindex, CAN29Long& result)
{
	result = 0;

	// are there enough data bytes
	if(startindex + CAN29LongSize > data.size())
		return ERR_INVALID_MESSAGE_DATA;

	CAN29Long tmp;
	memcpy (&tmp, &data.at(startindex), CAN29LongSize);
	result = (CAN29Long) ntohl(tmp);

	return DEVICE_OK;
}




///////////////////////////////////////////////////////////////////////////////
// CAN29
//
CAN29::CAN29():
port_("Undefined"),
	portInitialized_(false),
	receiveThread_(0),
	hasSendReadAnswer_(false),
	sendReadAnswer_(),
	sendReadMessage_()
{	
}


CAN29::~CAN29()
{
	if (receiveThread_ != 0)
		delete(receiveThread_);
}



int CAN29::Initialize(MM::Device* device, MM::Core* core)
{
	device_ = device;
	core_ = core;

	ClearPort();

	receiveThread_ = new CAN29ReceiveThread(this);
	receiveThread_->Start();


	return DEVICE_OK;
}


int CAN29::Send(Message msg)
{
	assert(msg.Data.size() <= 253);   

	// Prepare command according to CAN29 Protocol
	std::vector<unsigned char> preparedCommand(msg.Data.size() + 20); // make provision for escaping special charactes
	int nextIdx = 0;
	preparedCommand[nextIdx++] = 0x10;				// message begin
	preparedCommand[nextIdx++] = 0x02;

	AppendByte(preparedCommand, nextIdx, msg.CanDst);	// target
	AppendByte(preparedCommand, nextIdx, msg.CanSrc);	// source
	AppendByte(preparedCommand, nextIdx, (unsigned char)(msg.Data.size()+2) );	// data length
	AppendByte(preparedCommand, nextIdx, msg.CmdCls);	// cmd class
	AppendByte(preparedCommand, nextIdx, msg.CmdNr);	// cmd number
	AppendByte(preparedCommand, nextIdx, msg.ProcID);	// proc id  (0)
	AppendByte(preparedCommand, nextIdx, msg.SubID);	// sub id	(1)

	for (int i=0; i< msg.Data.size(); i++)					// data
		AppendByte(preparedCommand, nextIdx, msg.Data[i]);

	preparedCommand[nextIdx++]=0x10;				// message end
	preparedCommand[nextIdx++]=0x03;

	// send command
	int ret = core_->WriteToSerial(device_, port_.c_str(), &(preparedCommand[0]), (unsigned long) nextIdx);
	if (ret != DEVICE_OK)                                                     
		return ret;                                                            

	return DEVICE_OK; 
}

int CAN29::SendRead(Message msg, Message& answer, int timeoutMilliSec)
{
	sendReadMessage_ = msg;
	hasSendReadAnswer_ = false;

	// send message out
	int res = Send(msg);
	if(res != DEVICE_OK)
		return res;

	// wait for answer
	MM::MMTime dTimeout = MM::MMTime (timeoutMilliSec*1000);
	MM::MMTime start = core_->GetCurrentMMTime();
	while(!hasSendReadAnswer_ && ((core_->GetCurrentMMTime() - start) < dTimeout)) 
	{
		CDeviceUtils::SleepMs(20);
	}
	if (!hasSendReadAnswer_)
		return ERR_TIMEOUT;

	// return answer
	answer = sendReadAnswer_;

	return DEVICE_OK;
}



int CAN29::Receive(Message msg)
{
	// check if it is an expected answer for SendRead function
	if(IsAnswer(sendReadMessage_, msg))
	{
		sendReadAnswer_ = msg;
		hasSendReadAnswer_ = true;
	}

	// call all registered ReceiveMessageHandlers
	for(int i= 0; i< receiveMessageCallbackClasses_.size(); i++)
		receiveMessageCallbackClasses_[i]->ReceiveMessageHandler(msg);

	return DEVICE_OK;
}


bool CAN29::IsAnswer(Message& question, Message& answer)
{
	return ((question.CanDst == answer.CanSrc) &&
		(question.CanSrc == answer.CanDst) &&
		((question.CmdCls & 0xF) == answer.CmdCls) &&
		(question.CmdNr == answer.CmdNr) &&
		(question.SubID == answer.SubID));
}



/*
Appends a data byte to the CAN29 command array. Special characters 0x10 and 0x0D are escaped
*/
int CAN29::AppendByte(std::vector<unsigned char>& command, int& nextIndex, unsigned char byte)
{
	// escape 0x10 and 0x0D
	if(byte == 0x10)
		command[nextIndex++] = 0x10;
	else if(byte == 0x0D)
		command[nextIndex++] = 0x10;

	// add data byte
	command[nextIndex++] = byte;

	return DEVICE_OK; 
}

int CAN29::ClearPort()
{
	// Clear contents of serial port 
	const unsigned int bufSize = 255;
	unsigned char clear[bufSize];
	unsigned long read = bufSize;
	int ret;
	while (read == bufSize)
	{
		ret = core_->ReadFromSerial(device_, port_.c_str(), clear, bufSize, read);
		if (ret != DEVICE_OK)
			return ret;
	}
	return DEVICE_OK;
} 


int CAN29::AddReceiveMessageHandler(CAN29Component* component)
{
	receiveMessageCallbackClasses_.push_back(component);
	return DEVICE_OK;
}

int CAN29::RemoveReceiveMessageHandler(CAN29Component* component)
{
	for(int i = 0; i< receiveMessageCallbackClasses_.size(); i++)
	{
		if(receiveMessageCallbackClasses_[i] == component)
		{
			receiveMessageCallbackClasses_.erase(receiveMessageCallbackClasses_.begin()+i);
			return DEVICE_OK;
		}
	}
	return DEVICE_OK;
}




///////////////////////////////////////////////////////////////////////////////
// CAN29MessageParser
//
//  Utility class for CAN29ReceiveThread
//  Takes an input stream and returns CAN29 messages in the GetNextMessage method
//
CAN29MessageParser::CAN29MessageParser(unsigned char* inputStream, long inputStreamLength) :
index_(0)
{
	inputStream_ = inputStream;
	inputStreamLength_ = inputStreamLength;
}

/*
* Find a message starting with 0x10 0x02 and ends with 0x10 0x03.  
* Strips escaped 0x10 and 0x0D chars (which are escaped with 0x10)
*/
int CAN29MessageParser::GetNextMessage(unsigned char* nextMessage, int& nextMessageLength) {
	bool startFound = false;
	bool endFound = false;
	bool tenFound = false;
	nextMessageLength = 0;
	long remainder = index_;
	while ( (endFound == false) && (index_ < inputStreamLength_) && (nextMessageLength < messageMaxLength_) ) {
		if (tenFound && (inputStream_[index_] == 0x02) ) {
			startFound = true;
			tenFound = false;
		}
		else if (tenFound && (inputStream_[index_] == 0x03) ) {
			endFound = true;
			tenFound = false;
		}
		else if (tenFound && (inputStream_[index_] == 0x10) ) {
			nextMessage[nextMessageLength] = inputStream_[index_];
			nextMessageLength++;
			tenFound = false;
		}
		else if (tenFound && (inputStream_[index_] == 0x0D) ) {
			nextMessage[nextMessageLength] = inputStream_[index_];
			nextMessageLength++;
			tenFound = false;
		}
		else if (inputStream_[index_] == 0x10)
			tenFound = true;
		else if (startFound) {
			nextMessage[nextMessageLength] = inputStream_[index_];
			nextMessageLength++;
			if (tenFound)
				tenFound = false;
		}
		index_++;
	}
	if (endFound)
		return 0;
	else {
		// no more complete message found, return the whole stretch we were considering:
		for (long i = remainder; i < inputStreamLength_; i++)
			nextMessage[i-remainder] = inputStream_[i];
		nextMessageLength = inputStreamLength_ - remainder;
		return -1;
	}
}



///////////////////////////////////////////////////////////////////////////////
// CAN29ReceiveThread
//
// Thread that continuously monitors messages from CAN29.
//
CAN29ReceiveThread::CAN29ReceiveThread(CAN29* can29) :
can29_ (can29),  
	stop_ (true),
	debug_(true),
	intervalUs_(10000) // check every 10 ms for new messages, 
{  
}

CAN29ReceiveThread::~CAN29ReceiveThread()
{
	Stop();
	wait();
	can29_->core_->LogMessage(can29_->device_, "Destructing CAN29ReceiveThread", true);
}

void CAN29ReceiveThread::interpretMessage(unsigned char* message)
{
	Message msg( message[0], message[1], message[3], message[4], message[5], message[6], &message[7], message[2]-2);
	can29_->Receive(msg);
}

int CAN29ReceiveThread::svc() {

	can29_->core_->LogMessage(can29_->device_, "Starting CAN29ReceiveThread", true);

	unsigned long dataLength;
	unsigned long charsRead = 0;
	unsigned long charsRemaining = 0;
	unsigned char rcvBuf[RCV_BUF_LENGTH];
	memset(rcvBuf, 0, RCV_BUF_LENGTH);

	while (!stop_) 
	{
		do { 
			dataLength = RCV_BUF_LENGTH - charsRemaining;
			int ret = can29_->core_->ReadFromSerial(can29_->device_, can29_->port_.c_str(), rcvBuf + charsRemaining, dataLength, charsRead); 

			if (ret != DEVICE_OK) 
			{
				std::ostringstream oss;
				oss << "CAN29ReceiveThread: ERROR while reading from serial port, error code: " << ret;
				can29_->core_->LogMessage(can29_->device_, oss.str().c_str(), false);
			} 
			else if (charsRead > 0) 
			{
				CAN29MessageParser parser(rcvBuf, charsRead + charsRemaining);
				do 
				{
					unsigned char message[RCV_BUF_LENGTH];
					int messageLength;
					ret = parser.GetNextMessage(message, messageLength);
					if (ret == 0) 
					{                  
						// Report 
						if (debug_) 
						{
							std::ostringstream os;
							os << "CAN29ReceiveThread incoming message: ";
							for (int i=0; i< messageLength; i++) 
							{
								os << std::hex << (unsigned int)message[i] << " ";
							}
							can29_->core_->LogMessage(can29_->device_, os.str().c_str(), true);
						}
						// and do the real stuff
						interpretMessage(message);
					}
					else 
					{
						// no more messages, copy remaining (if any) back to beginning of buffer
						if (debug_ && messageLength > 0) 
						{
							std::ostringstream os;
							os << "CAN29ReceiveThread no message found!: ";
							for (int i = 0; i < messageLength; i++) 
							{
								os << std::hex << (unsigned int)message[i] << " ";
								rcvBuf[i] = message[i];
							}
							can29_->core_->LogMessage(can29_->device_, os.str().c_str(), true);
						}
						memset(rcvBuf, 0, RCV_BUF_LENGTH);
						for (int i = 0; i < messageLength; i++) 
						{
							rcvBuf[i] = message[i];
						}
						charsRemaining = messageLength;
					}
				} while (ret == 0);
			}
		} 
		while ((charsRead != 0) && (!stop_)); 
		CDeviceUtils::SleepMs(intervalUs_/1000);
	}
	can29_->core_->LogMessage(can29_->device_, "CAN29ReceiveThread finished", true);
	return 0;
}

void CAN29ReceiveThread::Start()
{

	stop_ = false;
	activate();
}

