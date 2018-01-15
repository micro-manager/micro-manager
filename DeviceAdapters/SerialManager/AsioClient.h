///////////////////////////////////////////////////////////////////////////////
// FILE:          AsioClient.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   boost::asio client 
//
// COPYRIGHT:     University of California, San Francisco, 2010
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
// AUTHOR:        Karl Hoover

#pragma once

#include "SerialManager.h"

#include "DeviceUtils.h"

#include <boost/asio.hpp>
#include <boost/asio/serial_port.hpp>
#include <boost/bind.hpp>
#include <boost/lexical_cast.hpp>

#include <deque>
#include <exception>
#include <string>
#include <vector>


#include <boost/version.hpp>
#if BOOST_VERSION >= 104700
typedef boost::asio::serial_port::native_handle_type SerialNativeHandle;
#else
typedef boost::asio::serial_port::native_type SerialNativeHandle;
#endif


class AsioClient 
{ 
public: 
   // Construct from an already open native handle.
   AsioClient(boost::asio::io_service& ioService,
         const std::string& deviceName,
         SerialNativeHandle nativeHandle,
         unsigned int baud,
         boost::asio::serial_port::flow_control::type flow,
         boost::asio::serial_port::parity::type parity,
         boost::asio::serial_port::stop_bits::type stopBits,
         SerialPort* pPort) :
      active_(true),
      io_service_(ioService),
      serialPortImplementation_(ioService, nativeHandle),
      pSerialPortAdapter_(pPort),
      device_(deviceName),
      shutDownInProgress_(false)
   {
      Construct(deviceName, baud, flow, parity, stopBits);
   }

   // Construct and open the given device name.
   AsioClient(boost::asio::io_service& ioService,
         unsigned int baud,
         const std::string& deviceName,
         boost::asio::serial_port::flow_control::type flow,
         boost::asio::serial_port::parity::type parity,
         boost::asio::serial_port::stop_bits::type stopBits,
         SerialPort* pPort) :
      active_(true),
      io_service_(ioService),
      serialPortImplementation_(ioService, deviceName),
      pSerialPortAdapter_(pPort),
      device_(deviceName),
      shutDownInProgress_(false)
   {
      Construct(deviceName, baud, flow, parity, stopBits);
   }

private:
   void Construct(const std::string& /* deviceName */ ,
         unsigned int baud,
         boost::asio::serial_port::flow_control::type flow,
         boost::asio::serial_port::parity::type parity,
         boost::asio::serial_port::stop_bits::type stopBits)
   {
      {
         MMThreadGuard g(implementationLock_);
         if (! serialPortImplementation_.is_open()) 
         { 
            LogMessage( "Failed to open serial port" , false);
            return; 
         } 
         boost::asio::serial_port_base::baud_rate baud_option(baud); 
         boost::system::error_code anError;

         ChangeBaudRate(baud);
         ChangeFlowControl(flow);
         ChangeParity(parity);
         ChangeStopBits(stopBits);

         serialPortImplementation_.set_option( boost::asio::serial_port_base::character_size( 8 ), anError ); 
         if( !!anError)
            LogMessage(("error setting character_size in AsioClient(): "+boost::lexical_cast<std::string,int>(anError.value()) + " " + anError.message()).c_str(), false);
      }

      ReadStart(); 
   } 

public:
   void ChangeFlowControl(const boost::asio::serial_port_base::flow_control::type& flow)
   {
      boost::system::error_code anError;

      // lexical_cast is useless here
      std::string sflow;
      switch( flow)
      {
      case boost::asio::serial_port_base::flow_control::none:
         sflow = "none";
         break;
      case boost::asio::serial_port_base::flow_control::software:
         sflow = "software";
         break;
      case boost::asio::serial_port_base::flow_control::hardware:
         sflow = "hardware";
         break;
      };
      LogMessage(("Attempting to set flow of " + device_ + " to " + sflow).c_str(), true);
      serialPortImplementation_.set_option(  boost::asio::serial_port_base::flow_control(flow) , anError ); 
      if( !!anError)
         LogMessage(("error setting flow_control in AsioClient(): "+boost::lexical_cast<std::string,int>(anError.value()) + " " + anError.message()).c_str(), false);
   }

   void ChangeParity(const boost::asio::serial_port_base::parity::type& parity)
   {
         
      boost::system::error_code anError;

      std::string sparity;
      switch( parity)
      {
      case boost::asio::serial_port_base::parity::none:
         sparity = "none";
         break;
      case boost::asio::serial_port_base::parity::odd:
         sparity = "odd";
         break;
      case boost::asio::serial_port_base::parity::even:
         sparity = "even";
         break;
      };
      LogMessage(("Attempting to set parity of " + device_ + " to " + sparity).c_str(), true);
      serialPortImplementation_.set_option( boost::asio::serial_port_base::parity(parity), anError); 
      if( !!anError)
         LogMessage(("error setting parity in AsioClient(): " + boost::lexical_cast<std::string,int>(anError.value()) + " " + anError.message()).c_str(), false);
   }

   void ChangeStopBits(const boost::asio::serial_port::stop_bits::type& stopBits)
   {
      boost::system::error_code anError;

      std::string sstopbits;
      switch( stopBits)
      {
      case boost::asio::serial_port_base::stop_bits::one:
         sstopbits = "1";
         break;
      case boost::asio::serial_port_base::stop_bits::onepointfive:
         sstopbits = "1.5";
         break;
      case boost::asio::serial_port_base::stop_bits::two:
         sstopbits = "2";
         break;
      };      
      LogMessage(("Attempting to set stopBits of " + device_ + " to " + sstopbits).c_str(), true);
      serialPortImplementation_.set_option( boost::asio::serial_port_base::stop_bits(stopBits), anError ); 
      if( !!anError)
         LogMessage(("error setting stop_bits in AsioClient(): "+boost::lexical_cast<std::string,int>(anError.value()) + " " + anError.message()).c_str(), false);
   }

   void ChangeBaudRate(unsigned int baud)
   {
      boost::system::error_code anError;
      boost::asio::serial_port_base::baud_rate baud_option(baud); 

#ifdef __APPLE__
         // Use ioctl() instead of Boost's implementation (which uses termios),
         // so that nonstandard baudrates can be set.
         speed_t speed = static_cast<speed_t>(baud);
         boost::asio::serial_port::native_handle_type portFd =
            serialPortImplementation_.native_handle();
         if (ioctl(portFd, IOSSIOSPEED, &speed))
         {
            const char* msg = strerror(errno);
            LogMessage((std::string("Error setting baud: ") + msg).c_str(),
                  false);
         }
#else
         serialPortImplementation_.set_option(baud_option, anError);
         if (!!anError)
         {
            LogMessage(("error setting baud: " +
                     boost::lexical_cast<std::string>(anError.value()) + " " +
                     anError.message()).c_str(), false);
         }
#endif

   }

   void WriteOneCharacterAsynchronously(const char ch)
   {
      io_service_.post(boost::bind(&AsioClient::DoWriteCh, this, ch));
   }

   void WriteCharactersAsynchronously(const char* pmsg, size_t len)
   {
      std::vector<char> msg(pmsg, pmsg + len);
      io_service_.post(boost::bind(&AsioClient::DoWriteMsg, this, msg));
   }


   bool WriteCharactersSynchronously(const char* msg, size_t len)
   { 
      bool retv = false;
      try
      {
         MMThreadGuard g(implementationLock_);
         retv = (len == boost::asio::write(  serialPortImplementation_, boost::asio::buffer(msg,len)));
      }
      catch( std::exception e)
      {
         LogMessage(e.what(), false);
      }
      return retv;
   } 

   bool WriteOneCharacterSynchronously(const char msg)
   { 
      bool retv = false;
      try
      {
         MMThreadGuard g(implementationLock_);
         retv = (1 == boost::asio::write(  serialPortImplementation_, boost::asio::buffer(&msg,1)));
      }
      catch( std::exception e)
      {
         LogMessage(e.what(), false);
      }
      return retv;
   } 

   void Close() // call the DoClose function via the io service
   { 
      if(active_)
      {
         io_service_.post(boost::bind(&AsioClient::DoClose, this, boost::system::error_code())); 
      }
   } 


   void Purge(void)
   {
      // clear read buffer;
      {
      MMThreadGuard g(readBufferLock_);
      data_read_.clear();
      }

      // clear write buffer
      {
      MMThreadGuard g(writeBufferLock_);
      write_msgs_.clear(); // buffered write data 
      }
   }


   // read one character, ret. is false if no characters are available.
   bool ReadOneCharacter(char& msg)
   {
      bool retval = false;
      MMThreadGuard g(readBufferLock_);
      if( 0 < data_read_.size())
      {
         retval = true;
         msg = data_read_.front();
         data_read_.pop_front();
      }
      return retval;
   }

   void ShutDownInProgress(const bool v){ shutDownInProgress_ = v;};


private: 
   // Call the owning device's LogMessage(). This is the only reason to keep a
   // pointer to the device object, and should be replaced by a functor for
   // logging only.
   void LogMessage(const char* msg, bool debug) const
   { pSerialPortAdapter_->LogMessage(msg, debug); }

   static const int max_read_length = 512; // maximum amount of data to read in one operation 
   void ReadStart(void) 
   { // Start an asynchronous read and call ReadComplete when it completes or fails 
      try
      {
         MMThreadGuard g(implementationLock_);
         serialPortImplementation_.async_read_some(boost::asio::buffer(read_msg_, max_read_length), 
            boost::bind(&AsioClient::ReadComplete, 
            this, 
            boost::asio::placeholders::error, 
            boost::asio::placeholders::bytes_transferred)); 
      }
      catch(std::exception e)
      {
         LogMessage(e.what(), false);
      }
   } 

   void ReadComplete(const boost::system::error_code& error, size_t bytes_transferred) 
   { // the asynchronous read operation has now completed or failed and returned an error 
      if (!error) 
      { // read completed, so process the data 
         {
            MMThreadGuard g(readBufferLock_);
            for(unsigned int ib = 0; ib < bytes_transferred; ++ib)
            {
               data_read_.push_back(read_msg_[ib]);
            }
         }
         CDeviceUtils::SleepMs(1);
         ReadStart(); // start waiting for another asynchronous read again 
      } 
      else 
      {
         // this is a normal situtation when closing the port 
         if( ! shutDownInProgress_)
            LogMessage(("error in ReadComplete: "+boost::lexical_cast<std::string,int>(error.value()) + " " + error.message()).c_str(), false);
         DoClose(error); 
      }
   } 


   // for asynchronous write operations:
   void DoWriteMsg(const std::vector<char>& msg)
   { // callback to handle write call from outside this class 
      MMThreadGuard writeBufferGuard(writeBufferLock_);
      bool write_in_progress = !write_msgs_.empty(); // is there anything currently being written?
      write_msgs_.push_back(msg); // store in write buffer

      if (!write_in_progress) // if nothing is currently being written, then start 
         WriteStart(); 
   } 

   void DoWriteCh(const char ch)
   {
      std::vector<char> msg(1, ch);
      DoWriteMsg(msg);
   }

   // Must be called with writeBufferLock_ acquired!
   void WriteStart(void) 
   { // Start an asynchronous write and call WriteComplete when it completes or fails 
      boost::asio::async_write(serialPortImplementation_, 
         boost::asio::buffer(&write_msgs_.front()[0], write_msgs_.front().size()),
         boost::bind(&AsioClient::WriteComplete, 
         this, 
         boost::asio::placeholders::error)); 
   } 

   void WriteComplete(const boost::system::error_code& error) 
   { // the asynchronous read operation has now completed or failed and returned an error 
      if (!error) 
      { // write completed, so send next write data 
         MMThreadGuard writeBufferGuard(writeBufferLock_);
         if (0 < write_msgs_.size()) // Should always be true, unless purged
            write_msgs_.pop_front(); // remove the completed data
         if (!write_msgs_.empty()) // if there is anthing left to be written
            WriteStart(); // then start sending the next item in the buffer 
      } 
      else 
      {
         LogMessage("error in WriteComplete: ", true);
         DoClose(error); 
      }
   } 



   void DoClose(const boost::system::error_code& error) 
   { // something has gone wrong, so close the socket & make this object inactive 
      if (error == boost::asio::error::operation_aborted) // if this call is the result of a timer cancel() 
         return; // ignore it because the connection cancelled the timer 
      if (error) 
      {
         LogMessage(error.message().c_str(), false);
      }
      else 
      {
         // this is a normal condition when shutting down port 
         if( ! shutDownInProgress_)
            LogMessage("Error: Connection did not succeed", false);
      }

      if(active_)
      {
         MMThreadGuard g(implementationLock_);
         serialPortImplementation_.close(); 
      }
      active_ = false; 
   } 


private: 
   bool active_; // remains true while this object is still operating 
   boost::asio::io_service& io_service_; // the main IO service that runs this connection 
   boost::asio::serial_port serialPortImplementation_; // the serial port this instance is connected to 
   char read_msg_[max_read_length]; // data read from the socket 
   std::deque< std::vector<char> > write_msgs_; // buffered write data
   std::deque<char> data_read_;
   SerialPort* pSerialPortAdapter_;
   std::string device_;

   MMThreadLock readBufferLock_;
   MMThreadLock writeBufferLock_;
   MMThreadLock implementationLock_;
   bool shutDownInProgress_;
};
