// DESCRIPTION:   Control devices using user-specified serial commands
//
// COPYRIGHT:     University of California San Francisco, 2014
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
//
// AUTHOR:        Mark Tsuchida

#include "ResponseDetector.h"

#include "UserDefinedSerialConstants.h"

#include <boost/lexical_cast.hpp>

#include <algorithm>
#include <iterator>
#include <memory>
#include <string>
#include <vector>


std::auto_ptr<ResponseDetector>
ResponseDetector::NewByName(const std::string& name)
{
   std::auto_ptr<ResponseDetector> newDetector;

   newDetector = IgnoringResponseDetector::NewByName(name);
   if (newDetector.get())
      return newDetector;

   newDetector = TerminatorResponseDetector::NewByName(name);
   if (newDetector.get())
      return newDetector;

   newDetector = FixedLengthResponseDetector::NewByName(name);
   if (newDetector.get())
      return newDetector;

   newDetector = VariableLengthResponseDetector::NewByName(name);
   if (newDetector.get())
      return newDetector;

   return std::auto_ptr<ResponseDetector>();
}


std::auto_ptr<ResponseDetector>
IgnoringResponseDetector::NewByName(const std::string& name)
{
   std::auto_ptr<ResponseDetector> ret;
   if (name == g_PropValue_ResponseIgnore)
      ret.reset(new IgnoringResponseDetector());
   return ret;
}


std::string
IgnoringResponseDetector::GetMethodName() const
{
   return g_PropValue_ResponseIgnore;
}


int
IgnoringResponseDetector::RecvExpected(MM::Core*, MM::Device*,
      const std::string&, const std::vector<char>&)
{
   return DEVICE_OK;
}


int
IgnoringResponseDetector::RecvAlternative(MM::Core*, MM::Device*,
      const std::string&, const std::vector< std::vector<char> >&, size_t&)
{
   return ERR_CANNOT_QUERY_IN_IGNORE_MODE;
}


std::auto_ptr<ResponseDetector>
TerminatorResponseDetector::NewByName(const std::string& name)
{
   std::auto_ptr<ResponseDetector> ret;
   if (name == g_PropValue_ResponseCRLFTerminated)
      ret.reset(new TerminatorResponseDetector("\r\n", "CRLF"));
   else if (name == g_PropValue_ResponseCRTerminated)
      ret.reset(new TerminatorResponseDetector("\r", "CR"));
   else if (name == g_PropValue_ResponseLFTerminated)
      ret.reset(new TerminatorResponseDetector("\n", "LF"));
   return ret;
}


std::string
TerminatorResponseDetector::GetMethodName() const
{
   return g_PropValuePrefix_ResponseTerminated + terminatorName_;
}


int
TerminatorResponseDetector::RecvExpected(MM::Core* core, MM::Device* device,
      const std::string& port, const std::vector<char>& expected)
{
   int err;
   std::vector<char> response;
   err = Recv(core, device, port, response);
   if (err != DEVICE_OK)
      return err;

   if (response != expected)
      return ERR_UNEXPECTED_RESPONSE;

   return DEVICE_OK;
}


int
TerminatorResponseDetector::RecvAlternative(MM::Core* core, MM::Device* device,
      const std::string& port,
      const std::vector< std::vector<char> >& alternatives, size_t& index)
{
   if (alternatives.empty())
      return ERR_NO_RESPONSE_ALTERNATIVES;

   int err;
   std::vector<char> response;
   err = Recv(core, device, port, response);
   if (err != DEVICE_OK)
      return err;

   std::vector< std::vector<char> >::const_iterator foundAlt =
      std::find(alternatives.begin(), alternatives.end(), response);
   if (foundAlt == alternatives.end())
      return ERR_UNEXPECTED_RESPONSE;
   index = std::distance(alternatives.begin(), foundAlt);

   return DEVICE_OK;
}


int
TerminatorResponseDetector::Recv(MM::Core* core, MM::Device* device,
      const std::string& port, std::vector<char>& response)
{
   if (!core)
      return DEVICE_NO_CALLBACK_REGISTERED;

   char buf[1024];
   int err = core->GetSerialAnswer(device, port.c_str(),
         sizeof(buf) - 1, buf, terminator_.c_str());
   buf[sizeof(buf) - 1] = '\0'; // Just in case
   if (err != DEVICE_OK)
      return err;

   response.clear();
   response.reserve(strlen(buf));
   char* p = buf;
   while (*p)
      response.push_back(*p++);

   return DEVICE_OK;
}


// Helper for BinaryResponseDetector::Recv()
static int
GetPortAnswerTimeout(MM::Core* core, MM::Device*,
      const std::string& port, double& timeoutMs)
{
   int err;
   char timeoutString[MM::MaxStrLength];
   err = core->GetDeviceProperty(port.c_str(), MM::g_Keyword_AnswerTimeout,
         timeoutString);
   if (err != DEVICE_OK)
      return ERR_CANNOT_GET_PORT_TIMEOUT;
   try
   {
      timeoutMs = boost::lexical_cast<double>(timeoutString);
   }
   catch (const boost::bad_lexical_cast&)
   {
      return ERR_CANNOT_GET_PORT_TIMEOUT;
   }
   if (timeoutMs < 0.0)
      return ERR_CANNOT_GET_PORT_TIMEOUT;
   return DEVICE_OK;
}


int
BinaryResponseDetector::Recv(MM::Core* core, MM::Device* device,
      const std::string& port, size_t recvLen, std::vector<char>& response)
{
   if (!core)
      return DEVICE_NO_CALLBACK_REGISTERED;

   response.clear();
   if (recvLen == 0)
      return DEVICE_OK;

   int err;

   // The binary interface does not use a timeout(!), so we need to handle that
   // ourselves.
   double timeoutMs;
   err = GetPortAnswerTimeout(core, device, port, timeoutMs);
   if (err != DEVICE_OK)
      return err;
   MM::MMTime deadline = core->GetCurrentMMTime() +
      MM::MMTime(1000.0 * timeoutMs);
   std::vector<char> buf(recvLen);
   do
   {
      unsigned char* bufPtr = reinterpret_cast<unsigned char*>(&buf[0]);
      unsigned long bytesRead = 0;
      err = core->ReadFromSerial(device, port.c_str(),
            bufPtr, static_cast<unsigned long>(buf.size()), bytesRead);
      if (err != DEVICE_OK)
         return err;

      std::copy(buf.begin(), buf.begin() + bytesRead,
            std::back_inserter(response));
   }
   while (response.size() < recvLen && core->GetCurrentMMTime() < deadline);

   if (response.size() < recvLen)
      return ERR_BINARY_SERIAL_TIMEOUT;

   return DEVICE_OK;
}


std::auto_ptr<ResponseDetector>
FixedLengthResponseDetector::NewByName(const std::string& name)
{
   std::auto_ptr<ResponseDetector> ret;
   const std::string prefix(g_PropValuePrefix_ResponseFixedByteCount);
   if (name.substr(0, prefix.size()) == prefix)
   {
      try
      {
         size_t byteCount =
            boost::lexical_cast<size_t>(name.substr(prefix.size()));
         ret.reset(new FixedLengthResponseDetector(byteCount));
      }
      catch (const boost::bad_lexical_cast&)
      {
         // Programming error; leave ret unset.
      }
   }
   return ret;
}


std::string
FixedLengthResponseDetector::GetMethodName() const
{
   return g_PropValuePrefix_ResponseFixedByteCount +
      boost::lexical_cast<std::string>(byteCount_);
}


int
FixedLengthResponseDetector::RecvExpected(MM::Core* core, MM::Device* device,
      const std::string& port, const std::vector<char>& expected)
{
   if (expected.size() != byteCount_)
      return ERR_EXPECTED_RESPONSE_LENGTH_MISMATCH;

   int err;
   std::vector<char> response;
   err = Recv(core, device, port, byteCount_, response);
   if (err != DEVICE_OK)
      return err;

   if (response != expected)
      return ERR_UNEXPECTED_RESPONSE;

   return DEVICE_OK;
}


int
FixedLengthResponseDetector::RecvAlternative(MM::Core* core,
      MM::Device* device, const std::string& port,
      const std::vector< std::vector<char> >& alternatives, size_t& index)
{
   if (alternatives.empty())
      return ERR_NO_RESPONSE_ALTERNATIVES;

   typedef std::vector< std::vector<char> >::const_iterator Iter;
   for (Iter it = alternatives.begin(), end = alternatives.end();
         it != end; ++it)
   {
      if (it->size() != byteCount_)
         return ERR_EXPECTED_RESPONSE_LENGTH_MISMATCH;
   }

   int err;
   std::vector<char> response;
   err = Recv(core, device, port, byteCount_, response);
   if (err != DEVICE_OK)
      return err;

   std::vector< std::vector<char> >::const_iterator foundAlt =
      std::find(alternatives.begin(), alternatives.end(), response);
   if (foundAlt == alternatives.end())
      return ERR_UNEXPECTED_RESPONSE;
   index = std::distance(alternatives.begin(), foundAlt);

   return DEVICE_OK;
}


std::auto_ptr<ResponseDetector>
VariableLengthResponseDetector::NewByName(const std::string& name)
{
   std::auto_ptr<ResponseDetector> ret;
   if (name == g_PropValue_ResponseVariableByteCount)
      ret.reset(new VariableLengthResponseDetector());
   return ret;
}


std::string
VariableLengthResponseDetector::GetMethodName() const
{
   return g_PropValue_ResponseVariableByteCount;
}


int
VariableLengthResponseDetector::RecvExpected(MM::Core* core,
      MM::Device* device, const std::string& port,
      const std::vector<char>& expected)
{
   if (expected.empty())
      return ERR_VAR_LEN_RESPONSE_MUST_NOT_BE_EMPTY;

   int err;
   std::vector<char> response;
   err = Recv(core, device, port, expected.size(), response);
   if (err != DEVICE_OK)
      return err;

   if (response != expected)
      return ERR_UNEXPECTED_RESPONSE;

   return DEVICE_OK;
}


int
VariableLengthResponseDetector::RecvAlternative(MM::Core* core,
      MM::Device* device, const std::string& port,
      const std::vector< std::vector<char> >& alternatives, size_t& index)
{
   if (alternatives.empty())
      return ERR_NO_RESPONSE_ALTERNATIVES;

   size_t recvLen = alternatives[0].size();
   typedef std::vector< std::vector<char> >::const_iterator Iter;
   for (Iter it = alternatives.begin(), end = alternatives.end();
         it != end; ++it)
   {
      if (it->size() != recvLen)
         return ERR_EXPECTED_RESPONSE_LENGTH_MISMATCH;
   }

   int err;
   std::vector<char> response;
   err = Recv(core, device, port, recvLen, response);
   if (err != DEVICE_OK)
      return err;

   std::vector< std::vector<char> >::const_iterator foundAlt =
      std::find(alternatives.begin(), alternatives.end(), response);
   if (foundAlt == alternatives.end())
      return ERR_UNEXPECTED_RESPONSE;
   index = std::distance(alternatives.begin(), foundAlt);

   return DEVICE_OK;
}
