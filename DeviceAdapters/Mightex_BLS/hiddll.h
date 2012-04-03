
 int  HidInit();
 int  HidUnInit();
 int  HidInitDevice( char *productName );
 int  HidGetDeviceName( int deviceNo, char *name, unsigned int sz );
 int  HidGetSerialNumber( int deviceNo, char *serialNum, unsigned int sz );
 int  HidOpenDevice(int deviceNo);
 void  HidCloseDevice(int deviceNo);
 int  HidReadDevice( int deviceNo, unsigned char *buf, unsigned int sz );
 int  HidWriteDevice( int deviceNo, unsigned char *buf, unsigned int sz );
 int  HidGetFeature( int deviceNo, unsigned char *buf, unsigned int sz );
 int  HidSetFeature( int deviceNo, unsigned char *buf, unsigned int sz );

