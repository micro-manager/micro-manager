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

#pragma once

#include "MMDevice.h"

#include <boost/utility.hpp>

#include <memory>
#include <string>
#include <vector>


/**
 * \brief Interface for serial response detection.
 */
class ResponseDetector : boost::noncopyable
{
public:
   static std::auto_ptr<ResponseDetector> NewByName(const std::string& name);

   virtual ~ResponseDetector() {}

   virtual std::string GetMethodName() const = 0;

   /**
    * \brief Receive and match to an expected response.
    * \return error code if could not receive or did not match
    */
   virtual int RecvExpected(MM::Core* core, MM::Device* device,
         const std::string& port, const std::vector<char>& expected) = 0;

   /**
    * \brief Receive and match to one of a number of possible responses.
    */
   virtual int RecvAlternative(MM::Core* core, MM::Device* device,
         const std::string& port,
         const std::vector< std::vector<char> >& alternatives,
         size_t& index) = 0;
};

class IgnoringResponseDetector : public ResponseDetector
{
public:
   static std::auto_ptr<ResponseDetector> NewByName(const std::string& name);

   virtual std::string GetMethodName() const;
   virtual int RecvExpected(MM::Core* core, MM::Device* device,
         const std::string& port, const std::vector<char>& expected);
   virtual int RecvAlternative(MM::Core* core, MM::Device* device,
         const std::string& port,
         const std::vector< std::vector<char> >& alternatives,
         size_t& index);

private:
   IgnoringResponseDetector() {}
};

class TerminatorResponseDetector : public ResponseDetector
{
   std::string terminator_;
   std::string terminatorName_;

public:
   static std::auto_ptr<ResponseDetector> NewByName(const std::string& name);

   virtual std::string GetMethodName() const;
   virtual int RecvExpected(MM::Core* core, MM::Device* device,
         const std::string& port, const std::vector<char>& expected);
   virtual int RecvAlternative(MM::Core* core, MM::Device* device,
         const std::string& port,
         const std::vector< std::vector<char> >& alternatives,
         size_t& index);

private:
   TerminatorResponseDetector(const char* terminator,
         const char* terminatorName) :
      terminator_(terminator), terminatorName_(terminatorName)
   {}
   int Recv(MM::Core* core, MM::Device* device, const std::string& port,
         std::vector<char>& response);
};

class BinaryResponseDetector : public ResponseDetector
{
protected:
   int Recv(MM::Core* core, MM::Device* device, const std::string& port,
         size_t recvLen, std::vector<char>& response);
};

class FixedLengthResponseDetector : public BinaryResponseDetector
{
   size_t byteCount_;

public:
   static std::auto_ptr<ResponseDetector> NewByName(const std::string& name);

   virtual std::string GetMethodName() const;
   virtual int RecvExpected(MM::Core* core, MM::Device* device,
         const std::string& port, const std::vector<char>& expected);
   virtual int RecvAlternative(MM::Core* core, MM::Device* device,
         const std::string& port,
         const std::vector< std::vector<char> >& alternatives,
         size_t& index);

private:
   FixedLengthResponseDetector(size_t byteCount) : byteCount_(byteCount) {}
};

class VariableLengthResponseDetector : public BinaryResponseDetector
{
public:
   static std::auto_ptr<ResponseDetector> NewByName(const std::string& name);

   virtual std::string GetMethodName() const;
   virtual int RecvExpected(MM::Core* core, MM::Device* device,
         const std::string& port, const std::vector<char>& expected);
   virtual int RecvAlternative(MM::Core* core, MM::Device* device,
         const std::string& port,
         const std::vector< std::vector<char> >& alternatives,
         size_t& index);

private:
   VariableLengthResponseDetector() {}
};
