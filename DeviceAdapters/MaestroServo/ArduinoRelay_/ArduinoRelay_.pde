#include <NewSoftSerial.h>

NewSoftSerial maestro(4,3);

void setup()
{
  maestro.begin(9600);
  Serial.begin(115200);
}

void loop()
{
  if (Serial.available() > 0) 
  {
    byte cmd = Serial.read();
    switch (cmd)
    {
      case 0x93 :
      {
        maestro.print(cmd);
        int res = getMaestroByte();
        if (res > -1)
           Serial.print(res, BYTE);
        break;
      }
      case 0x84 :
      case 0x87 :
      case 0x89 :
      {
         int buf[3];
         for (int i=0; i < 3; i++) {
           buf[i] = getSerialByte();
         }
         maestro.print(cmd);
         for (int i=0; i < 3; i++) {
           if (buf[i] >= 0)
             maestro.print(buf[i]);
         }
      }
    }
  }
}  
        
        
int getMaestroByte()
{
  // wait up to 10 msec for a response
  int counter = 1;
  while (maestro.available() <= 0 && counter < 10)
  {
    counter++;
    delay(1);
  }
  if (counter >= 10)
    return -1;
  return maestro.read();
}

int getSerialByte()
{
  // wait up to 10 msec for a response
  int counter = 1;
  while (Serial.available() <= 0 && counter < 10)
  {
    counter++;
    delay(1);
  }
  if (counter >= 10)
    return -1;
  return Serial.read();
}

