///////////////////////////////////////////////////////////////////////////////
// FILE:          TCPIPPort.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   A serial port implementation that communicates over TCP/IP
//                Can be used to connect to devices on another computer over the network
//
// AUTHOR:        Lukas Lang
//
// COPYRIGHT:     2017 Lukas Lang
// LICENSE:       Licensed under the Apache License, Version 2.0 (the "License");
//                you may not use this file except in compliance with the License.
//                You may obtain a copy of the License at
//                
//                http://www.apache.org/licenses/LICENSE-2.0
//                
//                Unless required by applicable law or agreed to in writing, software
//                distributed under the License is distributed on an "AS IS" BASIS,
//                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//                See the License for the specific language governing permissions and
//                limitations under the License.

#pragma once

#include "boost/asio.hpp"

#include <istream>

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#define BOOST_ERROR 20000

#include "error_code.h"

#define ERR_BUFFER_OVERRUN 106
#define ERR_TERM_TIMEOUT 107
#define ERR_PORT_CHANGE_FORBIDDEN 109
#define ERR_PORT_NOTINITIALIZED 111

extern const char* deviceName;

class TCPIPPort : public CSerialBase<TCPIPPort>
{
public:
	TCPIPPort(int index);
	~TCPIPPort();

	//Device API
	bool Busy();
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;

	std::string GetStringName() const;

	//Serial port API
	MM::PortType GetPortType() const;
	int SetCommand(const char* command, const char* term);
	int GetAnswer(char* txt, unsigned maxChars, const char* term);
	int Write(const unsigned char* buf, unsigned long bufLen);
	int Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead);
	int Purge();

	//Action handlers
	int OnHost(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAnswerTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);

	void close_sock();

	static int GetCount();
	static void RegisterNewPort();
private:
	bool initialized_;

	int index_;

	static int count_;

	boost::asio::io_service ios_;
	boost::asio::ip::tcp::socket sock_;
	std::string host_;
	unsigned short port_;
	unsigned int answerTimeoutMs_;

	void LogAsciiCommunication(const char * prefix, bool isInput, const std::string & data);
	void LogBinaryCommunication(const char* prefix, bool isInput, const unsigned char* content, std::size_t length);
};