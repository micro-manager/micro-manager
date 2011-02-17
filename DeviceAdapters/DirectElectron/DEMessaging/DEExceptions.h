#pragma once;

#include <string>
#include "DEServer.pb.h"
#include <boost/exception/all.hpp>

typedef boost::error_info<struct tag_description, std::string> errorMessage; //(1)
typedef boost::error_info<struct tag_description, DEMessaging::DEPacket> errorPacket; //(1)

class ProtocolException : public boost::exception, public std::exception { 
	virtual const char* what() const throw()
	{
		return "Protocol Exception";
	}
};
class CommandException : public boost::exception, public std::exception { 
	virtual const char* what() const throw()
	{
		return "Command Exception";
	}
};
class CommunicationException : public boost::exception, public std::exception { 
	virtual const char* what() const throw()
	{
		return "Communication Exception";
	}
};
class PropertyException : public boost::exception, public std::exception { 
	virtual const char* what() const throw()
	{
		return "Property Exception";
	}
};
