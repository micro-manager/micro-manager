// This is a PWM blanking sketch, currently uses a 7 bits accuaracy at 468750
/*
 * First, a serial command can directly set the digital output patern
 * 
 * Second, a series of patterns can be stored in the arduino and a trigger on pin 2 
 *    wil trigger the consecutive pattern (trigger mode).
 *    
 * Third, intervals between consecutive patterns can be specified and paterns will be
 * generated at these specified time points (timed trigger mode).
 */

/*Interface specifications
 *  Digital pattern specification: single byte, bit 0 corresponds to pin 8,
 *    bit 1 to pin 9, etc.. Bits 7 and 8 will not be used (and should stay 0).
 */

/* Set digital output command: 1p
 *  Where p is the desired digital pattern. 
 *  
 *  Controller will return 1 to indicate succesfull execution.
 */

/* Get Digital output command: 2
 *  
 *  Controller will return 2p. Where p is the current digital output pattern
 */

/* Digital output command 3xvv
 *    Where x is the ouput channel and vv is the output in
 *    a 12-bbit significant number.
 *    
 *    Controller will return 3xvv
 */

 /* Get analogue output: 4
  */

/* Set digital pattern for triggerd mode: 5xd
 *    Where x is the number of the pattern (currrently, 12 patterns can be stored).
 *    and d is the digital pattern to be stored at that position. Note that x should be
 *    real number (i.e., not ASCI encoded)
 *    
 *    Controller will return 5xd
 */
 
/* Set the Number of digital patterns to be used: 6x
 *   Where x indicates how many digital patterns will be used (currently, up to 12
 *   patterns maximum).  In triggered mode, after reaching this many triggers, 
 *   the controller will re-start the sequence with the first pattern.
 *   
 *   Controller will return 6x
*/

/* Skip tirgger: 7x
 *    Where x indicates how many digital change events on the trigger input pin
 *    will be ignored.
 *    
 *    Controller will respond with 7x
 */

/* Start trigger mode: 8 
 *    Controller will return 8 to indicate start of trigger mode
 *    Stop triggered at 9. Trigger mode will supersede (but not stop)
 *    blanking mode (if it was active)
 */

/* Stop Trigger mode: 9
 *    Controller will return 9x where x is the number of triggers received during the last
 *    trigger mode run
 */

/* Set time interval for timed trigger mode: 10xtt
 *    Where x is the number interval (currently 12 intervals can be stored)
 *    and tt is the interval (in ms) in Arduino unsigned int format.
 *    
 *    Controller will return 11x
 */

/* Sets how often the timed pattern will be repeated 11x
 *    This value will be used in timed-trigger mode and sets how often the ouput
 *    pattern will be repeated.
 *    
 *    Controller will return 11x
 */

/* Starts timed trigger mode: 12
 *    In timed trigger mode, digital patterns as with function 5 will appear on the
 *    output pins with intervals (in ms) as set with function 10. After the number of 
 *    patterns set with function 6, the pattern will be repeated for the number of times
 *    set with function 11. Any input character (which will be processed) will stop
 *    the pattern generation
 *    
 *    Controller will return 12.
 */

/* Start blanking mode: 20
 *    In blanking mode, zeroes will be written on the output pins when the trigger pin
 *    is low, when the trigger pin is high, the pattern set with command 1 will be
 *    applied to the output pins.
 *    
 *    Controller will return 20
 */

/* Stop blanking mode: 21
 *    Stopts blanking mode
 *    
 *    Controller returns 21
 */

/* Get Identifcatio: 30
 *     Returns in ASCI MM-Ard\r\n
 */

/* Get Version: 31
 *    Returns: version number in ASCI \r\n
 */

/* Read digital state of analog input pins 0-5: 40
 *    Returns raw value of PINC
 */

/* Read analog state of input pins 0-5: 41x
 *    x=0-5. Returns analog value as a 10-bit number (0-1023)
 */ 

/*
 * Possible extensions:
 *   Set and Get Mode (low, change, rising, falling) for trigger mode
 *   Get digital patterm
 *   Get Number of digital patterns
 */
 // pin on whick to receive the trigger (2 and 3 can be used with interrupts, although this code does not use them)
 // to read out the state of inPin_ faster, ise
 // int inPinBit_ = 1<< inPin_; // bit mask

// Old Configurations
 // TLV5618 configuration
 /*
  int dataPin  = 3; // DIN
  int clockPin = 4; // SLCK
  int latchPin = 5; // CS
*/
  unsigned int version_ = 3;

  const int SEQUENCELENGTH = 12 ; //This should be good enough for everybody
  byte triggerPattern_[SEQUENCELENGTH]       = {0,0,0,0,0,0,0,0,0,0,0,0};
  unsigned int triggerDelay_[SEQUENCELENGTH] = {0,0,0,0,0,0,0,0,0,0,0,0};
  int patternLength_  = 0;
  byte repeatPattern_ = 0;
  volatile long triggerNr_; // total # of triggers in this run (0-based)
  volatile long sequenceNr_; // # of trigger in sequence (0-based)
  int skipTriggers_ = 0; // # of triggers to skip before starting to generate patterns
  byte currentPattern_ = 0; 
  const unsigned long timeOut_ = 1000;
  bool blanking_ = false;
  bool blankOnHigh_ = false;
  bool triggerMode_ = false;
  boolean triggerState_ = false;
  
// New additions for rewrite code
  byte portbAlt = 0;
  byte pindAlt = 0;

// New additions for use of internal DAC
// Default Channel power
  const unsigned int ch1Power = 4095; 
  const unsigned int ch2Power = 4095;
  const unsigned int ch3Power = 4095;
  const unsigned int ch4Power = 4095;
  const unsigned int ch5Power = 4095;
  const unsigned int ch6Power = 4095;
  const unsigned int ch7Power = 4095;
  const unsigned int ch8Power = 4095;
  unsigned int msblsb[] = {ch1Power,ch2Power,ch3Power,ch4Power,ch5Power,ch6Power,ch7Power,ch8Power};
  
  const float freq = 468750;
  const unsigned int PWMresolution = 12;

  

// Channel selection
  const int inPin_ = 8;
  const int ch1 = 23;     // Non Dac blanking -> const int ch1 = 8;
  const int ch2 = 15;     // Non Dac blanking -> const int ch2 = 9;
  const int ch3 = 3;
  const int ch4 = 4;
  const int ch5 = 10;
  const int ch6 = 9;
  const int ch7 = 6;
  const int ch8 = 5;
 
void setup() {
  // put your setup code here, to run once:
  Serial.begin(500000); //Baud rate

  pinMode(inPin_, INPUT);
  pinMode(ch1, OUTPUT);
  pinMode(ch2, OUTPUT);
  pinMode(ch3, OUTPUT);
  pinMode(ch4, OUTPUT);
  pinMode(ch5, OUTPUT);
  pinMode(ch6, OUTPUT);
  pinMode(ch7, OUTPUT);
  pinMode(ch8, OUTPUT);

  // New for PWM for more information https://www.pjrc.com/teensy/td_pulse.html
  analogWriteResolution(PWMresolution);          // Sets PWM resolution to 12 bits
  analogWriteFrequency(23, freq);  // Sets PWM frequency of pins 3 and 4
  analogWriteFrequency(15, freq);  // Sets PWM frequency of pins 5 and 6
  analogWriteFrequency(3, freq);  // Sets PWM frequency of pins 7 and 8
  analogWriteFrequency(4, freq);  // Sets PWM frequency of pins 7 and 8
  analogWriteFrequency(9, freq);  // Sets PWM frequency of pins 7 and 8
  analogWriteFrequency(10, freq);  // Sets PWM frequency of pins 7 and 8
  analogWriteFrequency(6, freq);  // Sets PWM frequency of pins 7 and 8
  analogWriteFrequency(5, freq);  // Sets PWM frequency of pins 7 and 8
  
}

void loop() {
  // put your main code here, to run repeatedly:
  if (Serial.available() > 0)
  {
    int inByte = Serial.read();
    switch (inByte)
    {
      // Set digital output
      case 1: if (waitForSerial(timeOut_))
      {
       currentPattern_ = Serial.read();
       // Do not set bits 6 and 7 for Arduino Uno
       // currentPattern_ = currentPattern_ & B0011111;     Removed for additions of extra channels

       if (!blanking_)
       {
         portbAlt = currentPattern_;
         writePattern(currentPattern_);
       }
       Serial.write(byte(1));
      }
      break;
      
      // Get digital output
      case 2:
          Serial.write( byte(2));
          //Serial.write( PORTB);
          Serial.write( portbAlt);
          break;
      // Set Analogue output (TODO: save for 'Get Analogue output')
      //Not Implemented at the moment
      
      // DAC
      case 3:
        if (waitForSerial(timeOut_))
        {
          int channel = Serial.read();
          if (waitForSerial(timeOut_))
          {
            byte msb = Serial.read();
            msb &= B00001111;
            if (waitForSerial(timeOut_)){
              byte lsb = Serial.read();
              //msblsb = (int)lsb + (int)msb * 256;     Removed for DAC blanking
              //analogueOut(channel, msb, lsb);
              if( channel == 0){
                msblsb[channel] = (int)lsb + (int)msb * 256; // Added DAC blanking
                analogWrite(ch1, msblsb[channel]);
              }
              else if (channel == 1){
                msblsb[channel] = (int)lsb + (int)msb * 256; // Added DAC blanking
                analogWrite(ch2, msblsb[channel]);
                }
              else if (channel == 2){
                msblsb[channel] = (int)lsb + (int)msb * 256; // Added DAC blanking
                analogWrite(ch3, msblsb[channel]);
                }
              else if (channel == 3){
                msblsb[channel] = (int)lsb + (int)msb * 256; // Added DAC blanking
                analogWrite(ch4, msblsb[channel]);
                }
              else if (channel == 3){
                msblsb[channel] = (int)lsb + (int)msb * 256; // Added DAC blanking
                analogWrite(ch4, msblsb[channel]);
                }
              else if (channel == 4){
                msblsb[channel] = (int)lsb + (int)msb * 256; // Added DAC blanking
                analogWrite(ch5, msblsb[channel]);
                }
              else if (channel == 5){
                msblsb[channel] = (int)lsb + (int)msb * 256; // Added DAC blanking
                analogWrite(ch6, msblsb[channel]);
                }
              else if (channel == 6){
                msblsb[channel] = (int)lsb + (int)msb * 256; // Added DAC blanking
                analogWrite(ch7, msblsb[channel]);
                }
              else if (channel == 7){
                msblsb[channel] = (int)lsb + (int)msb * 256; // Added DAC blanking
                analogWrite(ch8, msblsb[channel]);
                }
              
              Serial.write( byte(3));
              Serial.write( channel);
              Serial.write(msb);
              Serial.write(lsb);
            }
          }
        }
        break;
        // Sets the specified digital pattern for triggered mode
       case 5:
          if (waitForSerial(timeOut_)) {
            int patternNumber = Serial.read();
            if ( (patternNumber >= 0) && (patternNumber < SEQUENCELENGTH) ) {
              if (waitForSerial(timeOut_)) {
                triggerPattern_[patternNumber] = Serial.read();
                // triggerPattern_[patternNumber] = triggerPattern_[patternNumber] & B00111111; Removed for extra channels
                Serial.write( byte(5));
                Serial.write( patternNumber);
                Serial.write( triggerPattern_[patternNumber]);
                break;
              }
            }
          }
          Serial.write( "n:");//Serial.print("n:");
          break;
          
       // Sets the number of digital patterns that will be used
       case 6:
         if (waitForSerial(timeOut_)) {
           int pL = Serial.read();
           if ( (pL >= 0) && (pL <= 12) ) {
             patternLength_ = pL;
             Serial.write( byte(6));
             Serial.write( patternLength_);
           }
         }
         break;
         
       // Skip triggers
       case 7:
         if (waitForSerial(timeOut_)) {
           skipTriggers_ = Serial.read();
           Serial.write( byte(7));
           Serial.write( skipTriggers_);
         }
         break;
         
       //  starts trigger mode
       case 8: 
         if (patternLength_ > 0) {
           sequenceNr_ = 0;
           triggerNr_ = -skipTriggers_;
           triggerState_ = digitalRead(inPin_) == HIGH;
           portbAlt = writeZeros();
           Serial.write( byte(8));
           triggerMode_ = true;           
         }
         break;
         
         // return result from last triggermode
       case 9:
          triggerMode_ = false;
          portbAlt = writeZeros();
          Serial.write( byte(9));
          Serial.write( triggerNr_);
          break;
          
       // Sets time interval for timed trigger mode
       // Tricky part is that we are getting an unsigned int as two bytes
       case 10:
          if (waitForSerial(timeOut_)) {
            int patternNumber = Serial.read();
            if ( (patternNumber >= 0) && (patternNumber < SEQUENCELENGTH) ) {
              if (waitForSerial(timeOut_)) {
                unsigned int highByte = 0;
                unsigned int lowByte = 0;
                highByte = Serial.read();
                if (waitForSerial(timeOut_))
                  lowByte = Serial.read();
                highByte = highByte << 8;
                triggerDelay_[patternNumber] = highByte | lowByte;
                Serial.write( byte(10));
                Serial.write(patternNumber);
                break;
              }
            }
          }
          break;

       // Sets the number of times the patterns is repeated in timed trigger mode
       case 11:
         if (waitForSerial(timeOut_)) {
           repeatPattern_ = Serial.read();
           Serial.write( byte(11));
           Serial.write( repeatPattern_);
         }
         break;

       //  starts timed trigger mode
       case 12: 
         if (patternLength_ > 0) {
           portbAlt = writeZeros();
           Serial.write( byte(12));
           for (byte i = 0; i < repeatPattern_ && (Serial.available() == 0); i++) {
             for (int j = 0; j < patternLength_ && (Serial.available() == 0); j++) {
               portbAlt = triggerPattern_[j];
               writePattern(triggerPattern_[j]);
               delay(triggerDelay_[j]);
             }
           }
           writeZeros();
         }
         break;

       // Blanks output based on TTL input
       case 20:
         blanking_ = true;
         Serial.write( byte(20));
         break;
         
       // Stops blanking mode
       case 21:
         blanking_ = false;
         Serial.write( byte(21));
         break;
         
       // Sets 'polarity' of input TTL for blanking mode
       case 22: 
         if (waitForSerial(timeOut_)) {
           int mode = Serial.read();
           if (mode==0)
             blankOnHigh_= true;
           else
             blankOnHigh_= false;
         }
         Serial.write( byte(22));
         break;
         
       // Gives identification of the device
       case 30:
         Serial.println("MM-Ard");
         break;
         
       // Returns version string
       case 31:
         Serial.println(version_);
         break;

       //Not implemented
       case 40:
         Serial.write( byte(40));
         //Serial.write( PINC);
         Serial.write( byte(0)); 
         break;
         
       case 41:
         if (waitForSerial(timeOut_)) {
           int pin = Serial.read();  
           if (pin >= 0 && pin <=5) {
              int val = analogRead(pin);
              Serial.write( byte(41));
              Serial.write( pin);
              Serial.write( highByte(val));
              Serial.write( lowByte(val));
           }
         }
         break;
         
       case 42:
         if (waitForSerial(timeOut_)) {
           int pin = Serial.read();
           if (waitForSerial(timeOut_)) {
             int state = Serial.read();
             Serial.write( byte(42));
             Serial.write( pin);
             if (state == 0) {
                digitalWrite(14+pin, LOW);
                Serial.write( byte(0));
             }
             if (state == 1) {
                digitalWrite(14+pin, HIGH);
                Serial.write( byte(1));
             }
           }
         }
         break;

       }
    }
    
    // In trigger mode, we will blank even if blanking is not on..
    if (triggerMode_) {
      pindAlt = digitalRead(inPin_);
      boolean tmp = pindAlt;
      if (tmp != triggerState_) {
        if (blankOnHigh_ && tmp ) {
          portbAlt = writeZeros();
          
        }
        else if (!blankOnHigh_ && !tmp ) {
          portbAlt = writeZeros();
        }
        else { 
          if (triggerNr_ >=0) {
            portbAlt = triggerPattern_[sequenceNr_];
            writePattern(triggerPattern_[sequenceNr_]);
            sequenceNr_++;
            if (sequenceNr_ >= patternLength_)
              sequenceNr_ = 0;
          }
          triggerNr_++;
        }
        
        triggerState_ = tmp;       
      }  
    } else if (blanking_) {
      if (blankOnHigh_) {
        if (! digitalRead(inPin_))
        {
          portbAlt = currentPattern_;
          writePattern(currentPattern_);
        }
        else
        {
          portbAlt = writeZeros();
        }
      }  else {
        if (! digitalRead(inPin_)) {
          portbAlt = writeZeros();
        }
        else  {
          portbAlt = currentPattern_;
          writePattern(currentPattern_);
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

byte writeZeros()
{

    analogWrite(ch1, 0);  
    analogWrite(ch2, 0); 
    analogWrite(ch3, 0); 
    analogWrite(ch4, 0); 
    analogWrite(ch5, 0); 
    analogWrite(ch6, 0);
    analogWrite(ch7, 0); 
    analogWrite(ch8, 0); 
  
    /*
    digitalWrite(ch1, 0);     Disabled for DAC Blanking
    digitalWrite(ch2, 0);     Disabled for DAC Blanking
    digitalWrite(ch3, 0);
    digitalWrite(ch4, 0);
    digitalWrite(ch5, 0);
    digitalWrite(ch6, 0);
    */
    return portbAlt = 0;
 }

void writePattern(byte pattern_)
{
    if (bitRead(pattern_,0) == 0)   // Added for DAC blanking
    {
    analogWrite(ch1,0);
    }
    else{
    analogWrite(ch1 ,msblsb[0]);
    }
    
    if (bitRead(pattern_,1) == 0) // Added for DAC blanking
    {
    analogWrite(ch2,0);
    }
    else{
    analogWrite(ch2 ,msblsb[1]);
    }
  
    if (bitRead(pattern_,2) == 0) // Added for DAC blanking
    {
    analogWrite(ch3,0);
    }
    else{
    analogWrite(ch3 ,msblsb[2]);
    }
    
    if (bitRead(pattern_,3) == 0) // Added for DAC blanking
    {
    analogWrite(ch4,0);
    }
    
    else{
    analogWrite(ch4 ,msblsb[3]);
    }
    
    if (bitRead(pattern_,4) == 0) // Added for DAC blanking
    {
    analogWrite(ch5,0);
    }
    else{
    analogWrite(ch5 ,msblsb[4]);
    }
    
    if (bitRead(pattern_,5) == 0) // Added for DAC blanking
    {
    analogWrite(ch6,0);
    }
    else{
    analogWrite(ch6 ,msblsb[5]);
    }
    
    if (bitRead(pattern_,6) == 0) // Added for DAC blanking
    {
    analogWrite(ch7,0);
    }
    else{
    analogWrite(ch7 ,msblsb[6]);
    }
    
    if (bitRead(pattern_,7) == 0) // Added for DAC blanking
    {
    analogWrite(ch8,0);
    }
    else{
    analogWrite(ch8 ,msblsb[7]);
    }
    

  
/* Removed for Arduino32
  digitalWrite(ch1, bitRead(pattern_,0));   
  digitalWrite(ch2, bitRead(pattern_,1));
  digitalWrite(ch3, bitRead(pattern_,2));
  digitalWrite(ch4, bitRead(pattern_,3));
  digitalWrite(ch5, bitRead(pattern_,4));
  digitalWrite(ch6, bitRead(pattern_,5));
  */
}


// Sets analogue output in the TLV5618
// channel is either 0 ('A') or 1 ('B')
// value should be between 0 and 4095 (12 bit max)
// pins should be connected as described above
/*void analogueOut(int channel, byte msb, byte lsb) 
{
  digitalWrite(latchPin, LOW);
  msb &= B00001111;
  if (channel == 0)
     msb |= B10000000;
  // Note that in all other cases, the data will be written to DAC B and BUFFER
  shiftOut(dataPin, clockPin, MSBFIRST, msb);
  shiftOut(dataPin, clockPin, MSBFIRST, lsb);
  // The TLV5618 needs one more toggle of the clockPin:
  digitalWrite(clockPin, HIGH);
  digitalWrite(clockPin, LOW);
  digitalWrite(latchPin, HIGH);
}
*/


/* 
 // This function is called through an interrupt   
void triggerMode() 
{
  if (triggerNr_ >=0) {
    PORTB = triggerPattern_[sequenceNr_];
    sequenceNr_++;
    if (sequenceNr_ >= patternLength_)
      sequenceNr_ = 0;
  }
  triggerNr_++;
}


void blankNormal() 
{
    if (DDRD & B00000100) {
      PORTB = currentPattern_;
    } else
      PORTB = 0;
}

void blankInverted()
{
   if (DDRD & B00000100) {
     PORTB = 0;
   } else {     
     PORTB = currentPattern_;  
   }
}   

*/
