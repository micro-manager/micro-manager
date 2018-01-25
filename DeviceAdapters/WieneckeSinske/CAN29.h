///////////////////////////////////////////////////////////////////////////////
// FILE:          CAN29.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Wienecke & Sinske CAN29 communication
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
#ifndef _CAN29_H_
#define _CAN29_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>
#include <queue>


//using namespace boost::assign;

//////////////////////////////////////////////////////////////////////////////
// CAN addresses
//
#define CAN_PC			0x11
#define CAN_XAXIS		0x26
#define CAN_YAXIS		0x27
#define CAN_ZAXIS		0x0F

//////////////////////////////////////////////////////////////////////////////
// Command numbers
//
#define CMDNR_SYSTEM	0x02
#define CMDNR_AXIS		0x5F

//////////////////////////////////////////////////////////////////////////////
// Default process ID
//
#define PROCID  		0xAA


////////////
// Typedefs for the following CAN29 datatypes:
// TEXT (max 20 ascii characters, not null terminated)
// BYTE - 1 byte
typedef char CAN29Byte;
// UBYTE - 1 unsigned byte
typedef unsigned char CAN29UByte;
#define CAN29ByteSize 1
// SHORT - 2 Byte Motorola format
typedef short CAN29Short;
// USHORT - 2 Byte Motorola format, unsigned
typedef unsigned short CAN29UShort;
#define CAN29ShortSize 2
// LONG - 4 Byte Motorola format
typedef int  CAN29Long;
// ULONG - 4 Byte Motorola format, unsigned
typedef unsigned int  CAN29ULong;
#define CAN29LongSize 4
// FLOAT - 4 Byte Float Motorola format
typedef float  CAN29Float;
#define CAN29FloatSize 4
// DOUBLE - 8 Byte Double Motorola format
typedef double CAN29Double;
#define CAN29DoubleSize 8



//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004                                   
#define ERR_SET_POSITION_FAILED      10005                                   
#define ERR_INVALID_STEP_SIZE        10006                                   
#define ERR_LOW_LEVEL_MODE_FAILED    10007                                   
#define ERR_INVALID_MODE             10008 
#define ERR_MODULE_NOT_FOUND         10014
#define ERR_TIMEOUT                  10021
#define ERR_INVALID_MESSAGE_DATA     10022


static const int RCV_BUF_LENGTH = 1024;


class CAN29ReceiveThread;
class CAN29;


/*
Class representing a CAN29 message
*/
class Message
{
public: 
	Message(){};
	Message(unsigned char canDst, unsigned char canSrc, unsigned char cmdCls, unsigned char cmdNr, unsigned char procID, unsigned char subID, unsigned char* data, int dataLen);

	unsigned char CanSrc;
	unsigned char CanDst;
	unsigned char CmdCls;
	unsigned char CmdNr; 
	unsigned char ProcID;
	unsigned char SubID;
	std::vector<unsigned char> Data; 
};



/*
Base class for CAN29 components
*/
class CAN29Component 
{
public: 
	CAN29Component(CAN29UByte canAddress, CAN29UByte devID, CAN29* can29);
	~CAN29Component();

	virtual int ReceiveMessageHandler(Message& msg) = 0;

protected:
	CAN29UByte canAddress_;
	CAN29UByte devID_;
	CAN29* can29_;

};


/*
Class containing CAN29 message tools
*/
class MessageTools
{
public: 
	static int GetString(std::vector<unsigned char>& messageData, int startindex, std::string& result);
	static int GetULong(std::vector<unsigned char>& data, int startindex, CAN29ULong& result);
	static int GetLong(std::vector<unsigned char>& data, int startindex, CAN29Long& result);
};



/*
Class for CAN29 message IO
*/
class CAN29
{
public:
	std::string port_;
	bool portInitialized_;
	MM::Device* device_;
	MM::Core* core_;


	CAN29();
	~CAN29();

	int Initialize(MM::Device* device, MM::Core* core);
	int Send(Message msg);
	int SendRead(Message msg, Message& answer, int timeoutMilliSec = 1000);
	int Receive(Message msg);

	int AddReceiveMessageHandler(CAN29Component* component);
	int RemoveReceiveMessageHandler(CAN29Component* component);

private:
	CAN29ReceiveThread* receiveThread_;
	Message sendReadMessage_;
	Message sendReadAnswer_;
	bool hasSendReadAnswer_;

	bool IsAnswer(Message& question, Message& answer);
	int ClearPort();
	int AppendByte(std::vector<unsigned char>& command, int& nextIndex, unsigned char byte);

	std::vector<CAN29Component*> receiveMessageCallbackClasses_;
};


/*
* CAN29MessageParser: Takes a stream containing CAN29 messages and
* splits this stream into individual messages.
* Also removes escaped characters (like 0x10 0x10) 
*/
class CAN29MessageParser{
public:
	CAN29MessageParser(unsigned char* inputStream, long inputStreamLength);
	~CAN29MessageParser(){};
	int GetNextMessage(unsigned char* nextMessage, int& nextMessageLength);
	static const int messageMaxLength_ = 64;

private:
	unsigned char* inputStream_;
	long inputStreamLength_;
	long index_;
};


class CAN29ReceiveThread : public MMDeviceThreadBase
{
public:
	CAN29ReceiveThread(CAN29* can29); 
	~CAN29ReceiveThread(); 
	int svc();
	int open (void*) { return 0;}
	int close(unsigned long) {return 0;}

	void Start();
	void Stop() {stop_ = true;}

private:
	CAN29* can29_;
	void interpretMessage(unsigned char* message);
	bool stop_;
	long intervalUs_;
	bool debug_;

	CAN29ReceiveThread& operator=(CAN29ReceiveThread& /*rhs*/) {assert(false); return *this;}
};



#endif // _CAN29_H_
