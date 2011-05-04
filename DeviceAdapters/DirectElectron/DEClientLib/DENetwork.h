#ifndef DENETWORK_H
#define DENETWORK_H

#include <boost/asio.hpp>
#include <boost/array.hpp>
#include <string>
#include <boost/thread.hpp>
#include <boost/optional.hpp>
#include <boost/system/error_code.hpp>

using namespace std;
using boost::asio::ip::tcp;
using boost::optional;
using boost::system::error_code;

typedef unsigned short port;
typedef unsigned char byte;

////////////////////////////////////////////////////////////////////////////////////////////////////
/// <summary>	Direct Electron networking interface.  Tries to simplify the network communication
///             between the server and the client.  The interface has only a few methods to connect
///             to a server, close a connection to server, load or save a set of information from the
///             the server.  The set of information is encapsulated as a BasicPacket from which
///             we extend into two types, the PropertyPacket and BufferPacket defined in DEProperty.h.  
///             PropertyPackets are used to encapsulate a set of pre defined values and are used
///             primarily to set and load one or two value properties from the server.  The BufferPacket
///             is used primarly to load a set of arbitrary sized data, namely an image.  All functions
///             return either true or false indicating success or failure, respectively.  </summary>
///
/// <remarks>	Sunny, 5/28/2010. </remarks>
////////////////////////////////////////////////////////////////////////////////////////////////////

class DENetwork
{
public:

	static DENetwork* getInstance();

	~DENetwork();

	////////////////////////////////////////////////////////////////////////////////////////////////////
	/// <summary>	Connects to the server</summary>
	///
	/// <remarks>	Sunny, 5/28/2010. </remarks>
	///
	/// <param name="ip">		The ip address as a string.  eg "127.0.0.1" </param>
	/// <param name="read">		The port number to read from. </param>
	/// <param name="write">	The port number to write from. </param>
	///
	/// <returns>	true if it succeeds, false if it fails. </returns>
	////////////////////////////////////////////////////////////////////////////////////////////////////

	bool connect(const char* ip, port read, port write);

	////////////////////////////////////////////////////////////////////////////////////////////////////
	/// <summary>	Closes the connection to the server</summary>
	///
	/// <remarks>	Sunny, 5/28/2010. </remarks>
	///
	/// <returns>	true if it succeeds, false if it fails. </returns>
	////////////////////////////////////////////////////////////////////////////////////////////////////

	bool close();
	bool send(void* data, long size, size_t timeout);
	bool receive(void* data, long size, size_t timeout);
	
	long run(long (*command)(), size_t timeout);	

	////////////////////////////////////////////////////////////////////////////////////////////////////
	/// <summary>	Should a function fail, use this method to retrieve the error message from the 
	///             that last operation.</summary>
	///
	/// <remarks>	Sunny, 5/28/2010. </remarks>
	///
	/// <returns>	The last error. </returns>
	////////////////////////////////////////////////////////////////////////////////////////////////////

	string getLastError();

	boost::mutex* getTransactionMutex();

private:
	DENetwork();

	bool createSocket(const char* ip, port no, tcp::socket* socket_);
	
	boost::asio::io_service io_service;
	tcp::socket* read;
	tcp::socket* write;

	void setResult(optional<error_code>* a, const error_code& b, std::size_t bytes_transferred);
	bool connected;
	boost::system::error_code error;
	bool debugMode;

	static DENetwork* instance;
	boost::mutex _transactionMutex;
};
#endif DENETWORK_H
