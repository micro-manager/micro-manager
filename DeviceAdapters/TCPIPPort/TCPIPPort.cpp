///////////////////////////////////////////////////////////////////////////////
// FILE:          TCPIPPort.cpp
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

#include "TCPIPPort.h"

#include "boost/lexical_cast.hpp"
#include "boost/format.hpp"
#include "boost/lambda/bind.hpp"
#include "boost/lambda/lambda.hpp"

#include "Util.h"

using boost::asio::ip::tcp;

const char* deviceName = "TCP/IP serial port adapter";

int TCPIPPort::count_ = 0;

TCPIPPort::TCPIPPort(int index) :
	index_(index),
	host_("127.0.0.1"),
	port_(0),
	initialized_(false),
	sock_(ios_),
	answerTimeoutMs_(500)
{
	SetErrorText(ERR_BUFFER_OVERRUN, "Buffer overrun occured during read");
	SetErrorText(ERR_TERM_TIMEOUT, "Timeout occured during init or read");
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Cannot change host/port after initialization");
	SetErrorText(ERR_PORT_NOTINITIALIZED, "Operation failed. Port not inititalized");

	CreateProperty("Host", "127.0.0.1", MM::String, false, new CPropertyAction(this, &TCPIPPort::OnHost), true);
	CreateProperty("TCP Port", "0", MM::Integer, false, new CPropertyAction(this, &TCPIPPort::OnPort), true);
	CreateProperty("Answer timeout", "500", MM::Integer, false, new CPropertyAction(this, &TCPIPPort::OnAnswerTimeout), false);
}

TCPIPPort::~TCPIPPort()
{
}

bool TCPIPPort::Busy()
{
	return false;
}

void TCPIPPort::close_sock()
{
	sock_.close();
}

int TCPIPPort::Initialize()
{
ERRH_START
	if (initialized_)
		return DEVICE_OK;

	tcp::endpoint endpoint(boost::asio::ip::address::from_string(host_), port_);

	tcp::resolver::iterator it = tcp::resolver(ios_).resolve(endpoint);

	boost::system::error_code ec = boost::asio::error::would_block;

	boost::asio::deadline_timer deadline(ios_);
	deadline.expires_from_now(boost::posix_time::millisec(answerTimeoutMs_));
	deadline.async_wait(boost::lambda::bind(&TCPIPPort::close_sock, this));
	
	boost::asio::async_connect(sock_, it, boost::lambda::var(ec) = boost::lambda::_1);

	do ios_.run_one(); while (ec == boost::asio::error::would_block);

	if (ec || !sock_.is_open())
		return ERR_TERM_TIMEOUT;

	initialized_ = true;

	if (index_ == GetCount())
		RegisterNewPort();
ERRH_END
}

int TCPIPPort::Shutdown()
{
ERRH_START
	if (!initialized_)
		return DEVICE_OK;

	sock_.shutdown(tcp::socket::shutdown_both);
	sock_.close();

	initialized_ = false;
ERRH_END
}

void TCPIPPort::GetName(char* name) const
{
	strcpy(name, GetStringName().c_str());
}

std::string TCPIPPort::GetStringName() const
{
	return to_string(deviceName) + " (" + to_string(index_) + ")";
}

MM::PortType TCPIPPort::GetPortType() const
{
	return MM::SerialPort;
}

int TCPIPPort::SetCommand(const char* command, const char* term)
{
	ERRH_START
		if (!initialized_)
			return ERR_PORT_NOTINITIALIZED;

	std::string cmd(command);

	if (term != 0)
		cmd += term;

	boost::asio::write(sock_, boost::asio::buffer(cmd));

	LogAsciiCommunication("SetCommand", false, cmd);
	ERRH_END
}

//mostly copied from SerialManager.cpp (Serialport::GetAnswer)
int TCPIPPort::GetAnswer(char* txt, unsigned maxChars, const char* term)
{
ERRH_START
	if (!initialized_)
		return ERR_PORT_NOTINITIALIZED;

	if (maxChars < 1)
	{
		LogMessage("BUFFER_OVERRUN error occured!");
		return ERR_BUFFER_OVERRUN;
	}
	std::ostringstream logMsg;
	unsigned long answerOffset = 0;
	memset(txt, 0, maxChars);
	char theData = 0;

	MM::MMTime startTime = GetCurrentMMTime();
	MM::MMTime answerTimeout(answerTimeoutMs_ * 1000.0);
	MM::MMTime nonTerminatedAnswerTimeout(5.0 * 1000.0); // For bug-compatibility
	while ((GetCurrentMMTime() - startTime) < answerTimeout)
	{
		boost::asio::socket_base::bytes_readable command(true);
		sock_.io_control(command);
		size_t bytes_readable = command.get();

		if (bytes_readable > 0)
		{
			sock_.read_some(boost::asio::buffer(&theData, 1));

			if (maxChars <= answerOffset)
			{
				txt[answerOffset] = '\0';
				LogMessage("BUFFER_OVERRUN error occured!");
				return ERR_BUFFER_OVERRUN;
			}
			txt[answerOffset++] = theData;
		}
		else
		{
			//Yield to other threads:
			CDeviceUtils::SleepMs(1);
		}

		// look for the terminator, if any
		if (term && term[0])
		{
			// check for terminating sequence
			char* termPos = strstr(txt, term);
			if (termPos != 0) // found the terminator
			{
				LogAsciiCommunication("GetAnswer", true, txt);

				// erase the terminator from the answer:
				*termPos = '\0';

				return DEVICE_OK;
			}
		}
		else
		{
			// XXX Shouldn't it be an error to not have a terminator?
			// TODO Make it a precondition check (immediate error) once we've made
			// sure that no device adapter calls us without a terminator. For now,
			// keep the behavior for the sake of bug-compatibility.

			MM::MMTime elapsed = GetCurrentMMTime() - startTime;
			if (elapsed > nonTerminatedAnswerTimeout)
			{
				LogAsciiCommunication("GetAnswer", true, txt);
				long millisecs = static_cast<long>(elapsed.getMsec());
				LogMessage(("GetAnswer without terminator returning after " +
					boost::lexical_cast<std::string>(millisecs) +
					"msec").c_str(), true);
				return DEVICE_OK;
			}
		}
	}

	LogMessage("TERM_TIMEOUT error occured!");
	return ERR_TERM_TIMEOUT;
	ERRH_END
}

int TCPIPPort::Write(const unsigned char* buf, unsigned long bufLen)
{
	ERRH_START
		if (!initialized_)
			return ERR_PORT_NOTINITIALIZED;

	boost::asio::write(sock_, boost::asio::buffer(buf, bufLen));

	LogBinaryCommunication("Write", false, buf, bufLen);
	ERRH_END
}

int TCPIPPort::Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead)
{
	ERRH_START
		if (!initialized_)
			return ERR_PORT_NOTINITIALIZED;

	if (bufLen < 0)
		return ERR_BUFFER_OVERRUN;

	memset(buf, 0, bufLen);

	charsRead = (unsigned long)boost::asio::read(sock_, boost::asio::buffer(buf, bufLen));

	if (charsRead > 0)
		LogBinaryCommunication("Read", true, buf, charsRead);
	ERRH_END
}

int TCPIPPort::Purge()
{
	return DEVICE_OK;
}

int TCPIPPort::OnHost(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(host_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (initialized_)
		{
			// revert
			pProp->Set(host_.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}
		pProp->Get(host_);
	}

	return DEVICE_OK;
}

int TCPIPPort::OnPort(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(to_string(port_).c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (initialized_)
		{
			// revert
			pProp->Set(to_string(port_).c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}
		std::string s;
		pProp->Get(s);
		port_ = (short)atoi(s.c_str());
	}

	return DEVICE_OK;
}

int TCPIPPort::OnAnswerTimeout(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(to_string(answerTimeoutMs_).c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		std::string s;
		pProp->Get(s);
		answerTimeoutMs_ = atoi(s.c_str());
	}

	return DEVICE_OK;
}

int TCPIPPort::GetCount()
{
	return count_;
}

void TCPIPPort::RegisterNewPort()
{
	RegisterDevice(TCPIPPort(++count_).GetStringName().c_str(), MM::SerialDevice, "TCP/IP serial port adapter");
}

// Helper functions for message logging (from SerialManager.cpp)
// (TODO: Do these have any utility outside of SerialManager?)

// Note: This returns true for ' ' (space).
static bool ShouldEscape(char ch)
{
	if (ch >= 0 && std::isgraph(ch))
		if (std::string("\'\"\\").find(ch) == std::string::npos)
			return false;
	return true;
}

static void PrintEscaped(std::ostream& strm, char ch)
{
	switch (ch)
	{
		// We leave out some less common C escape sequences that are more handy
		// to read as hex values (\a, \b, \f, \v).
	case '\'': strm << "\\\'"; break;
	case '\"': strm << "\\\""; break;
	case '\\': strm << "\\\\"; break;
	case '\0': strm << "\\0"; break;
	case '\n': strm << "\\n"; break;
	case '\r': strm << "\\r"; break;
	case '\t': strm << "\\t"; break;
	default:
	{
		// boost::format doesn't work with "%02hhx". Also note that the
		// reinterpret_cast to unsigned char is necessary to prevent sign
		// extension.
		unsigned char byte = *reinterpret_cast<unsigned char*>(&ch);
		strm << boost::format("\\x%02x") % static_cast<unsigned int>(byte);
		break;
	}
	}
}

static void FormatAsciiContent(std::ostream& strm, const char* begin, const char* end)
{
	// We log ASCII data in an unambiguous format free of control characters and
	// spaces. The format used is a valid C escaped string (without the
	// surrounding quotes), with the exception of '?' not being escaped even if
	// it constitutes part of a trigraph.

	// We want to escape leading and trailing spaces, but not internal spaces,
	// for maximum readability and zero ambiguity.
	bool hasEncounteredNonSpace = false;
	unsigned pendingSpaces = 0;

	for (const char* p = begin; p != end; ++p)
	{
		if (*p == ' ')
		{
			if (!hasEncounteredNonSpace)
				PrintEscaped(strm, ' '); // Leading space
			else
				++pendingSpaces; // Don't know yet if internal or trailing space
		}
		else // *p != ' '
		{
			if (!hasEncounteredNonSpace)
				hasEncounteredNonSpace = true;
			else
			{
				while (pendingSpaces > 0)
				{
					strm << ' '; // Internal space
					--pendingSpaces;
				}
			}

			if (ShouldEscape(*p))
				PrintEscaped(strm, *p);
			else
				strm << *p;
		}
	}

	while (pendingSpaces > 0)
	{
		PrintEscaped(strm, ' '); // Trailing space
		--pendingSpaces;
	}
}

static void PrintCommunicationPrefix(std::ostream& strm, const char* prefix, bool isInput)
{
	strm << prefix;
	strm << (isInput ? " <- " : " -> ");
}

void TCPIPPort::LogAsciiCommunication(const char* prefix, bool isInput, const std::string& data)
{
	std::ostringstream oss;
	PrintCommunicationPrefix(oss, prefix, isInput);
	FormatAsciiContent(oss, data.c_str(), data.c_str() + data.size());
	LogMessage(oss.str().c_str(), true);
}

static void FormatBinaryContent(std::ostream& strm, const unsigned char* begin, const unsigned char* end)
{
	for (const unsigned char* p = begin; p != end; ++p)
	{
		if (p != begin)
			strm << ' ';
		strm << boost::format("%02x") % static_cast<unsigned int>(*p);
	}
}

void TCPIPPort::LogBinaryCommunication(const char* prefix, bool isInput, const unsigned char* pdata, std::size_t length)
{
	std::ostringstream oss;
	PrintCommunicationPrefix(oss, prefix, isInput);
	oss << "(hex) ";
	FormatBinaryContent(oss, pdata, pdata + length);
	LogMessage(oss.str().c_str(), true);
}
