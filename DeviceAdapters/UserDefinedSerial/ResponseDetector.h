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
 *
 * This is a strategy class that provides different implementations for
 * receiving and checking responses.
 */
class ResponseDetector : boost::noncopyable
{
public:
   /**
    * \brief Create an instance for the named stragtegy.
    *
    * This version in the abstract base class calls the derived classes'
    * NewByName() functions to create the appropriate instance. If name is not
    * a known name, returns null.
    */
   static std::auto_ptr<ResponseDetector> NewByName(const std::string& name);

   virtual ~ResponseDetector() {}

   /**
    * \brief Return the method name.
    *
    * This is equal to the name passed to NewByName() to create the instance.
    */
   virtual std::string GetMethodName() const = 0;

   /**
    * \brief Receive and match to an expected response.
    * \return error code if could not receive or did not match.
    */
   virtual int RecvExpected(MM::Core* core, MM::Device* device,
         const std::string& port, const std::vector<char>& expected) = 0;

   /**
    * \brief Receive and match to one of a number of possible responses.
    * \param alternatives The possible responses to match against.
    * \param index The index of the matched alternative is returned.
    * \return error cde if could not receive or did not match.
    */
   virtual int RecvAlternative(MM::Core* core, MM::Device* device,
         const std::string& port,
         const std::vector< std::vector<char> >& alternatives,
         size_t& index) = 0;
};

/**
 * \brief Response detector that ignores all responses.
 */
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

/**
 * \brief Response detector for ASCII responses terminated with newlines.
 */
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

/**
 * \brief Common base for binary response detectors.
 */
class BinaryResponseDetector : public ResponseDetector
{
protected:
   int Recv(MM::Core* core, MM::Device* device, const std::string& port,
         size_t recvLen, std::vector<char>& response);
};

/**
 * \brief Response detector for fixed-length binary responses.
 */
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

/**
 * \brief Response detector for variable-length binary responses.
 *
 * The response length is determined from the expected response(s).
 */
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
