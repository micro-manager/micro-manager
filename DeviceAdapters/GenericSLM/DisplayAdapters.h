#include <string>
#include <vector>
#include <tchar.h>
#include <Windows.h>
using namespace std;

struct MonitorDevice {
   string cardName;
   string deviceName;
   string cardType;
   string deviceType;
   int x;
   int y;
   int width;
   int height;
   bool isPrimary;
   bool isDisabled;
   bool isSLM;
};



vector<MonitorDevice> getMonitorInfo();
RECT getViewingMonitorsBounds(vector<MonitorDevice> displays);
POINT getNextDisplayPosition(vector<MonitorDevice> displays);
void updateMonitorRect(MonitorDevice * display);
void updateMonitorRects(vector<MonitorDevice> * displays);
