#include <Servo.h> 

Servo myservo;
const unsigned long timeOut_ = 200;
const int minPos_ = 990;
const int maxPos_ = 2000;
const int servoPin_ = 9;

void setup() 
{ 
  Serial.begin(115200);
  myservo.attach(servoPin_, minPos_, maxPos_);
} 

void loop() 
{
  if (Serial.available() > 0)
  {
    byte cmd = Serial.read();

    switch (cmd)
    {
      case 0x93:
        Serial.write(0, BYTE);
        break;
      case 0x84:
      {

        if (!waitForSerial(timeOut_))
          break;
        int servoNr = Serial.read();
        
        unsigned int pos;
        if (serialReadInt(&pos)) {
           Serial.println((int)pos, DEC);
           pos = round (pos / 4);
           Serial.println((int)pos, DEC);
           if (pos >= minPos_ && pos <= maxPos_) {
              myservo.writeMicroseconds(pos);
           }              
              
        }
        break;
      }
      case 0x87: // speed, what to do with it?
      {
        if (!waitForSerial(timeOut_))
          break;
        int servoNr = Serial.read();
        unsigned int sp;
        if (serialReadInt(&sp)) {
           
        }
        break;
      }
      case 0x89: // acceleration, not sure what to do....
      {
        if (!waitForSerial(timeOut_))
          break;
        int servoNr = Serial.read();
        unsigned int ac;
        if (serialReadInt(&ac)) {
           
        }
        break;
      }
    }
  }      
}



bool waitForSerial(unsigned long timeOut)
{
    unsigned long startTime = millis();
    while (Serial.available() == 0 && (millis() - startTime < timeOut) ) {}
    if (Serial.available() > 0)
       return true;
    return false;
}

bool serialReadInt(unsigned int* value) 
{
   byte hByte, lByte;         
   if (waitForSerial(timeOut_)) {
      lByte = Serial.read();
      if (waitForSerial(timeOut_)) {
          hByte = Serial.read();
          hByte = hByte >> 1;
          *value = word (hByte, lByte);
          return true;   
       }
    }
    return false;
}

