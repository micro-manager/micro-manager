// Olympus MT20 Device Adapter
//
// Copyright 2010 
// Michael Mitchell
// mich.r.mitchell@gmail.com
//
// Last modified 27.7.10
//
//
// This file is part of the Olympus MT20 Device Adapter.
//
// This device adapter requires the Real-Time Controller board that came in the original
// Cell^R/Scan^R/Cell^M/etc. computer to work. It uses TinyXML ( http://www.grinninglizard.com/tinyxml/ )
// to parse XML messages from the device.
//
// The Olympus MT20 Device Adapter is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// any later version.
//
// The Olympus MT20 Device Adapter is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the Olympus MT20 Device Adapter.  If not, see <http://www.gnu.org/licenses/>.

#ifndef _MT20HUB_H_
#define _MT20HUB_H_

#ifdef WIN32
	#define WIN32_LEAN_AND_MEAN 
	#define __USE_W32_SOCKETS
	#include <winsock2.h>
	#include <ws2tcpip.h>
	#include <windows.h>
#else
	#include <netdb.h>
#endif

#include "msg_lib.h"

#include "../../MMDevice/MMDeviceConstants.h"
#include "../../MMCore/Error.h"
#include "tinyxml.h"
#include <list>
#include <queue>
#include <ctime>
#include <errno.h>

#define RECV_BUF_SIZE 1024


/////////////////////////////////////////////////////////////////////////


class MT20hub
{
public:
	MT20hub();
	~MT20hub();

	bool connected() {return connected_;}

	// establish a connection to the device
	std::string initialize();
	std::string shutdown();

	// sets value of status to 1 if on and 0 if off
	std::string GetBurnerStatus(long* status);
	// turns burner on if status == 1; turns burner off if status == 0
	std::string SetBurnerStatus(long status);
	// return number of hours on burner
	std::string GetBurnerHours(long* hours);

	// sets value of state to 1 if open and 0 if closed
	std::string GetShutterState(long* state);
	// opens shutter if status == 1; closes shutter if status == 0
	std::string SetShutterState(long state);

	// sets value of pos to current filterwheel position
	std::string GetFilterwheelPosition(long* pos);
	// sets filterwheel position to pos
	std::string SetFilterwheelPosition(long pos);

	// sets value of state to current attenuator state
	std::string GetAttenuatorState(long* state);
	// sets attenuator position to state
	std::string SetAttenuatorState(long state);

private:

/////////////////////////////////////////////////////////////////////////
//	Private variables

	bool connected_;

	// Socket data
	struct addrinfo host_addr_, *host_addr_ptr_;	// host address
	struct sockaddr_in sock_addr_;				// used for constructing host address
	socklen_t addr_size_;		// set to sizeof sock_addr_
	int sockfd_;					// file descriptor of socket
	int newfd_;					// socket file descriptor returned by accept(sockfd_,...)
	int getaddrinfo_out_;		// return value of getaddrinfo()
	int bytes_recvd_;			// number of bytes received during last call to recv()
	int bytes_to_send_;			// should be strlen(data) on call to send_all; send_all replaces value by number of bytes sent
	struct timeval tv_;			// time value struct specifying timeout for select()
	fd_set readfds_, writefds_;	// socket file descriptor sets containing sockfds to check for read and write in select()
	
	// Communication data
	std::queue<int> write_queue_;			// queue of messages waiting to be written on the socket
	const int recv_buf_size_;			// max capacity (in bytes) of recv_buf
	int PCMsgId_;				// value to insert in PCMsg MsgId attribute
	std::string read_buffer_;	// string contains all data received not yet returned to calling function (everything after most recent terminator)
	int msgs_to_recv_;			// number of messages we currently expect to receive from MT20 controller

	std::list<TiXmlDocument> recvd_msg_buf_;			// queue of incoming messages from socket to buffer asynchronous reads
	std::list<TiXmlDocument>::iterator recvd_msg_iter_;

	// global variable for TinyXML dump_to_stdout
	const unsigned int NUM_INDENTS_PER_SPACE;

/////////////////////////////////////////////////////////////////////////
//	Private methods

	// exit standby mode
	std::string leave_standby();

	// push message msg_num (as in msg_lib.h) onto write queue
	int push_msg(int msg_num);
	
	// concatenate message elements corresponding to message msg_num into the full XML document
	//     inserts current PCMsgId_ index
	std::string make_msg(char* data, int msg_num);
	
	// call send() repeatedly until buflen bytes have been sent
	std::string send_all(char *sendbuf, int *buflen);
	
	// check for completed messages in read_buffer_
	std::string flush_read_buffer();

	// flush read_buffer_ until no more complete messages, then call handle_select() until msgs_to_recv messages have been found
	std::string handle_socket();

	// call handle_socket() until read and write buffers are empty
	std::string do_read_write();
	
	// called when select() indicates read condition; reads data and appends it to read_buffer_
	std::string handle_read();
	
	// called when select() indicates write condition; prepares message and writes it
	std::string handle_write();
	
	// checks newfd_ for read condition of read==true and write condition if write==true
	// and passes them to handle_read() and handle_write()
	std::string handle_select();

	// TinyXML dump_to_stdout functions
	const char* getIndent(unsigned int numIndents);
	const char* getIndentAlt(unsigned int numIndents);
	int dump_attribs_to_stdout(TiXmlElement* pElement, std::ostringstream* oss, unsigned int indent);
	void dump_to_stdout(TiXmlNode* pParent, std::ostringstream* oss, unsigned int indent = 0);

	// wrapper function to output debug info to "mt20_debug.txt" ifdef MT20_DEBUG
	void log(std::string msg);

	#ifdef WIN32 // converts WSAError codes to readable string
	const char* stringerror(int errornum);
	#endif

};

#endif // _MT20HUB_H_