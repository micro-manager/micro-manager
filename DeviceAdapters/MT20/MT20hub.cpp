// Olympus MT20 Device Adapter
//
// Copyright 2010 
// Michael Mitchell
// mich.r.mitchell@gmail.com
//
// Last modified 26.7.10
//
//
// This file is part of the Olympus MT20 Device Adapter.
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


#include "MT20hub.h"


#ifdef WIN32
	#define close closesocket
	#define snprintf _snprintf
	//#define errno WSAGetLastError()
	#define strerror stringerror	// defined internally to use FormatMessage() instead of strerror
#else
	#include <errno.h>
#endif

MT20hub::MT20hub() :
	connected_(false),
	bytes_to_send_(0),
	PCMsgId_(1),
	recv_buf_size_(RECV_BUF_SIZE),
	msgs_to_recv_(0),
	NUM_INDENTS_PER_SPACE(2)	// for TinyXML dump_to_stdout
{
	tv_.tv_sec = 0;				// set select() timeout seconds
	tv_.tv_usec = 500000;		// set select() timeout microseconds

	log(std::string("**********************************************************************\nConstructing MT20hub object."));
}

MT20hub::~MT20hub()
{
	shutdown();
	log(std::string("Destructing MT20hub object."));
}

std::string MT20hub::initialize()
{
	log(std::string("Entering MT20hub::initialize()."));
	char ret_msg [4096];
	if(!connected_)
	{
		#ifdef WIN32
			WSAData wsaData;

			if(WSAStartup(MAKEWORD(2, 0), &wsaData) != 0)
			{
				snprintf(ret_msg, 4095, "Error: failed to establish connection to MT20: WSAStartup returns error during MT20hub::initialize()");
				log(std::string(ret_msg));
				return std::string(ret_msg);
			}
			log(std::string("WSAStartup executed successfully in MT20hub::initialize()."));
		#endif

		FD_ZERO(&readfds_);			// make sure there is initially nothing in the readfds_ fd_set
		FD_ZERO(&writefds_);		// make sure there is initially nothing in the writefds_ fd_set

		// Zero out memory in addrinfo so that sin_zero[8] contains all zeros
		memset(&host_addr_, 0, sizeof host_addr_);
		memset(&sock_addr_, 0, sizeof sock_addr_);

		host_addr_.ai_family = AF_INET;
		host_addr_.ai_socktype = SOCK_STREAM;
		host_addr_.ai_flags = AI_NUMERICHOST;

		// Call getaddrinfo to fill host_addr_ struct
		if((getaddrinfo_out_ = getaddrinfo("42.42.42.17", "4242", &host_addr_, &host_addr_ptr_)) != 0)
		{
			snprintf(ret_msg, 4095, "Error: failed to establish connection to MT20: getaddrinfo() returns error during MT20hub::initialize()");
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
		log(std::string("getaddrinfo() executed successfully in MT20hub::initialize()."));

		// Make a socket
		if((sockfd_ = socket(host_addr_ptr_->ai_family, host_addr_ptr_->ai_socktype, host_addr_ptr_->ai_protocol)) == -1)
		{
			snprintf(ret_msg, 4095, "Error: failed to establish connection to MT20: socket() returns error %i during MT20hub::initialize(): %s", errno, strerror(errno));
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
		log(std::string("socket() executed successfully in MT20hub::initialize()."));

		// Bind socket
		if(bind(sockfd_, host_addr_ptr_->ai_addr, static_cast<int>(host_addr_ptr_->ai_addrlen)) == -1)
		{
			snprintf(ret_msg, 4095, "Error: failed to establish connection to MT20: bind() returns error %i during MT20hub::initialize(): %s", errno, strerror(errno));
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
		log(std::string("bind() executed successfully in MT20hub::initialize()."));

		// Listen for connections on socket
		if(listen(sockfd_, 1) == -1)
		{
			snprintf(ret_msg, 4095, "Error: failed to establish connection to MT20: listen() returns error %i during MT20hub::initialize(): %s", errno, strerror(errno));
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
		log(std::string("listen() executed successfully in MT20hub::initialize()."));

		addr_size_ = sizeof sock_addr_;

		// Accept a connection on the socket; newfd_ is the socket descriptor connected to the client
		if( (newfd_ = accept(sockfd_, (struct sockaddr *)&sock_addr_, &addr_size_)) == -1)
		{
			snprintf(ret_msg, 4095, "Error: failed to establish connection to MT20: accept() returns error %i during MT20hub::initialize(): %s", errno, strerror(errno));
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
		log(std::string("accept() executed successfully in MT20hub::initialize()."));

		++msgs_to_recv_;							// expect one message (LAN Ack)
		std::string recv_ret;
		if( (recv_ret = do_read_write()).size() > 0)
		{
			snprintf(ret_msg, 4095, "Error: failed to establish connection to MT20: do_read_write() returns error during MT20hub::initialize() waiting for LAN Ack");
			log(recv_ret.append(std::string(ret_msg)));
			return recv_ret.append(std::string(ret_msg));
		}
		log(std::string("do_read_write() executed successfully after accept() in MT20hub::initialize()."));

		// Check for LAN Ack in recvd_msg_buf_; look for <MsgNum>701</MsgNum> in parsed XML document
		recvd_msg_iter_ = recvd_msg_buf_.begin();
		while(recvd_msg_iter_ != recvd_msg_buf_.end())
		{
			TiXmlHandle handle(&(*recvd_msg_iter_));
			TiXmlElement* element;
			char number[] = "701";
			
			if((element = handle.FirstChildElement().FirstChildElement("AckMsg").FirstChildElement("MsgNum").Element()) != NULL)
			{
				// Found appropriate element; check MsgNum
				const char* msgnum = element->GetText();
				if(strcmp(number, msgnum) == 0)
				{
					// message has been found; remove the document from revd_msg_buffer_
					recvd_msg_buf_.erase(recvd_msg_iter_);
					break;
				}
			}

			++recvd_msg_iter_;
		}
		
		if(recvd_msg_iter_ == recvd_msg_buf_.end())		// Couldn't find message in recvd_msg_buf_
		{
			snprintf(ret_msg, 4095, "Error: failed to establish connection to MT20: MT20hub::initialize() failed to find LAN Ack from the device");
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}

		push_msg(GET_SETTING_BB_0_LS_0);			// to check Access attribute; "na" => device unavailable; "stby" => device on standby; "act" => device available
		msgs_to_recv_ += 2;							// expect parsing Ack and BB.0-LS.0 settings
		if( (recv_ret = do_read_write()).size() > 0)
		{
			snprintf(ret_msg, 4095, "Error: failed to establish connection to MT20: do_read_write() returns error during MT20hub::initialize() after GET_SETTING");
			log(recv_ret.append(std::string(ret_msg)));
			return recv_ret.append(std::string(ret_msg));
		}
		log(std::string("do_read_write() executed successfully after GET_SETTING in MT20hub::initialize()."));

		// Check for access="act" in BB.0-LS.0 setting to indicate device is available
		recvd_msg_iter_ = recvd_msg_buf_.begin();
		while(recvd_msg_iter_ != recvd_msg_buf_.end())
		{
			TiXmlHandle handle(&(*recvd_msg_iter_));
			TiXmlElement* element;
			char id[] = "BB.0-LS.0";
			char act[] = "act";
			char stby[] = "stby";
			char na[] = "na";
			
			if((element = handle.FirstChildElement().FirstChildElement("Setting").Element()) != NULL)
			{
				// Found appropriate element
				const char* msg_id = element->Attribute("Id");
				if(strcmp(id, msg_id) == 0)
				{
					// message has been found; remove the document from recvd_msg_buffer_ after checking access
					const char* msg_access = element->Attribute("Access");
					if(strcmp(act, msg_access) == 0)
					{
						break;
					}
					else if(strcmp(stby, msg_access) == 0)
					{
						// MT20 is on standby; tell it to exit standby
						std::string ret = leave_standby();
						if(ret.size() > 0)
						{
							snprintf(ret_msg, 4095, "Error: Error during MT20hub::initialize(): MT20hub::exit_standby returns %i\n", ret);
							log(recv_ret.append(std::string(ret_msg)));
							return ret.append(std::string(ret_msg));
						}
						break;
					}
					else if(strcmp(na, msg_access) == 0)
					{
						snprintf(ret_msg, 4095, "Error: Error during MT20hub::initialize(): GET_SETTING_BB_0_LS_0 access == na\n");
						std::ostringstream oss;
						oss << "Received messages:" << std::endl;
						dump_to_stdout(&(*recvd_msg_iter_), &oss);
						log(std::string(ret_msg).append(oss.str()));
						return std::string(ret_msg).append(oss.str());
					}
					else			// access value is unknown
					{
						snprintf(ret_msg, 4095, "Error: Error during MT20hub::initialize(): GET_SETTING_BB_0_LS_0: unknown access value\n");
						std::ostringstream oss;
						oss << "Received messages:" << std::endl;
						dump_to_stdout(&(*recvd_msg_iter_), &oss);
						log(std::string(ret_msg).append(oss.str()));
						return std::string(ret_msg).append(oss.str());
					}

					recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);	// erase BB.0-LS.0 setting
					--recvd_msg_iter_;
					recvd_msg_buf_.erase(recvd_msg_iter_);						// preceding message should be Parsing Ack; erase it, too
					break;
				}
			}
			++recvd_msg_iter_;
		}
		
		if(recvd_msg_iter_ == recvd_msg_buf_.end())		// Couldn't find message in recvd_msg_buf_
		{
			snprintf(ret_msg, 4095, "Error: failed to receive expected response from device following GET_SETTING in MT20hub::initialize()\n");
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}

		connected_ = true;
	}
	
	log(std::string("Successfully initialized MT20hub object."));
	return std::string("");
}

std::string MT20hub::shutdown()
{
	log(std::string("Entering MT20hub::shutdown().\n"));
	char ret_msg [4096];

	if(connected_)
	{
		int sockret = 0;
		if( (sockret = close(newfd_)) == -1)
		{
			snprintf(ret_msg, 4095, "Error: close(newfd_) returns error %i during MT20hub::shutdown(): %s\n", errno, strerror(errno));
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
		if( (sockret = close(sockfd_)) == -1)
		{
			snprintf(ret_msg, 4095, "Error: close(sockfd_) returns error %i during MT20hub::shutdown(): %s\n", errno, strerror(errno));
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
		
		#ifdef WIN32
		if( (sockret = WSACleanup()) != 0)
		{
			snprintf(ret_msg, 4095,"WSACleanup() returns error %i during MT20hub::shutdown(): %s\n", WSAGetLastError(), stringerror(WSAGetLastError()));
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
		#endif
		
		connected_ = false;
	}
	log(std::string("MT20hub::shutdown() returns successfully.\n"));
	return std::string("");
}

std::string MT20hub::GetBurnerStatus(long* status)
{
	log(std::string("Entering MT20hub::GetBurnerStatus().\n"));
	char ret_msg [4096];
	push_msg(GET_STATE_BB_0_LS_0);
	msgs_to_recv_ += 2;							// expect parsing Ack and BB.0-LS.0 state
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::GetBurnerStatus() after GET_STATE\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	
	// Find first message with State Id="BB.0-LS.0" to identify correct message
	// Then check Device Id="BB.0-LS.0-Lamp.0" state to find burner status
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	
	char state_id[] = "BB.0-LS.0";
	// char device_id[] = "BB.0-LS.0-Lamp.0";

	bool success = false;

	while( (recvd_msg_iter_ != recvd_msg_buf_.end()) && !success )
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;
		log(std::string("Testing message.\n"));
		if((element = handle.FirstChildElement().FirstChildElement("State").Element()) != NULL)
		{
			// Found appropriate element
			log(std::string("Found state message in MT20hub::GetBurnerStatus().\n"));
			const char* msg_state_id = element->Attribute("Id");
			log(std::string(msg_state_id));
			if(strcmp(state_id, msg_state_id) == 0)		// this is the correct message
			{
				log(std::string("Found BB.0-LS.0 setting message in MT20hub::GetBurnerStatus().\n"));
				// message has been found; remove the document from revd_msg_buffer_ after retrieving lamp state
				element = handle.FirstChildElement().FirstChildElement("State").ChildElement("Device" , 3).Element();
				int state;
				int query_attribute_ret = element->QueryIntAttribute("State", &state);
				if(query_attribute_ret == TIXML_WRONG_TYPE || query_attribute_ret == TIXML_NO_ATTRIBUTE)
				{
					snprintf(ret_msg, 4095, "Error: 2 - TinyXML returns TIXML_WRONG_TYPE or TIXML_NO_ATTRIBUTE parsing message in MT20hub::GetBurnerStatus()\n");
					std::ostringstream oss;
					oss << "Received messages:" << std::endl;
					dump_to_stdout(&(*recvd_msg_iter_), &oss);
					log(std::string(ret_msg).append(oss.str()));
					return std::string(ret_msg).append(oss.str());
				}
				*status = (long)state;
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);	// erase BB.0-LS.0 setting
				--recvd_msg_iter_;
				recvd_msg_buf_.erase(recvd_msg_iter_);						// preceding message should be Parsing Ack; erase it, too
				success = true;
				break;
			}
		}
		++recvd_msg_iter_;
	}
	
	if( (recvd_msg_iter_ == recvd_msg_buf_.end()) && !success)		// Couldn't find message in recvd_msg_buf_
	{
		snprintf(ret_msg, 4095, "Error: 3 - failed to receive expected response from device following GET_STATE in MT20hub::GetBurnerStatus()\nContents of recvd_msg_buf_:\n");
		std::string msg = std::string(ret_msg);
		recvd_msg_iter_ = recvd_msg_buf_.begin();
		std::ostringstream oss;
		oss << "Received messages:" << std::endl;
		while(recvd_msg_iter_ != recvd_msg_buf_.end())
		{
			dump_to_stdout(&(*recvd_msg_iter_), &oss);
			oss << std::endl;
			++recvd_msg_iter_;
		}
		log(std::string(msg).append(oss.str()));
		return std::string(msg).append(oss.str());
	}

	log(std::string("MT20hub::GetBurnerStatus() returns successfully.\n"));
	return std::string("");
}

std::string MT20hub::SetBurnerStatus(long status)
{
	log(std::string("Entering MT20hub::SetBurnerStatus().\n"));
	char ret_msg [4096];
	std::string ret_str = std::string("");

	// Check if the requested state is already set; if so, return immediately
	long prestate;
	std::string prestate_ret = GetBurnerStatus(&prestate);
	if( prestate_ret.size() > 0 )
	{
		log(prestate_ret.append(std::string("MT20hub::GetBurnerStatus returns error in MT20hub::SetBurnerStatus().\n")));
		return prestate_ret.append(std::string("MT20hub::GetBurnerStatus returns error in MT20hub::SetBurnerStatus().\n"));
	}
	if(prestate == status)
	{
		log(std::string("MT20hub::SetBurnerStatus exited successfully; requested state was already set.\n"));
		return std::string("");
	}

	if(status == 0) push_msg(BURNER_OFF);
	else if(status == 1) push_msg(BURNER_ON);
	else
	{
		snprintf(ret_msg, 4095, "Error: Invalid status passed to MT20hub::SetBurner Status: %l\n", status);
		log(std::string(ret_msg));
		return std::string(ret_msg);
	}

	msgs_to_recv_++;
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: 1 - do_read_write() returns error during MT20hub::SetBurnerStatus() after BURNER_ON/OFF\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	// sleep for 5 seconds to allow burner to start
	if(status == 1)
	{
		#ifdef WIN32
		Sleep(5000);
		#else	
		sleep(5);
		#endif
	}
	
	msgs_to_recv_++;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: 2 - do_read_write() returns error during MT20hub::SetBurnerStatus() after BURNER_ON/OFF\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}

	// Find message with MsgNum 803 to indicate successful completion
	// Then delete this message and preceding one (should be MsgNum 502, Parsing Ack)
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	
	char num[] = "803";

	while(recvd_msg_iter_ != recvd_msg_buf_.end())
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;

		if((element = handle.FirstChildElement().FirstChildElement("AckMsg").FirstChildElement("MsgNum").Element()) != NULL)
		{
			// Found appropriate element
			const char* msg_num = element->GetText();
			if(strcmp(num, msg_num) == 0)		// this is the correct message
			{
				// message has been found; remove the document from revd_msg_buffer_ after retrieving lamp state
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);	// erase BB.0-LS.0 setting
				--recvd_msg_iter_;
				recvd_msg_buf_.erase(recvd_msg_iter_);						// preceding message should be Parsing Ack; erase it, too
				log(std::string("MT20hub::SetBurnerStatus() returns successfully.\n"));
				return ret_str;
			}
		}
		else if((element = handle.FirstChildElement().FirstChildElement("ErrorReport").FirstChildElement("RepNum").Element()) != NULL)
		{
			// Found error report; if it is RepNum 64 ('LS: Burner lifetime approaching specified value.')
			//	expect one extra message and throw ERR_REPLACE_BURNER_SOON
			char* expect_num = "64";
			const char* rep_num = element->GetText();
			if(strcmp(expect_num, rep_num) == 0)	// found burner lifetime error
			{
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
				--recvd_msg_iter_;
				msgs_to_recv_++;
				if( (recv_ret = do_read_write()).size() > 0)
				{
					snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetBurnerStatus() after finding burner lifetime warning\n");
					log(recv_ret.append(std::string(ret_msg)));
					return recv_ret.append(std::string(ret_msg));
				}
				ret_str = std::string("Replace burner soon!");
				log(std::string("MT20 reports burner near end of lifetime in MT20hub::SetBurnerState().\n"));
			}
		}
		++recvd_msg_iter_;
	}

	if(recvd_msg_iter_ == recvd_msg_buf_.end())		// Couldn't find message in recvd_msg_buf_
	{
		// Three possibilities: timeout -> msg still waiting to be read, actual problem, or "Burner already [on/off]"
		// Ignore the first and last, report the second
		// There should be one extra message in case of "Burner already [on/off]" or msg still waiting to be read
		++msgs_to_recv_;
		if( (recv_ret = do_read_write()).size() > 0)
		{
			snprintf(ret_msg, 4095, "Error: 3 - do_read_write() returns error during MT20hub::SetBurnerStatus() after finding end of recvd_msg_buf_\nContents of recvd_msg_buf_:\n");
			recvd_msg_iter_ = recvd_msg_buf_.begin();
			std::ostringstream oss;
			oss << "Received messages:" << std::endl;
			while(recvd_msg_iter_ != recvd_msg_buf_.end())
			{
				dump_to_stdout(&(*recvd_msg_iter_), &oss);
				oss << std::endl;
				++recvd_msg_iter_;
			}
			log(recv_ret.append(std::string(ret_msg)).append(oss.str()));
			return recv_ret.append(std::string(ret_msg)).append(oss.str());
		}
		
		// look again for MsgNum 803
		recvd_msg_iter_ = recvd_msg_buf_.begin();
	
		while(recvd_msg_iter_ != recvd_msg_buf_.end())
		{
			TiXmlHandle handle(&(*recvd_msg_iter_));
			TiXmlElement* element = NULL;

			if((element = handle.FirstChildElement().FirstChildElement("AckMsg").FirstChildElement("MsgNum").Element()) != NULL)
			{
				// Found appropriate element
				
				const char* msg_num = element->GetText();
				if(strcmp(num, msg_num) == 0)			// this is the correct message
				{
					// message has been found; check the previous message to see if it is error RepNum 30 or 31 (already on/off)
					// if so, delete original message, error report, and parsing ack preceding it
					// if it is RepNum 64, delete original message, error report, and parsing ack, and throw ERR_REPLACE_BURNER_SOON
					// if not, there was a real error; log and handle it
					--recvd_msg_iter_;
					--recvd_msg_iter_;
					// check for MsgNum 502 (parsing ack); delete it and ignore
					if((element = handle.FirstChildElement().FirstChildElement("AckMsg").FirstChildElement("MsgNum").Element()) != NULL)
					{
						char ack_num[] = "502";
						const char* msg2_num = element->GetText();
						if(strcmp(ack_num, msg2_num) == 0)	// found parsing ack; delete and ignore
						{
							recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
							recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
							log(std::string("MT20hub::SetBurnerStatus() returns successfully"));
							return ret_str;
						}
					}
					else if((element = handle.FirstChildElement().FirstChildElement("ErrorReport").FirstChildElement("RepNum").Element()) != NULL)
					// check for error 30 or 31 (already on/off); delete and ignore it
					{
						char error_num_a[] = "30";
						char error_num_b[] = "31";
						char error_num_c[] = "64";
						const char* msg_error_num = element->GetText();
						if(strcmp(error_num_a, msg_error_num) == 0 || strcmp(error_num_b, msg_error_num) == 0)
						{
							--recvd_msg_iter_;
							recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
							recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
							recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
							log(std::string("MT20hub::SetBurnerStatus() returns successfully"));
							return ret_str;
						}
						else if(strcmp(error_num_c, msg_error_num) == 0)
						{
							recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
							--recvd_msg_iter_;
							msgs_to_recv_++;
							if( (recv_ret = do_read_write()).size() > 0)
							{
								snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetBurnerStatus() after finding burner lifetime warning\n");
								log(recv_ret.append(std::string(ret_msg)));
								return recv_ret.append(std::string(ret_msg));
							}
							ret_str = std::string("Replace burner soon!");
							log(std::string("MT20 reports burner near end of lifetime in MT20hub::SetBurnerState().\n"));
						}
						else
						{
							snprintf(ret_msg, 4095, "Error: 5 - failed to receive expected response from device in MT20hub::SetBurnerStatus()\n");
							std::ostringstream oss;
							oss << "Received messages:" << std::endl;
							dump_to_stdout(&(*recvd_msg_iter_), &oss);
							log(std::string(ret_msg).append(oss.str()));
							return std::string(ret_msg).append(oss.str());
						}
					}
					else
					{
						snprintf(ret_msg, 4095, "Error: 6 - failed to receive expected response from device in MT20hub::SetBurnerStatus()\n");
						std::ostringstream oss;
						oss << "Received messages:" << std::endl;
						dump_to_stdout(&(*recvd_msg_iter_), &oss);
						log(std::string(ret_msg).append(oss.str()));
						return std::string(ret_msg).append(oss.str());
					}
				}
			}
			++recvd_msg_iter_;
		}

		if(recvd_msg_iter_ == recvd_msg_buf_.end())		// There was an error
		{
			snprintf(ret_msg, 4095, "Error: 7 - failed to receive expected response from device in MT20hub::SetBurnerStatus()\nContents of recvd_msg_buf_:\n");
			recvd_msg_iter_ = recvd_msg_buf_.begin();
			std::ostringstream oss;
			oss << "Received messages:" << std::endl;
			while(recvd_msg_iter_ != recvd_msg_buf_.end())
			{
				dump_to_stdout(&(*recvd_msg_iter_), &oss);
				oss << std::endl;
				++recvd_msg_iter_;
			}
			log(std::string(ret_msg).append(oss.str()));
			return std::string(ret_msg).append(oss.str());
		}
	}

	log(std::string("MT20hub::SetBurnerStatus() returns successfully.\n"));
	return ret_str;
}


std::string MT20hub::GetBurnerHours(long* hours)
{
	log(std::string("Entering MT20hub::GetBurnerHours().\n"));
	char ret_msg[4096];
	push_msg(GET_STATE_BB_0_LS_0);
	msgs_to_recv_ += 2;							// expect parsing Ack and BB.0-LS.0 state
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::GetBurnerHours() after GET_STATE\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	
	// Find first message with State Id="BB.0-LS.0" to identify correct message
	// Then check Device Id="BB.0-LS.0-Lamp.0" state to find burner hours
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	
	char state_id[] = "BB.0-LS.0";
	// char device_id[] = "BB.0-LS.0-Lamp.0";

	while(recvd_msg_iter_ != recvd_msg_buf_.end())
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;

		if((element = handle.FirstChildElement().FirstChildElement("State").Element()) != NULL)
		{
			// Found appropriate element
			
			const char* msg_state_id = element->Attribute("Id");
			if(strcmp(state_id, msg_state_id) == 0)		// this is the correct message
			{
				// message has been found; remove the document from revd_msg_buffer_ after retrieving burner hours
				element = handle.FirstChildElement().FirstChildElement("State").ChildElement("Device" , 3).Element();
				int time;
				int query_attribute_ret = element->QueryIntAttribute("Hours", &time);
				if(query_attribute_ret == TIXML_WRONG_TYPE || query_attribute_ret == TIXML_NO_ATTRIBUTE)
				{
					snprintf(ret_msg, 4095, "Error: TinyXML returns TIXML_WRONG_TYPE or TIXML_NO_ATTRIBUTE parsing message in MT20hub::GetBurnerHours()\n");
					std::ostringstream oss;
					oss << "Received messages:" << std::endl;
					dump_to_stdout(&(*recvd_msg_iter_), &oss);
					log(std::string(ret_msg).append(oss.str()));
					return std::string(ret_msg).append(oss.str());
				}
				*hours = (long)time;
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);	// erase BB.0-LS.0 setting
				--recvd_msg_iter_;
				recvd_msg_buf_.erase(recvd_msg_iter_);						// preceding message should be Parsing Ack; erase it, too
				break;
			}
		}
		++recvd_msg_iter_;
	}
	
	if(recvd_msg_iter_ == recvd_msg_buf_.end())		// Couldn't find message in recvd_msg_buf_
	{
		snprintf(ret_msg, 4095, "Error: failed to receive expected response from device in MT20hub::GetBurnerHours()\n");
		log(std::string(ret_msg));
		return std::string(ret_msg);
	}

	log(std::string("MT20hub::GetBurnerHours() returns successfully.\n"));
	return std::string("");
}

std::string MT20hub::GetShutterState(long* state)
{
	log(std::string("Entering MT20hub::GetShutterState().\n"));
	char ret_msg [4096];
	push_msg(GET_STATE_BB_0_LS_0);
	msgs_to_recv_ += 2;							// expect parsing Ack and BB.0-LS.0 state
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::GetShutterState() after GET_STATE\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	
	// Find first message with State Id="BB.0-LS.0" to identify correct message
	// Then check Device Id="BB.0-LS.0-Shut.0" state to find burner status
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	
	char state_id[] = "BB.0-LS.0";
	// char device_id[] = "BB.0-LS.0-Shut.0";

	while(recvd_msg_iter_ != recvd_msg_buf_.end())
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;

		if((element = handle.FirstChildElement().FirstChildElement("State").Element()) != NULL)
		{
			// Found appropriate element
			
			const char* msg_state_id = element->Attribute("Id");
			if(strcmp(state_id, msg_state_id) == 0)		// this is the correct message
			{
				// message has been found; remove the document from revd_msg_buffer_ after retrieving shutter state
				element = handle.FirstChildElement().FirstChildElement("State").ChildElement("Device" , 0).Element();
				int status;
				int query_attribute_ret = element->QueryIntAttribute("State", &status);
				if(query_attribute_ret == TIXML_WRONG_TYPE || query_attribute_ret == TIXML_NO_ATTRIBUTE)
				{
					snprintf(ret_msg, 4095, "Error: TinyXML returns TIXML_WRONG_TYPE or TIXML_NO_ATTRIBUTE parsing message in MT20hub::GetShutterState()\n");
					std::ostringstream oss;
					oss << "Received messages:" << std::endl;
					dump_to_stdout(&(*recvd_msg_iter_), &oss);
					log(std::string(ret_msg).append(oss.str()));
					return std::string(ret_msg).append(oss.str());
				}
				*state = status;
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);	// erase BB.0-LS.0 setting
				--recvd_msg_iter_;
				recvd_msg_buf_.erase(recvd_msg_iter_);						// preceding message should be Parsing Ack; erase it, too
				break;
			}
		}
		++recvd_msg_iter_;
	}
	
	if(recvd_msg_iter_ == recvd_msg_buf_.end())		// Couldn't find message in recvd_msg_buf_
	{
		snprintf(ret_msg, 4095, "Error: failed to receive expected response from device in MT20hub::GetShutterState()\n");
		log(std::string(ret_msg));
		return std::string(ret_msg);
	}

	log(std::string("MT20hub::GetShutterState() returns successfully"));
	return std::string("");
}

std::string MT20hub::SetShutterState(long state)
{
	log(std::string("Entering MT20hub::SetShutterState().\n"));
	char ret_msg [4096];
	push_msg(EXPERIMENT);
	++msgs_to_recv_;
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetShutterState() after EXPERIMENT\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	if(state == 1)
	{
		push_msg(OPEN_SHUTTER);
		++msgs_to_recv_;
		if( (recv_ret = do_read_write()).size() > 0)
		{
			snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetShutterState() after OPEN_SHUTTER\n");
			log(recv_ret.append(std::string(ret_msg)));
			return recv_ret.append(std::string(ret_msg));
		}
	}
	if(state == 0)
	{
		push_msg(CLOSE_SHUTTER);
		++msgs_to_recv_;
		if( (recv_ret = do_read_write()).size() > 0)
		{
			snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetShutterState() after CLOSE_SHUTTER\n");
			log(recv_ret.append(std::string(ret_msg)));
			return recv_ret.append(std::string(ret_msg));
		}
	}
	push_msg(GO_EXP);
	msgs_to_recv_ += 2;
	if( (recv_ret = do_read_write()).size() > 0)
	{ 
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetShutterState() after GO_EXP\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}

	// Find message containing ExperimentReport
	// Check that it is RepNum 601 (Experiment executed); otherwise, there was an error
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	char repnum[] = "601";
	char experr[] = "0";

	while(recvd_msg_iter_ != recvd_msg_buf_.end())
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;

		if((element = handle.FirstChildElement().FirstChildElement("ExperimentReport").FirstChildElement("RepNum").Element()) != NULL)
		{
			// Found appropriate message
			// Check there were no errors
			if(strcmp(element->GetText(), repnum) != 0)
			{
				snprintf(ret_msg, 4095, "Error: failed to receive expected response (MsgNum 601: Experiment executed) from device in MT20hub::SetShutterState()\nmessage received:\n");
				std::ostringstream oss;
				oss << "Received messages:" << std::endl;
				dump_to_stdout(&(*recvd_msg_iter_), &oss);
				log(std::string(ret_msg).append(oss.str()));
				return std::string(ret_msg).append(oss.str());
			}
			element = handle.FirstChildElement().FirstChildElement("ExperimentReport").FirstChildElement("ExpErrors").Element();
			if(strcmp(element->GetText(), experr) != 0)
			{
				snprintf(ret_msg, 4095, "Error: detected error in MT20hub::SetShutterState()\nmessage received:\n");
				std::ostringstream oss;
				oss << "Received messages:" << std::endl;
				dump_to_stdout(&(*recvd_msg_iter_), &oss);
				log(std::string(ret_msg).append(oss.str()));
				return std::string(ret_msg).append(oss.str());
			}
			// Remove this message and the three preceding (should be Parsing Acks)
			--recvd_msg_iter_;
			--recvd_msg_iter_;
			--recvd_msg_iter_;
			for(int i = 0; i < 4; ++i) recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
			log(std::string("MT20hub::SetShutterState() returns successfully.\n"));
			return std::string("");
		}
		++recvd_msg_iter_;
	}
	
	// If we get here, there was an error
	snprintf(ret_msg, 4095, "Error: failed to identify correct response during MT20hub::SetShutterState()\n");
	log(std::string(ret_msg));
	return std::string(ret_msg);
}

std::string MT20hub::GetFilterwheelPosition(long* pos)
{
	log(std::string("Entering MT20hub::GetFilterwheelPosition().\n"));
	char ret_msg [4096];
	push_msg(GET_STATE_BB_0_LS_0);
	msgs_to_recv_ += 2;							// expect parsing Ack and BB.0-LS.0 state
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::GetFilterwheelPosition() after GET_STATE\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	
	// Find first message with State Id="BB.0-LS.0" to identify correct message
	// Then check Device Id="BB.0-LS.0-Shut.0" state to find burner status
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	
	char state_id[] = "BB.0-LS.0";
	// char device_id[] = "BB.0-LS.0-Filtwl.0";

	while(recvd_msg_iter_ != recvd_msg_buf_.end())
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;

		if((element = handle.FirstChildElement().FirstChildElement("State").Element()) != NULL)
		{
			// Found appropriate element
			
			const char* msg_state_id = element->Attribute("Id");
			if(strcmp(state_id, msg_state_id) == 0)		// this is the correct message
			{
				// message has been found; remove the document from revd_msg_buffer_ after retrieving filterwheel position
				element = handle.FirstChildElement().FirstChildElement("State").ChildElement("Device" , 1).Element();
				int state;
				int query_attribute_ret = element->QueryIntAttribute("Pos", &state);
				if(query_attribute_ret == TIXML_WRONG_TYPE || query_attribute_ret == TIXML_NO_ATTRIBUTE)
				{
					snprintf(ret_msg, 4095, "Error: TinyXML returns TIXML_WRONG_TYPE or TIXML_NO_ATTRIBUTE parsing message in MT20hub::GetFilterwheelPosition()\n");
					std::ostringstream oss;
					oss << "Received messages:" << std::endl;
					dump_to_stdout(&(*recvd_msg_iter_), &oss);
					log(std::string(ret_msg).append(oss.str()));
					return std::string(ret_msg).append(oss.str());
				}
				*pos = state;
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);	// erase BB.0-LS.0 setting
				--recvd_msg_iter_;
				recvd_msg_buf_.erase(recvd_msg_iter_);						// preceding message should be Parsing Ack; erase it, too
				log(std::string("MT20hub::GetFilterwheelPosition() returns successfully.\n"));
				return std::string("");
			}
		}
		++recvd_msg_iter_;
	}
	
	// Couldn't find message in recvd_msg_buf_
	snprintf(ret_msg, 4095, "Error: failed to identify correct response during MT20hub::GetFilterwheelPosition()\n");
	log(std::string(ret_msg));
	return std::string(ret_msg);
}

std::string MT20hub::SetFilterwheelPosition(long pos)
{
	log(std::string("Entering MT20hub::SetFilterwheelPosition().\n"));
	char ret_msg [4096];
	push_msg(EXPERIMENT);
	++msgs_to_recv_;
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetFilterwheelPosition() after EXPERIMENT\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	switch(pos)
	{
		case 0:
			push_msg(SET_FILTWL_0);
			break;
		case 1:
			push_msg(SET_FILTWL_1);
			break;
		case 2:
			push_msg(SET_FILTWL_2);
			break;
		case 3:
			push_msg(SET_FILTWL_3);
			break;
		case 4:
			push_msg(SET_FILTWL_4);
			break;
		case 5:
			push_msg(SET_FILTWL_5);
			break;
		case 6:
			push_msg(SET_FILTWL_6);
			break;
		case 7:
			push_msg(SET_FILTWL_7);
			break;
		
		default:
			snprintf(ret_msg, 4095, "Error: invalid filterwheel position %l requested in MT20hub::SetFilterwheelPosition()\n", pos);
			log(std::string(ret_msg));
			return std::string(ret_msg);
	}
	++msgs_to_recv_;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetFilterwheelPosition() after SET_FILTWL_%l\n", pos);
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	push_msg(GO_EXP);
	msgs_to_recv_ += 2;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetFilterwheelPosition() after GO_EXP\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}

	// Find message containing ExperimentReport
	// Check that it is RepNum 601 (Experiment executed); otherwise, there was an error
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	char repnum[] = "601";
	char experr[] = "0";

	while(recvd_msg_iter_ != recvd_msg_buf_.end())
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;

		if((element = handle.FirstChildElement().FirstChildElement("ExperimentReport").FirstChildElement("RepNum").Element()) != NULL)
		{
			// Found appropriate message
			// Check there were no errors
			if(strcmp(element->GetText(), repnum) != 0)
			{
				snprintf(ret_msg, 4095, "Error: failed to receive expected response (MsgNum 601: Experiment executed) from device in MT20hub::SetFilterwheelPosition()\nmessage received:\n");
				std::ostringstream oss;
				oss << "Received messages:" << std::endl;
				dump_to_stdout(&(*recvd_msg_iter_), &oss);
				log(std::string(ret_msg).append(oss.str()));
				return std::string(ret_msg).append(oss.str());
			}
			element = handle.FirstChildElement().FirstChildElement("ExperimentReport").FirstChildElement("ExpErrors").Element();
			if(strcmp(element->GetText(), experr) != 0)
			{
				snprintf(ret_msg, 4095, "Error: detected device error in MT20hub::SetFilterwheelPosition()\nmessage received:\n");
				std::ostringstream oss;
				oss << "Received messages:" << std::endl;
				dump_to_stdout(&(*recvd_msg_iter_), &oss);
				log(std::string(ret_msg).append(oss.str()));
				return std::string(ret_msg).append(oss.str());
			}
			// Remove this message and the three preceding (should be Parsing Acks)
			--recvd_msg_iter_;
			--recvd_msg_iter_;
			--recvd_msg_iter_;
			for(int i = 0; i < 4; ++i) recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
			log(std::string("MT20hub::SetFilterwheelPosition() returns successfully.\n"));
			return std::string("");
		}
		++recvd_msg_iter_;
	}
	
	// If we get here, there was an error
	snprintf(ret_msg, 4095, "Error: failed to find expected response from device in MT20hub::SetShutterState()\n");
	log(std::string(ret_msg));
	return std::string(ret_msg);
}

std::string MT20hub::GetAttenuatorState(long* state)
{
	log(std::string("Entering MT20hub::GetAttenuatorState().\n"));
	char ret_msg [4096];
	push_msg(GET_STATE_BB_0_LS_0);
	msgs_to_recv_ += 2;							// expect parsing Ack and BB.0-LS.0 state
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::GetAttenuatorState() after EXPERIMENT\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	
	// Find first message with State Id="BB.0-LS.0" to identify correct message
	// Then check Device Id="BB.0-LS.0-Shut.0" state to find burner status
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	
	char state_id[] = "BB.0-LS.0";
	// char device_id[] = "BB.0-LS.0-Att.0";

	while(recvd_msg_iter_ != recvd_msg_buf_.end())
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;

		if((element = handle.FirstChildElement().FirstChildElement("State").Element()) != NULL)
		{
			// Found appropriate element
			
			const char* msg_state_id = element->Attribute("Id");
			if(strcmp(state_id, msg_state_id) == 0)		// this is the correct message
			{
				// message has been found; remove the document from revd_msg_buffer_ after retrieving attenuator state
				element = handle.FirstChildElement().FirstChildElement("State").ChildElement("Device" , 2).Element();
				int status;
				int query_attribute_ret = element->QueryIntAttribute("Pos", &status);
				if(query_attribute_ret == TIXML_WRONG_TYPE || query_attribute_ret == TIXML_NO_ATTRIBUTE)
				{
					snprintf(ret_msg, 4095, "Error: TinyXML returns TIXML_WRONG_TYPE or TIXML_NO_ATTRIBUTE parsing message in MT20hub::GetAttenuatorState()\n");
					std::ostringstream oss;
					oss << "Received messages:" << std::endl;
					dump_to_stdout(&(*recvd_msg_iter_), &oss);
					log(std::string(ret_msg).append(oss.str()));
					return std::string(ret_msg).append(oss.str());
				}
				*state = status;
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);	// erase BB.0-LS.0 setting
				--recvd_msg_iter_;
				recvd_msg_buf_.erase(recvd_msg_iter_);						// preceding message should be Parsing Ack; erase it, too
				log(std::string("MT20hub::GetAttenuatorState() returns successfully.\n"));
				return std::string("");
			}
		}
		++recvd_msg_iter_;
	}
	
	// Couldn't find message in recvd_msg_buf_
	snprintf(ret_msg, 4095, "Error: failed to find expected device response during MT20hub::GetAttenuatorState()\n");
	log(std::string(ret_msg));
	return std::string(ret_msg);
}


std::string MT20hub::SetAttenuatorState(long state)
{
	log(std::string("Entering MT20hub::SetAttenuatorState().\n"));
	char ret_msg [4096];
	push_msg(EXPERIMENT);
	++msgs_to_recv_;
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetAttenuatorState() after EXPERIMENT\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	switch(state)
	{
		case 0:
			push_msg(SET_ATTENUATOR_100);
			break;
		case 1:
			push_msg(SET_ATTENUATOR_89);
			break;
		case 2:
			push_msg(SET_ATTENUATOR_78);
			break;
		case 3:
			push_msg(SET_ATTENUATOR_71);
			break;
		case 4:
			push_msg(SET_ATTENUATOR_68);
			break;
		case 5:
			push_msg(SET_ATTENUATOR_57);
			break;
		case 6:
			push_msg(SET_ATTENUATOR_42);
			break;
		case 7:
			push_msg(SET_ATTENUATOR_32);
			break;
		case 8:
			push_msg(SET_ATTENUATOR_23);
			break;
		case 9:
			push_msg(SET_ATTENUATOR_11);
			break;
		case 10:
			push_msg(SET_ATTENUATOR_12);
			break;
		case 11:
			push_msg(SET_ATTENUATOR_8);
			break;
		case 12:
			push_msg(SET_ATTENUATOR_4);
			break;
		case 13:
			push_msg(SET_ATTENUATOR_2);
			break;
		default:
			snprintf(ret_msg, 4095, "Error: invalid filterwheel position %l requested in MT20hub::SetAttenuatorState()\n", state);
			log(std::string(ret_msg));
			return std::string(ret_msg);
	}
	++msgs_to_recv_;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetAttenuatorState() after SET_ATTENUATOR_%l\n", state);
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	push_msg(GO_EXP);
	msgs_to_recv_ += 2;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::SetAttenuatorState() after GO_EXP\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}

	// Find message containing ExperimentReport
	// Check that it is RepNum 601 (Experiment executed); otherwise, there was an error
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	char repnum[] = "601";
	char experr[] = "0";

	while(recvd_msg_iter_ != recvd_msg_buf_.end())
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;

		if((element = handle.FirstChildElement().FirstChildElement("ExperimentReport").FirstChildElement("RepNum").Element()) != NULL)
		{
			// Found appropriate message
			// Check there were no errors
			if(strcmp(element->GetText(), repnum) != 0)
			{
				snprintf(ret_msg, 4095, "Error: failed to receive expected response (MsgNum 601: Experiment executed) from device in MT20hub::SetAttenuatorState()\nmessage received:\n");
				std::ostringstream oss;
				oss << "Received messages:" << std::endl;
				dump_to_stdout(&(*recvd_msg_iter_), &oss);
				log(std::string(ret_msg).append(oss.str()));
				return std::string(ret_msg).append(oss.str());
			}
			element = handle.FirstChildElement().FirstChildElement("ExperimentReport").FirstChildElement("ExpErrors").Element();
			if(strcmp(element->GetText(), experr) != 0)
			{
				snprintf(ret_msg, 4095, "Error: detected device error in MT20hub::SetAttenuatorState()\nmessage received:\n");
				std::ostringstream oss;
				oss << "Received messages:" << std::endl;
				dump_to_stdout(&(*recvd_msg_iter_), &oss);
				log(std::string(ret_msg).append(oss.str()));
				return std::string(ret_msg).append(oss.str());
			}
			// Remove this message and the three preceding (should be Parsing Acks)
			--recvd_msg_iter_;
			--recvd_msg_iter_;
			--recvd_msg_iter_;
			for(int i = 0; i < 4; ++i) recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
			log(std::string("MT20hub::SetAttenuatorState returns successfully.\n"));
			return std::string("");
		}
		++recvd_msg_iter_;
	}
	
	// If we get here, there was an error
	snprintf(ret_msg, 4095, "Error: failed to find expected device response during MT20hub::SetAttenuatorState()\n");
	log(std::string(ret_msg));
	return std::string(ret_msg);
}

std::string MT20hub::leave_standby()
{
	log(std::string("Entering MT20hub::leave_standby().\n"));
	char ret_msg [4096];

	push_msg(EXIT_STANDBY);

	msgs_to_recv_++;
	std::string recv_ret;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::leave_standby() after EXIT_STANDBY\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}
	#ifdef WIN32 // sleep for 10 seconds to allow MT20 to exit standby
	Sleep(15000);
	#else	
	sleep(15);
	#endif
	msgs_to_recv_++;
	if( (recv_ret = do_read_write()).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::leave_standby() after EXIT_STANDBY\n");
		log(recv_ret.append(std::string(ret_msg)));
		return recv_ret.append(std::string(ret_msg));
	}

	// Find message with MsgNum 803 to indicate successful completion or error report with RepNum 120 to indicate device already active
	// Then delete this message and preceding one (should be MsgNum 502, Parsing Ack)
	recvd_msg_iter_ = recvd_msg_buf_.begin();
	
	char num[] = "803";

	while(recvd_msg_iter_ != recvd_msg_buf_.end())
	{
		TiXmlHandle handle(&(*recvd_msg_iter_));
		TiXmlElement* element;

		if((element = handle.FirstChildElement().FirstChildElement("AckMsg").FirstChildElement("MsgNum").Element()) != NULL)
		{
			// Found appropriate element
			const char* msg_num = element->GetText();
			if(strcmp(num, msg_num) == 0)		// this is the correct message
			{
				// message has been found; remove the document from revd_msg_buffer_
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);	// erase MsgNum 803
				--recvd_msg_iter_;
				recvd_msg_buf_.erase(recvd_msg_iter_);						// preceding message should be Parsing Ack; erase it, too
				log(std::string("MT20hub::leave_standby() returns successfully.\n"));
				return std::string("");
			}
		}
		else if((element = handle.FirstChildElement().FirstChildElement("ErrorReport").FirstChildElement("RepNum").Element()) != NULL)
		// check for error 120 (already active); delete and ignore it
		{
			char error_num[] = "120";
			const char* msg_error_num = element->GetText();
			if(strcmp(error_num, msg_error_num) == 0)
			{
				--recvd_msg_iter_;
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
				recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
				log(std::string("MT20hub::leave_standby() returns successfully.\n"));
				return std::string("");
			}
			else
			{
				snprintf(ret_msg, 4095, "Error: failed to receive expected response from device in MT20hub::leave_standby()\n");
				std::ostringstream oss;
				oss << "Received messages:" << std::endl;
				dump_to_stdout(&(*recvd_msg_iter_), &oss);
				log(std::string(ret_msg).append(oss.str()));
				return std::string(ret_msg).append(oss.str());
			}
		}
		++recvd_msg_iter_;
	}

	if(recvd_msg_iter_ == recvd_msg_buf_.end())		// Couldn't find message in recvd_msg_buf_
	{
		// Three possibilities: timeout -> msg still waiting to be read, actual problem, or "System already active"
		// Ignore the first and last, report the second
		// There should be one extra message in case of "System already active" or msg still waiting to be read
		++msgs_to_recv_;
		if( (recv_ret = do_read_write()).size() > 0)
		{ 
			snprintf(ret_msg, 4095, "Error: do_read_write() returns error during MT20hub::leave_standby() after finding end of recvd_msg_buf_\n");
			log(recv_ret.append(std::string(ret_msg)));
			return recv_ret.append(std::string(ret_msg));
		}
		
		// look again for MsgNum 803
		recvd_msg_iter_ = recvd_msg_buf_.begin();
	
		while(recvd_msg_iter_ != recvd_msg_buf_.end())
		{
			TiXmlHandle handle(&(*recvd_msg_iter_));
			TiXmlElement* element = NULL;

			if((element = handle.FirstChildElement().FirstChildElement("AckMsg").FirstChildElement("MsgNum").Element()) != NULL)
			{
				// Found appropriate element
				
				const char* msg_num = element->GetText();
				if(strcmp(num, msg_num) == 0)			// this is the correct message
				{
					// message has been found; check the previous message to see if it is error RepNum 120 (system already active)
					// if so, delete original message, error report, and parsing ack preceding it
					// if not, there was a real error; log and handle it
					--recvd_msg_iter_;
					--recvd_msg_iter_;
					// check for MsgNum 502 (parsing ack); delete it and ignore
					if((element = handle.FirstChildElement().FirstChildElement("AckMsg").FirstChildElement("MsgNum").Element()) != NULL)
					{
						char ack_num[] = "502";
						const char* msg2_num = element->GetText();
						if(strcmp(ack_num, msg2_num) == 0)	// found parsing ack; delete and ignore
						{
							recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
							recvd_msg_iter_ = recvd_msg_buf_.erase(recvd_msg_iter_);
							log(std::string("MT20hub::leave_standby() returns successfully.\n"));
							return std::string("");
						}
						else
						{
							snprintf(ret_msg, 4095, "Error: failed to receive expected response from device in MT20hub::leave_standby()\n");
							std::ostringstream oss;
							oss << "Received messages:" << std::endl;
							dump_to_stdout(&(*recvd_msg_iter_), &oss);
							log(std::string(ret_msg).append(oss.str()));
							return std::string(ret_msg).append(oss.str());
						}
					}
					else
					{
						snprintf(ret_msg, 4095, "Error: failed to receive expected response from device in MT20hub::leave_standby()\n");
						std::ostringstream oss;
						oss << "Received messages:" << std::endl;
						dump_to_stdout(&(*recvd_msg_iter_), &oss);
						log(std::string(ret_msg).append(oss.str()));
						return std::string(ret_msg).append(oss.str());
					}
				}
			}
			++recvd_msg_iter_;
		}

		if(recvd_msg_iter_ == recvd_msg_buf_.end())		// There was an error
		{
			snprintf(ret_msg, 4095, "Error: failed to receive expected response from device in MT20hub::leave_standby()\n");
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
	}

	log(std::string("MT20hub::leave_standby() returns successfully.\n"));
	return std::string("");
}

int MT20hub::push_msg(int msg_num)
{
	write_queue_.push(msg_num);
	return DEVICE_OK;
}

std::string MT20hub::make_msg(char* data, int msg_num)
{
	log(std::string("Entering MT20hub::make_msg().\n"));
	char ret_msg [4096];

	char msg_end[3071];
	switch (msg_num)
	{
		case ENUMERATE_DEVICES:
			snprintf(msg_end, 3071, "%s", enumerate_devices);
			break;

		// get_setting messages
		case GET_SETTING_SC_0:
			snprintf(msg_end, 3071, "%s", get_setting_SC_0);
			break;
		case GET_SETTING_LPT_0:
			snprintf(msg_end, 3071, "%s", get_setting_LPT_0);
			break;
		case GET_SETTING_BB_0:
			snprintf(msg_end, 3071, "%s", get_setting_BB_0);
			break;
		case GET_SETTING_BB_0_LS_0:
			snprintf(msg_end, 3071, "%s", get_setting_BB_0_LS_0);
			break;
		case GET_SETTING_SER_0_STAGE_0:
			snprintf(msg_end, 3071, "%s", get_setting_SER_0_Stage_0);
			break;
		case GET_SETTING_CAN_0:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0);
			break;
		case GET_SETTING_CAN_0_LS_0:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_LS_0);
			break;
		case GET_SETTING_CAN_0_LS_1:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_LS_1);
			break;
		case GET_SETTING_CAN_0_LS_2:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_LS_2);
			break;
		case GET_SETTING_CAN_0_FILTWL_0:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_Filtwl_0);
			break;
		case GET_SETTING_CAN_0_FILTWL_1:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_Filtwl_1);
			break;
		case GET_SETTING_CAN_0_TURRET_0:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_Turret_0);
			break;
		case GET_SETTING_CAN_0_LASER_0:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_Laser_0);
			break;
		case GET_SETTING_CAN_0_LASER_1:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_Laser_1);
			break;
		case GET_SETTING_CAN_0_LASER_2:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_Laser_2);
			break;
		case GET_SETTING_CAN_0_STAGE_0:
			snprintf(msg_end, 3071, "%s", get_setting_CAN_0_Stage_0);
			break;

		// get_state messages
		case GET_STATE_SC_0:
			snprintf(msg_end, 3071, "%s", get_state_SC_0);
			break;
		case GET_STATE_LPT_0:
			snprintf(msg_end, 3071, "%s", get_state_LPT_0);
			break;
		case GET_STATE_BB_0:
			snprintf(msg_end, 3071, "%s", get_state_BB_0);
			break;
		case GET_STATE_BB_0_LS_0:
			snprintf(msg_end, 3071, "%s", get_state_BB_0_LS_0);
			break;
		case GET_STATE_SER_0_STAGE_0:
			snprintf(msg_end, 3071, "%s", get_state_SER_0_Stage_0);
			break;
		case GET_STATE_CAN_0:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0);
			break;
		case GET_STATE_CAN_0_LS_0:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_LS_0);
			break;
		case GET_STATE_CAN_0_LS_1:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_LS_1);
			break;
		case GET_STATE_CAN_0_LS_2:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_LS_2);
			break;
		case GET_STATE_CAN_0_FILTWL_0:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_Filtwl_0);
			break;
		case GET_STATE_CAN_0_FILTWL_1:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_Filtwl_1);
			break;
		case GET_STATE_CAN_0_TURRET_0:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_Turret_0);
			break;
		case GET_STATE_CAN_0_LASER_0:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_Laser_0);
			break;
		case GET_STATE_CAN_0_LASER_1:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_Laser_1);
			break;
		case GET_STATE_CAN_0_LASER_2:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_Laser_2);
			break;
		case GET_STATE_CAN_0_STAGE_0:
			snprintf(msg_end, 3071, "%s", get_state_CAN_0_Stage_0);
			break;

		// controlling commands (not encapsulated in experiment commands)
		case ENTER_STANDBY:
			snprintf(msg_end, 3071, "%s", enter_standby);
			break;
		case EXIT_STANDBY:
			snprintf(msg_end, 3071, "%s", exit_standby);
			break;
		case BURNER_ON:
			snprintf(msg_end, 3071, "%s", burner_on);
			break;
		case BURNER_OFF:
			snprintf(msg_end, 3071, "%s", burner_off);
			break;

		// experiment commands
		case EXPERIMENT:
			snprintf(msg_end, 3071, "%s", experiment);
			break;
		case GO_EXP:
			snprintf(msg_end, 3071, "%s", go_exp);
			break;

		// shutter commands
		case OPEN_SHUTTER:
			snprintf(msg_end, 3071, "%s", open_shutter);
			break;
		case CLOSE_SHUTTER:
			snprintf(msg_end, 3071, "%s", close_shutter);
			break;

		// attenuator commands
		case SET_ATTENUATOR_100:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans100, set_att_2);
			break;
		case SET_ATTENUATOR_89:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans89, set_att_2);
			break;
		case SET_ATTENUATOR_78:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans78, set_att_2);
			break;
		case SET_ATTENUATOR_71:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans71, set_att_2);
			break;
		case SET_ATTENUATOR_68:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans68, set_att_2);
			break;
		case SET_ATTENUATOR_57:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans57, set_att_2);
			break;
		case SET_ATTENUATOR_42:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans42, set_att_2);
			break;
		case SET_ATTENUATOR_32:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans32, set_att_2);
			break;
		case SET_ATTENUATOR_23:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans23, set_att_2);
			break;
		case SET_ATTENUATOR_11:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans11, set_att_2);
			break;
		case SET_ATTENUATOR_12:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans12, set_att_2);
			break;
		case SET_ATTENUATOR_8:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans8, set_att_2);
			break;
		case SET_ATTENUATOR_4:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans4, set_att_2);
			break;
		case SET_ATTENUATOR_2:
			snprintf(msg_end, 3071, "%s%i%s", set_att_1, trans2, set_att_2);
			break;

		// filterwheel commands
		case SET_FILTWL_0:
			snprintf(msg_end, 3071, "%s%i%s", set_filtwl_1, filtwl_empty0, set_filtwl_2);
			break;
		case SET_FILTWL_1:
			snprintf(msg_end, 3071, "%s%i%s", set_filtwl_1, filtwl_empty1, set_filtwl_2);
			break;
		case SET_FILTWL_2:
			snprintf(msg_end, 3071, "%s%i%s", set_filtwl_1, filtwl_empty2, set_filtwl_2);
			break;
		case SET_FILTWL_3:
			snprintf(msg_end, 3071, "%s%i%s", set_filtwl_1, filtwl_empty3, set_filtwl_2);
			break;
		case SET_FILTWL_4:
			snprintf(msg_end, 3071, "%s%i%s", set_filtwl_1, filtwl_empty4, set_filtwl_2);
			break;
		case SET_FILTWL_5:
			snprintf(msg_end, 3071, "%s%i%s", set_filtwl_1, filtwl_empty5, set_filtwl_2);
			break;
		case SET_FILTWL_6:
			snprintf(msg_end, 3071, "%s%i%s", set_filtwl_1, filtwl_empty6, set_filtwl_2);
			break;
		case SET_FILTWL_7:
			snprintf(msg_end, 3071, "%s%i%s", set_filtwl_1, filtwl_empty7, set_filtwl_2);
			break;

		default:
			snprintf(ret_msg, 4095, "Error: invalid message identifier %i passed to MT20hub::make_msg()\n", msg_num);
			log(std::string(ret_msg));
			return std::string(ret_msg);
	}
	snprintf(data, 4095, "%s%i%s", msg_begin, PCMsgId_, msg_end);
	++PCMsgId_;
	log(std::string("MT20hub::make_msg() returns successfully.\n"));
	return std::string("");
}

std::string MT20hub::send_all(char *sendbuf, int *buflen)
{
	log(std::string("Entering MT20hub::send_all().\n"));
	char ret_msg [4096];
	int bytes_sent = 0;					// total number of bytes sent via send()
	int bytes_remaining = *buflen;		// number of bytes left in the send buffer
	int send_out;						// return value of send()

	while(bytes_sent < *buflen)
	{
		if((send_out = send(newfd_, sendbuf+bytes_sent, bytes_remaining, 0)) == -1)
		{
			snprintf(ret_msg, 4095, "Error: send() returns error %i during MT20hub::send_all(): %s\n", errno, strerror(errno));
			log(std::string(ret_msg));
			return std::string(ret_msg);
		}
		bytes_sent += send_out;
		bytes_remaining -= send_out;
	}

	*buflen = bytes_sent;
	log(std::string("MT20hub::send_all() returns successfully.\n"));
	return std::string("");
}

std::string MT20hub::flush_read_buffer()
{
	log(std::string("Entering MT20hub::flush_read_buffer().\n"));
	char ret_msg [4096];
	std::string terminator("</SCMsg>\n");

   std::size_t terminator_index = read_buffer_.find(terminator);
	if(terminator_index == std::string::npos)
	{
		log(std::string("flush_read_buffer: no messages found\n"));
		return std::string("flush_read_buffer: no messages found\n");		// buffer contains no complete messages
	}

	char* msg = new char[terminator_index + terminator.length() + 1];
	std::strcpy(msg, read_buffer_.substr(0, terminator_index + terminator.length()).c_str());
	TiXmlDocument doc;
	const char* parse_out = doc.Parse(msg, 0, TIXML_ENCODING_UTF8);

	if(parse_out == NULL)
	{
		snprintf(ret_msg, 4095, "Error: error parsing XML document during MT20hub::flush_read_buffer()\n");
		log(std::string(ret_msg));
		return std::string(ret_msg);									// error parsing XML document
	}
	read_buffer_.erase(0, terminator_index + terminator.length());
	recvd_msg_buf_.push_back(doc);

	delete msg;
	log(std::string("MT20hub::flush_read_buffer() returns successfully.\n"));
	return std::string("");
}

std::string MT20hub::handle_socket()
{
	log(std::string("Entering MT20hub::handle_socket().\n"));
	char ret_msg [4096];
	std::string ret_str = std::string("");
	// read_buffer_ may contain more completed messages; find and parse them
	std::string flush_ret;
	if(((flush_ret = flush_read_buffer()).size() > 0) && (flush_ret.find("flush_read_buffer: no messages found\n") == std::string::npos))
	{
		snprintf(ret_msg, 4095, "Error: MT20hub::flush_read_buffer returns XML parsing error in MT20hub::handle_socket()\n");
		log(flush_ret.append(std::string(ret_msg)));
		return flush_ret.append(std::string(ret_msg));				// error parsing XML document
	}

	while(flush_ret.size() == 0)
	{
		--msgs_to_recv_;		// decrement msgs_to_recv_, since we just found a message
		if(((flush_ret = flush_read_buffer()).size() > 0) && (flush_ret.find("flush_read_buffer: no messages found\n") == std::string::npos))
		{
			snprintf(ret_msg, 4095, "Error: MT20hub::flush_read_buffer returns XML parsing error in MT20hub::handle_socket()\n");
			log(flush_ret.append(std::string(ret_msg)));
			return flush_ret.append(std::string(ret_msg));				// error parsing XML docment
		}
	}

	int timeout_limiter = 3;
	while(msgs_to_recv_ > 0 && timeout_limiter > 0)
	{
		std::string handle_select_out;
		if((handle_select_out = handle_select()).size() > 0)
		{
			if(handle_select_out.find("Warning: select() timed out during MT20hub::handle_select()") != std::string::npos)
			{
				--timeout_limiter;	// select timed out
				log(std::string("select() timed out in MT20hub::handle_socket().\n"));
				ret_str = std::string("select timeout");
			}
			else
			{
				snprintf(ret_msg, 4095, "Error: MT20hub::handle_select returns error in MT20hub::handle_socket()\n");
				log(handle_select_out.append(std::string(ret_msg)));
				return handle_select_out.append(std::string(ret_msg));
			}
		}
		else
		{
			timeout_limiter = 3;						// reset timeout_limiter
			ret_str = std::string("");					// ignore previous timeout
		}

		if(((flush_ret = flush_read_buffer()).size() > 0) && (flush_ret.find("flush_read_buffer: no messages found\n") == std::string::npos))
		{
			snprintf(ret_msg, 4095, "Error: MT20hub::flush_read_buffer returns XML parsing error in MT20hub::handle_socket()\n");
			log(flush_ret.append(std::string(ret_msg)));
			return flush_ret.append(std::string(ret_msg));				// error parsing XML document
		}
		while(flush_ret.size() == 0)
		{
			--msgs_to_recv_;
			if(((flush_ret = flush_read_buffer()).size() > 0) && (flush_ret.find("flush_read_buffer: no messages found\n") == std::string::npos))
			{
				snprintf(ret_msg, 4095, "Error: MT20hub::flush_read_buffer returns XML parsing error in MT20hub::handle_socket()\n");
				log(flush_ret.append(std::string(ret_msg)));
				return flush_ret.append(std::string(ret_msg));						// error parsing XML docment
			}
		}
		if(flush_ret.size() == 0)
		{
			--msgs_to_recv_;					// found a message; decrement counter
		}
	}
	if(timeout_limiter == 0)
	{
		snprintf(ret_msg, 4095, "Error: failed to receive expected message in MT20hub::handle_socket: handle_select timed out 3 times\n");
		log(std::string(ret_msg));
		return std::string(ret_msg);
	}
	log(std::string("MT20hub::handle_socket() returns successfully.\n"));
	return ret_str;						// success
}

std::string MT20hub::do_read_write()
{
	log(std::string("Entering MT20hub::do_read_write().\n"));
	char ret_msg [4096];
	std::string recv_ret;
	int timeout_limiter = 3;
	while( (!write_queue_.empty() || msgs_to_recv_ > 0 ) && (timeout_limiter > 0) )
	{
		if( ( (recv_ret = handle_socket()).size() > 0 ) && (recv_ret.find("select timeout") == std::string::npos) )
		{
			snprintf(ret_msg, 4095, "Error: MT20hub::handle_socket returns error in MT20hub::do_read_write():\n");
			log(recv_ret.append(std::string(ret_msg)));
			return recv_ret.append(std::string(ret_msg));
		}
		else if( recv_ret.find("select timeout") != std::string::npos)
		{
			--timeout_limiter;
		}
		if(timeout_limiter < 1)
		{
			log(std::string("MT20hub::handle_socket returned timeout three times in MT20hub::do_read_write(). Failed to find an expected message.\n"));
			return std::string("MT20hub::do_read_write() exceeded timeout limit.");
		}
	}

	log(std::string("MT20hub::do_read_write() completed successfully.\n"));
	return std::string("");
}

std::string MT20hub::handle_read()
{
	log(std::string("Entering MT20hub::handle_read().\n"));
	char ret_msg [4096];
	std::string next_msg;
	char recv_buf[RECV_BUF_SIZE];
	if((bytes_recvd_ = recv(newfd_, recv_buf, recv_buf_size_, 0)) == -1)
	{
		snprintf(ret_msg, 4095, "Error: recv() returns error %i during MT20hub::handle_read(): %s\n", errno, strerror(errno));
		log(std::string(ret_msg));
		return std::string(ret_msg);			// error during recv()
	}
	read_buffer_.append(recv_buf, bytes_recvd_);

	log(std::string("MT20hub::handle_read() returns successfully.\n"));
	return std::string("");
}

std::string MT20hub::handle_write()
{
	log(std::string("Entering MT20hub::handle_write().\n"));
	char ret_msg [4096];
	char data[4096];
	std::string ret;
	if( (ret = make_msg(data, write_queue_.front())).size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: MT20hub::make_msg() returns error in MT20hub::handle_write()\n");
		log(ret.append(std::string(ret_msg)));
		return ret.append(std::string(ret_msg));
	}
	write_queue_.pop();
	int bytes_to_send = static_cast<int>(strlen(data));
   ret = send_all(data, &bytes_to_send);
   bytes_to_send_ = bytes_to_send;
	if(ret.size() > 0)
	{
		snprintf(ret_msg, 4095, "Error: MT20hub::sent_all() returns error in MT20hub::handle_write()\n");
		log(ret.append(std::string(ret_msg)));
		return ret.append(std::string(ret_msg));
	}
	else
	{
		FD_CLR(newfd_, &writefds_);
		bytes_to_send_ = 0;
	}
	log(std::string("MT20hub::handle_write() returns successfully.\n"));
	return std::string("");
}

std::string MT20hub::handle_select()
{
	log(std::string("Entering MT20hub::handle_select().\n"));
	char ret_msg [4096];

	if(!FD_ISSET(newfd_, &readfds_))
	{
		FD_SET(newfd_, &readfds_);
	}
	if(!FD_ISSET(newfd_, &writefds_) && !write_queue_.empty())
	{
		FD_SET(newfd_, &writefds_);
	}

	SOCKET select_out = select(static_cast<int>(newfd_ + 1), &readfds_, &writefds_, NULL, &tv_);
	if(select_out == -1)
	{
		snprintf(ret_msg, 4095, "Error: select() returns error %i during MT20hub::handle_select(): %s\n", errno, strerror(errno));
		log(std::string(ret_msg));
		return std::string(ret_msg);
	}
	else if(select_out == 0)
	{
		snprintf(ret_msg, 4095, "Warning: select() timed out during MT20hub::handle_select()\n");
		log(std::string(ret_msg));
		return std::string(ret_msg);
	}
	else
	{
		// receive data from MT20
		if(FD_ISSET(newfd_, &readfds_))
		{
			std::string handle_read_out = handle_read();
			if(handle_read_out.size() > 0)
			{
				snprintf(ret_msg, 4095, "Error: MT20hub::handle_read() returns error during MT20hub::handle_select()\n");
				log(handle_read_out.append(std::string(ret_msg)));
				return handle_read_out.append(std::string(ret_msg));
			}
		}
		
		// write data to MT20
		if(FD_ISSET(newfd_, &writefds_))
		{
			std::string handle_write_out = handle_write();
			if(handle_write_out.size() > 0)
			{
				snprintf(ret_msg, 4095, "Error: MT20::handle_write() returns error during MT20hub::handle_select()\n");
				log(handle_write_out.append(std::string(ret_msg)));
				return handle_write_out.append(std::string(ret_msg));
			}
		}
	}
	log(std::string("MT20hub::handle_select() returns successfully.\n"));
	return std::string("");
}

// ----------------------------------------------------------------------------------------------------
// STDOUT dump and indenting utility functions
// modified from TinyXML example
// ----------------------------------------------------------------------------------------------------

const char * MT20hub::getIndent( unsigned int numIndents )
{
	static const char * pINDENT="                                      + ";
	static const size_t LENGTH = strlen( pINDENT );
	size_t n = numIndents*NUM_INDENTS_PER_SPACE;
	if ( n > LENGTH ) n = LENGTH;

	return &pINDENT[ LENGTH-n ];
}

// same as getIndent but no "+" at the end
const char * MT20hub::getIndentAlt( unsigned int numIndents )
{
	static const char * pINDENT="                                        ";
	static const size_t LENGTH = strlen( pINDENT );
	size_t n = numIndents*NUM_INDENTS_PER_SPACE;
	if ( n > LENGTH ) n = LENGTH;

	return &pINDENT[ LENGTH-n ];
}

int MT20hub::dump_attribs_to_stdout(TiXmlElement* pElement, std::ostringstream* oss, unsigned int indent)
{
	if ( !pElement ) return 0;
	TiXmlAttribute* pAttrib=pElement->FirstAttribute();
	int i=0;
	int ival;
	double dval;
	const char* pIndent=getIndent(indent);
	*oss << std::endl;
	while (pAttrib)
	{
		*oss << pIndent << pAttrib->Name() << ": value=[" << pAttrib->Value() << "]";

		if (pAttrib->QueryIntValue(&ival)==TIXML_SUCCESS)
		{
			*oss << " int=" << ival;
		}
		if (pAttrib->QueryDoubleValue(&dval)==TIXML_SUCCESS)
		{
			*oss << " d=" << dval;
		}
		*oss << std::endl;
		i++;
		pAttrib=pAttrib->Next();
	}
	return i;	
}

void MT20hub::dump_to_stdout( TiXmlNode* pParent, std::ostringstream* oss, unsigned int indent)
{
	if ( !pParent ) return;
	TiXmlNode* pChild;
	TiXmlText* pText;
	int t = pParent->Type();
	*oss << getIndent(indent);
	int num;

	switch ( t )
	{
	case TiXmlNode::TINYXML_DOCUMENT:
		*oss << "Document";
		break;

	case TiXmlNode::TINYXML_ELEMENT:
		*oss << "Element[" << pParent->Value() << "]";
		num=dump_attribs_to_stdout(pParent->ToElement(), oss, indent+1);
		switch(num)
		{
			case 0:  
				*oss << " (No attributes)";
				break;
			case 1:
				*oss << getIndentAlt(indent) << "1 attribute";
				break;
			default:
				*oss << getIndentAlt(indent) << num << " attributes";
				break;
		}
		break;

	case TiXmlNode::TINYXML_COMMENT:
		*oss << "Comment: [" << pParent->Value() << "]";
		break;

	case TiXmlNode::TINYXML_UNKNOWN:
		*oss << "Unknown";
		break;

	case TiXmlNode::TINYXML_TEXT:
		pText = pParent->ToText();
		*oss << "Text: [" << pText->Value() << "]";
		break;

	case TiXmlNode::TINYXML_DECLARATION:
		*oss << "Declaration";
		break;
	default:
		break;
	}
	*oss << std::endl;
	for ( pChild = pParent->FirstChild(); pChild != 0; pChild = pChild->NextSibling()) 
	{
		dump_to_stdout( pChild, oss, indent+1 );
	}
}

// ----------------------------------------------------------------------------------------------------
// End of STDOUT dump and indenting utility functions
// ----------------------------------------------------------------------------------------------------


void MT20hub::log(std::string msg)
{
	#ifdef MT20_DEBUG
	FILE * debug;
	debug = fopen("mt20_debug.txt", "a");
	std::time_t now = std::time(NULL);
	std::string nowstring = std::string(std::ctime(&now));
	std::string message = nowstring.substr(0, nowstring.size() - 1).append(std::string(": ")).append(msg).append(std::string("\n"));
	fprintf(debug, message.c_str());
	fclose(debug);
	#endif // MT20_DEBUG
}

#ifdef WIN32
const char* MT20hub::stringerror(int errornum)
{
	static char buf[1024];

	FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS |
		FORMAT_MESSAGE_MAX_WIDTH_MASK, NULL, errornum, 0, buf, 1024, NULL);

	return buf;
}
#endif	// WIN32