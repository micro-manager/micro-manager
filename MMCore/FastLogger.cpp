///////////////////////////////////////////////////////////////////////////////
// FILE:          FastLogger.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of the IMMLogger interface
// COPYRIGHT:     University of California, San Francisco, 2009
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
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
// AUTHOR:        Karl Hoover, karl.hoover@ucsf.edu, 2009 11 11




#include <stdarg.h>
#include <string>
#include <iostream>
#include <fstream>

#include <limits.h>

#include "FastLogger.h"
#include "CoreUtils.h"
#include "../MMDevice/DeviceUtils.h"

#include <boost/algorithm/string.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/thread/thread.hpp>

// TODO Stop relying on Boost internals
#include "boost/interprocess/detail/os_thread_functions.hpp" 
#include "boost/version.hpp"
#if BOOST_VERSION >= 104800
#  define BOOST_IPC_DETAIL boost::interprocess::ipcdetail
#else
#  define BOOST_IPC_DETAIL boost::interprocess::detail
#endif

using namespace std;
const char* g_textLogIniFiled = "Logging initialization failed\n";

class LoggerThread : public MMDeviceThreadBase
{
   public:
      LoggerThread(FastLogger* l) : log_(l), stop_(false) {}
      ~LoggerThread() {}
      int svc (void);

      void Stop() {stop_ = true;}
      void Start() {stop_ = false; activate();}

   private:
      FastLogger* log_;
      bool stop_;
};


int LoggerThread::svc(void)
{
   do
   {
		std::string stmp;
		{
			MMThreadGuard stringGuard(log_->logStringLock_g);
			stmp = log_->stringToWrite_g;
			log_->stringToWrite_g.clear();
		}

		if (0 < stmp.length())
		{
         if (log_->stderrLoggingEnabled_)
				std::cerr << stmp << '\n' << flush;
                                
			MMThreadGuard fileGuard(log_->logFileLock_g);
			if( NULL != log_->plogFile_g)
				*log_->plogFile_g << stmp << '\n' << flush;
		}
		CDeviceUtils::SleepMs(30);
   } while (!stop_ );

   return 0;
}


LoggerThread* pLogThread_g = NULL;


FastLogger::FastLogger()
:debugLoggingEnabled_(true)
,stderrLoggingEnabled_(true)
,fileLoggingEnabled_(false)
,failureReported(false),
plogFile_g(0)
{
}

FastLogger::~FastLogger()
{
   if( NULL != pLogThread_g)
   {
      pLogThread_g->Stop();
      pLogThread_g->wait();
      delete pLogThread_g;
      pLogThread_g = 0;
   }
   Shutdown();
}

/**
* methods declared in IMMLogger as pure virtual
* Refere to IMMLogger declaration
*/


bool FastLogger::Initialize(std::string logFileName, std::string logInstanceName)throw(IMMLogger::runtime_exception)
{
   bool bRet =false;
   try
   {
      failureReported=false;
      logInstanceName_=logInstanceName;
		{
			MMThreadGuard guard(logFileLock_g);
         bRet = Open(logFileName);
         if(bRet)
            fileLoggingEnabled_ = true;
		}

      if( NULL == pLogThread_g)
      {
		   pLogThread_g = new LoggerThread(this);
		   pLogThread_g->Start();
      }

   }
   catch(...)
   {
      ReportLogFailure();
      throw(IMMLogger::runtime_exception(g_textLogIniFiled));
   }
   return bRet;
};


void FastLogger::Shutdown()throw(IMMLogger::runtime_exception)
{
   try
   {
      MMThreadGuard guard(logFileLock_g);
      failureReported=false;

      if(NULL != plogFile_g)
      {
         fileLoggingEnabled_ = false;
         plogFile_g->close();
         delete plogFile_g;
         plogFile_g = NULL;
      }
   }
   catch(...)
   {
      plogFile_g = NULL;
      ReportLogFailure();
      throw(IMMLogger::runtime_exception(g_textLogIniFiled));
   }
}

bool FastLogger::Reset()throw(IMMLogger::runtime_exception)
{
   bool bRet =false;
   try{
		MMThreadGuard guard(logFileLock_g);
      failureReported=false;
      if(NULL != plogFile_g)
      {
         if (plogFile_g->is_open())
         {
            plogFile_g->close();
         }
         //re-open same file but truncate old log content
         plogFile_g->open(logFileName_.c_str(), ios_base::trunc);
         bRet = true;
      }
   }
   catch(...)
   {
      ReportLogFailure();
      throw(IMMLogger::runtime_exception(g_textLogIniFiled));
   }

   return bRet;
};

void FastLogger::SetPriorityLevel(bool includeDebug) throw()
{
   debugLoggingEnabled_ = includeDebug;
}

bool FastLogger::EnableLogToStderr(bool enable)throw()
{
   if (stderrLoggingEnabled_ == enable)
      return stderrLoggingEnabled_;

   bool bRet = stderrLoggingEnabled_;
   pLogThread_g->Stop();
   pLogThread_g->wait();
   stderrLoggingEnabled_ = enable;
	pLogThread_g->Start();

   return bRet;
};


const size_t MaxBuf = 32767;
struct BigBuffer { char buffer[MaxBuf]; };


void FastLogger::Log(bool isDebug, const char* format, ...) throw()
{
	{
		MMThreadGuard guard(logFileLock_g);
		if( NULL == plogFile_g) 
		{
			cerr<< " log file is NULL!" << endl;
			return;
		}
	}

	try
	{
      if (isDebug && !debugLoggingEnabled_)
			return;

		std::string entryPrefix = GetEntryPrefix(isDebug);

		std::auto_ptr<BigBuffer> pB (new BigBuffer());

		va_list argp;
      va_start(argp, format);
#ifdef WIN32
      int n = vsnprintf_s(pB->buffer, MaxBuf, MaxBuf - 1, format, argp);
#else
      int n = vsnprintf(pB->buffer, MaxBuf - 1, format, argp);
#endif
      va_end(argp);
      if (n <= -1) {
         ReportLogFailure();
         return;
      }

      std::string entryString = entryPrefix + pB->buffer;
      boost::algorithm::trim_right(entryString);

		{
			MMThreadGuard stringGuard(logStringLock_g);
			if ( 0 <stringToWrite_g.size())
				stringToWrite_g += '\n';
			stringToWrite_g += entryString;
		}
   }
   catch(...)
   {
      ReportLogFailure();
   }
};

void FastLogger::ReportLogFailure()throw()
{
   if(!failureReported)
   {
      failureReported=true;

      MMThreadGuard guard(logFileLock_g);
      try {
         std::cerr << g_textLogIniFiled;
      }
      catch (...) {
      }
   }
};


std::string FastLogger::GetEntryPrefix(bool isDebug)
{
   // date, pid, tid, and log level
   std::string entryPrefix;
   entryPrefix.reserve(100);

   // Date
   boost::posix_time::ptime bt = boost::posix_time::microsec_clock::local_time();
   std::string todaysDate = boost::posix_time::to_iso_extended_string(bt);
   entryPrefix += todaysDate;

   // PID
   // XXX Avoid using Boost 'detail' classes!
   BOOST_IPC_DETAIL::OS_process_id_t pidd = BOOST_IPC_DETAIL::get_current_process_id();
   entryPrefix += " p:";
   entryPrefix += boost::lexical_cast<std::string>(pidd);

   // TID
   entryPrefix += " t:";
   // Use the platform thread id where available, so that it can be compared
   // with debugger, etc.
#ifdef WIN32
   entryPrefix += boost::lexical_cast<std::string>(GetCurrentThreadId());
#else
   entryPrefix += boost::lexical_cast<std::string>(pthread_self());
#endif

   // Log level
   if (isDebug)
      entryPrefix += " [dbg] ";
   else
      entryPrefix += " [LOG] ";

   return entryPrefix;
}


bool FastLogger::Open(const std::string specifiedFile)
{

   bool bRet = false;
   try
   {
		if(NULL == plogFile_g)
		{
			plogFile_g = new std::ofstream();
		}
		if (!plogFile_g->is_open())
		{
         // N.B. we do NOT handle re-opening of the log file on a different path!!
         
         if(logFileName_.length() < 1) // if log file path has not yet been specified:
         {
            logFileName_ = specifiedFile;
         }

         // first try to open the specified file without any assumption about the path
	      plogFile_g->open(logFileName_.c_str(), ios_base::app);
         //std::cout << "first attempt to open  " << logFileName_.c_str() << (plogFile_g->is_open()?" OK":" FAILED")  << std::endl;

         // if the open failed, assume that this is because the ordinary user does not have write access to the application / program directory
	 if (!plogFile_g->is_open())
         {
            std::string homePath;
#ifdef _WINDOWS
            homePath = std::string(getenv("HOMEDRIVE")) + std::string(getenv("HOMEPATH")) + "\\";
#else
            homePath = std::string(getenv("HOME")) + "/";
#endif
            logFileName_ = homePath + specifiedFile;
				plogFile_g->open(logFileName_.c_str(), ios_base::app);
         }

		}
      else
      {
         ;//std::cout << "log file " << logFileName_.c_str() << " was open already" << std::endl;
      }

      bRet = plogFile_g->is_open();
   }
   catch(...){}
   return bRet;

}

void FastLogger::LogContents(char** ppContents, unsigned long& len)
{
   *ppContents = 0;
   len = 0;

   MMThreadGuard guard(logFileLock_g);

   if (plogFile_g->is_open())
   {
      plogFile_g->close();
   }

   // open to read, and position at the end of the file
   // XXX We simply return NULL if cannot open file or size is too large!
   std::ifstream ifile(logFileName_.c_str(), ios::in | ios::binary | ios::ate);
   if (ifile.is_open())
   {
      std::ifstream::pos_type pos = ifile.tellg();

      // XXX This is broken (sort of): on 64-bit Windows, we artificially
      // limit ourselves to 4 GB. But it is probably okay since we don't
      // expect the log contents to be > 4 GB. Fixing would require changing
      // the signature of this function.
      if (pos < ULONG_MAX)
      {
         len = static_cast<unsigned long>(pos);
         *ppContents = new char[len];
         if (0 != *ppContents)
         {
            ifile.seekg(0, ios::beg);
            ifile.read(*ppContents, len);
            ifile.close();
         }
      }
   }

   // re-open for logging
   plogFile_g->open(logFileName_.c_str(), ios_base::app);

   return;
}


std::string FastLogger::LogPath(void)
{
   return logFileName_;

}

