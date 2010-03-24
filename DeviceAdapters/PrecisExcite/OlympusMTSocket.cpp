#include "OlympusMTSocket.h"

#pragma comment(lib, "Ws2_32.lib")

int OlympusMTSocket::connect()
{
   int err = initWinsock();
   if (err!=0)
      return err;


   server_ = socket( AF_INET, SOCK_STREAM, 0 );

   struct sockaddr_in sin;

   memset( &sin, 0, sizeof sin );

   sin.sin_family = AF_INET;
   sin.sin_addr.s_addr = inet_addr("42.42.42.17");
   sin.sin_port = htons( 4242 );

   if ( bind( server_, (const sockaddr *) &sin, sizeof sin ) == SOCKET_ERROR )
   {
       /* could not start server_ */
       return 1;
   }
     
   while ( listen( server_, SOMAXCONN ) == SOCKET_ERROR );

   int length;

   length = sizeof sin;
   client_ = accept( server_, (sockaddr *) &sin, &length );
   return 0;
}

int OlympusMTSocket::initWinsock()
{
   WSADATA wsaData;
   WORD version;
   int error;

   version = MAKEWORD( 2, 0 );

   error = WSAStartup( version, &wsaData );

   /* check for error */
   if ( error != 0 )
   {
       /* error occured */
       return error;
   }

   /* check for correct version */
   if ( LOBYTE( wsaData.wVersion ) != 2 ||
        HIBYTE( wsaData.wVersion ) != 0 )
   {
       /* incorrect WinSock version */
       WSACleanup();
       return 1;
   }
   return 0;
}

int OlympusMTSocket::Send(string cmd)
{
   return send(client_, cmd.c_str(), cmd.size(), 0);
}

int OlympusMTSocket::receive(string & data)
{
   data = string("");
   return 0;
}

int OlympusMTSocket::disconnect()
{
   closesocket(client_);
   closesocket(server_);
   WSACleanup();
   return 0;
}