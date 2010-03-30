#include <string>
using namespace std;
#include <winsock2.h>

class OlympusMTSocket {
public:
   int connect();
   int disconnect();
   int Send(string cmd);
   int receive(string & data);
private:
   int initWinsock();
   SOCKET client_;
   SOCKET server_;

};