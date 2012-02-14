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
//
// CVS:           $Id$
//

// PRE-REQUISITES: 
//#include "../../MMDevice/DeviceUtils.h"
//#include <sstream>
//#include <cstdio>
//
//#include <deque> 
//#include <iostream> 
//#include <boost/bind.hpp> 
//#include <boost/asio.hpp> 
//#include <boost/asio/serial_port.hpp> 
//#include <boost/thread.hpp> 

#ifndef ASIOCLIENT_H
#define ASIOCLIENT_H

bool bfalse_s = false;

class AsioClient 
{ 
public: 
   AsioClient(boost::asio::io_service& io_service, unsigned int baud, const std::string& device, 
      const boost::asio::serial_port_base::flow_control::type& flow,
      const boost::asio::serial_port_base::parity::type& parity,
      const boost::asio::serial_port_base::stop_bits::type& stopBits,
      SerialPort* pPort) 
      : active_(true), 
      io_service_(io_service), 
      serialPortImplementation_(io_service, device),
      pSerialPortAdapter_(pPort),
      shutDownInProgress_(false),
      device_(device)
   { 
      do // just a scope for the guard
      {
         MMThreadGuard g(implementationLock_);
         if (! serialPortImplementation_.is_open()) 
         { 
            pSerialPortAdapter_->LogMessage( "Failed to open serial port" , false);
            return; 
         } 
         boost::asio::serial_port_base::baud_rate baud_option(baud); 
         boost::system::error_code anError;

         pSerialPortAdapter_->LogMessage(("Attempting to set baud of " + device + " to " + boost::lexical_cast<std::string,int>(baud)).c_str(), true);
         serialPortImplementation_.set_option(baud_option, anError); // set the baud rate after the port has been opened 
         if( !!anError)
            pSerialPortAdapter_->LogMessage(("error setting baud in AsioClient(): "+boost::lexical_cast<std::string,int>(anError.value()) + " " + anError.message()).c_str(), false);

         ChangeFlowControl(flow);

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
         pSerialPortAdapter_->LogMessage(("Attempting to set parity of " + device + " to " + sparity).c_str(), true);
         serialPortImplementation_.set_option( boost::asio::serial_port_base::parity(parity), anError); 
         if( !!anError)
            pSerialPortAdapter_->LogMessage(("error setting parity in AsioClient(): " + boost::lexical_cast<std::string,int>(anError.value()) + " " + anError.message()).c_str(), false);

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
         pSerialPortAdapter_->LogMessage(("Attempting to set stopBits of " + device + " to " + sstopbits).c_str(), true);
         serialPortImplementation_.set_option( boost::asio::serial_port_base::stop_bits(stopBits), anError ); 
         if( !!anError)
            pSerialPortAdapter_->LogMessage(("error setting stop_bits in AsioClient(): "+boost::lexical_cast<std::string,int>(anError.value()) + " " + anError.message()).c_str(), false);
         serialPortImplementation_.set_option( boost::asio::serial_port_base::character_size( 8 ), anError ); 
         if( !!anError)
            pSerialPortAdapter_->LogMessage(("error setting character_size in AsioClient(): "+boost::lexical_cast<std::string,int>(anError.value()) + " " + anError.message()).c_str(), false);
      }while(bfalse_s);

      ReadStart(); 
   } 

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
      pSerialPortAdapter_->LogMessage(("Attempting to set flow of " + device_ + " to " + sflow).c_str(), true);
      serialPortImplementation_.set_option(  boost::asio::serial_port_base::flow_control(flow) , anError ); 
      if( !!anError)
         pSerialPortAdapter_->LogMessage(("error setting flow_control in AsioClient(): "+boost::lexical_cast<std::string,int>(anError.value()) + " " + anError.message()).c_str(), false);
   }

   void WriteOneCharacterAsynchronously(const char msg) // pass the write data to the DoWrite function via the io service in the other thread 
   { 
	   int cz = sizeof(msg);
	   if( 1!=cz)
	   {
		   return;
	   }
      io_service_.post(boost::bind(&AsioClient::DoWrite, this, msg)); 
   }

   void WriteCharactersAsynchronously(const char* pmsg, int len) // pass the write data to the DoWrite function via the io service in the other thread 
   { 

      for( int ii =0; ii < len; ++ii)
      {
         io_service_.post(boost::bind(&AsioClient::DoWrite, this, *(pmsg+ii))); 
      }
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
         pSerialPortAdapter_->LogMessage(e.what(), false);
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
         pSerialPortAdapter_->LogMessage(e.what(), false);
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
      do{
      MMThreadGuard g(readBufferLock_);
      data_read_.clear();
      }while(bfalse_s);

      // clear write buffer
      do{
      MMThreadGuard g(writeBufferLock_);
      write_msgs_.clear(); // buffered write data 
      }while(bfalse_s);
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
         pSerialPortAdapter_->LogMessage(e.what(), false);
      }
   } 

   void ReadComplete(const boost::system::error_code& error, size_t bytes_transferred) 
   { // the asynchronous read operation has now completed or failed and returned an error 
      if (!error) 
      { // read completed, so process the data 
         do  // just a scope for the guard...
         {
            MMThreadGuard g(readBufferLock_);
            for(unsigned int ib = 0; ib < bytes_transferred; ++ib)
            {
               data_read_.push_back(read_msg_[ib]);
            }
         }while(bfalse_s);
         CDeviceUtils::SleepMs(1);
         ReadStart(); // start waiting for another asynchronous read again 
      } 
      else 
      {
         // this is a normal situtation when closing the port 
         if( ! shutDownInProgress_)
            pSerialPortAdapter_->LogMessage(("error in ReadComplete: "+boost::lexical_cast<std::string,int>(error.value()) + " " + error.message()).c_str(), false);
         DoClose(error); 
      }
   } 


   // for asynchronous write operations:
   void DoWrite(const char msg) 
   { // callback to handle write call from outside this class 
      std::auto_ptr<MMThreadGuard> apwg( new MMThreadGuard(writeBufferLock_));
      bool write_in_progress = !write_msgs_.empty(); // is there anything currently being written? 
      write_msgs_.push_back(msg); // store in write buffer 

      // unlock the thread guard here
      MMThreadGuard* pg = apwg.release();
      delete pg;

      if (!write_in_progress) // if nothing is currently being written, then start 
         WriteStart(); 
   } 

   void WriteStart(void) 
   { // Start an asynchronous write and call WriteComplete when it completes or fails 
      MMThreadGuard g(writeBufferLock_);
      boost::asio::async_write(serialPortImplementation_, 
         boost::asio::buffer(&write_msgs_.front(), 1), 
         boost::bind(&AsioClient::WriteComplete, 
         this, 
         boost::asio::placeholders::error)); 
   } 

   void WriteComplete(const boost::system::error_code& error) 
   { // the asynchronous read operation has now completed or failed and returned an error 
      if (!error) 
      { // write completed, so send next write data 
         std::auto_ptr<MMThreadGuard> apwg( new MMThreadGuard(writeBufferLock_));
         if ( 0 < write_msgs_.size())
            write_msgs_.pop_front(); // remove the completed data 
         bool anythingThere = !write_msgs_.empty();

         // unlock the thread guard here
         MMThreadGuard* pg = apwg.release();
         delete pg;

         if (anythingThere) // if there is anthing left to be written 
            WriteStart(); // then start sending the next item in the buffer 
      } 
      else 
      {
         pSerialPortAdapter_->LogMessage("error in WriteComplete: ", true);
         DoClose(error); 
      }
   } 



   void DoClose(const boost::system::error_code& error) 
   { // something has gone wrong, so close the socket & make this object inactive 
      if (error == boost::asio::error::operation_aborted) // if this call is the result of a timer cancel() 
         return; // ignore it because the connection cancelled the timer 
      if (error) 
      {
         pSerialPortAdapter_->LogMessage(error.message().c_str(), false);
      }
      else 
      {
         // this is a normal condition when shutting down port 
         if( ! shutDownInProgress_)
            pSerialPortAdapter_->LogMessage("Error: Connection did not succeed", false);
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
   std::deque<char> write_msgs_; // buffered write data 
   std::deque<char> data_read_;
   SerialPort* pSerialPortAdapter_;
   std::string device_;

   MMThreadLock readBufferLock_;
   MMThreadLock writeBufferLock_;
   MMThreadLock serviceLock_;
   MMThreadLock implementationLock_;
   bool shutDownInProgress_;


}; 

#endif // ASIOCLIENT_H

