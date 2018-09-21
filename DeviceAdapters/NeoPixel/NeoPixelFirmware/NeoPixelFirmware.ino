
/**
   Arduino firmware to control an array of Adafruit Neopixels
   You need to install the Adafruit GFX, NeoMatrix and NeoPixel libraries
   in order to compile this code (see:
     https://learn.adafruit.com/adafruit-neopixel-uberguide/arduino-library-installation)

   You can communicate through the serial port with this code.  The Micro-Manager
   device adapter "NeoPixel" interfaces specifically with this firmware.

   The following serial commands are recognized:

   1:  Sets "active" pixels on (to the preset color)
   2:  Switches all pixels off
   3:  Sets all pixels to "active" (but do not show)
   4:  Sets all pixels to "inactive" (but do not show)
   5:  Sets activity of pixel given by row and column number
            order:  row, column, 0 or 1
   7:  Sets given color (RGB) to given intensity (0-255) for all pixels
            example: 0 255 0 sets all pixels to bright green

   30: Get Identification
     Returns (asci!) MM-NeoPixel\r\n

   31: Get Version
     Returns: version number (as ASCI string) \r\n


ASCII commands:

l: Sets "active" pixels on (to the preset color)


*/

#include <Adafruit_GFX.h>
#include <Adafruit_NeoMatrix.h>
#include <Adafruit_NeoPixel.h>

#ifdef __AVR__
#include <avr/power.h>
#endif

#define PIN 12  // pin connected to digital in of the NeoPixel array

// How many NeoPixels are attached to the Arduino?
#define NUMROWS       3
#define NUMCOLUMNS    4

#define version       1

const unsigned long timeOut_ = 1000;
const unsigned int version_ = 1;


const int numpixels_ = NUMROWS * NUMCOLUMNS;
bool pixelState_[NUMROWS][NUMCOLUMNS];
byte pixelColor_[NUMROWS][NUMCOLUMNS][3];

Adafruit_NeoPixel pixels_ = Adafruit_NeoPixel(numpixels_, PIN, NEO_GRB + NEO_KHZ800);

void setup() {
   Serial.begin(57600);  // not sure if higher speeds are feasible these days
   pixels_.begin(); // This initializes the NeoPixel library.
   pixels_.show(); // Make sure all pixels are off
   for (int row = 0; row < NUMROWS; row++) {
      for (int column = 0; column < NUMCOLUMNS; column++) {
         pixelState_[row][column] = false;
         for (byte c = 0; c < 3; c++) {
            pixelColor_[row][column][c] = 0;
         }
      }
   }
}


void loop() {

   if (Serial.available() > 0) {
      int inByte = Serial.read();
      switch (inByte) {

         case 1:   // Set "active" pixels on (to the preset color)
            on();
            Serial.write( byte(1));  // acknowledge
            break;

         case 2:  // Switches everything off
            off();
            Serial.write( byte(2));  // acknowledge
            break;

         case 3: // set all pixels to "active" (but do not show)
            for (int row = 0; row < NUMROWS; row++) {
               for (int column = 0; column < NUMCOLUMNS; column++) {
                  pixelState_[row][column] = true;
               }
            }
            Serial.write( byte(3));  // acknowledge
            break;

         case 4: // set all pixels to "inactive" (but do not show)
            for (int row = 0; row < NUMROWS; row++) {
               for (int column = 0; column < NUMCOLUMNS; column++) {
                  pixelState_[row][column] = false;
               }
            }
            Serial.write( byte(4));  // acknowledge
            break;

         case 5: // set activity of pixel given by row and column number
            // order:  row, column, 0 or 1
            if (waitForSerial(timeOut_)) {
               byte row = Serial.read();
               if (row >= 0 && row < NUMROWS && waitForSerial(timeOut_)) {
                  byte column = Serial.read();
                  if (column >= 0 && column < NUMCOLUMNS && waitForSerial(timeOut_)) {
                     byte state = Serial.read();
                     pixelState_[row][column] = (state == 0);
                  }
               }
            }
            Serial.write(byte(5));
            break;


         case 7: // set given color (RGB) to given intensity (0-255) for all pixels
            // example: 0 255 0 sets all pixels to bright green
            if (waitForSerial(timeOut_)) {
               byte red = Serial.read();
               if (waitForSerial(timeOut_)) {
                  byte green = Serial.read();
                  if (waitForSerial(timeOut_)) {
                     byte blue = Serial.read();
                     for (int row = 0; row < NUMROWS; row++) {
                        for (int column = 0; column < NUMCOLUMNS; column++) {
                           pixelColor_[row][column][0] = red;
                           pixelColor_[row][column][1] = green;
                           pixelColor_[row][column][2] = blue;
                        }
                     }
                  }
               }
            }
            Serial.write(7);
            break;

         case 8: // set given color (RGB) to given intensity (0-255) for specified pixel
            // example: 2 1 0 255 0 sets pixel row 2, column 1 to green
            if (waitForSerial(timeOut_)) {
               int row = Serial.read();
               if (waitForSerial(timeOut_)) {
                  int column = Serial.read();
                  if (waitForSerial(timeOut_)) {
                     byte red = Serial.read();
                     if (waitForSerial(timeOut_)) {
                        byte green = Serial.read();
                        if (waitForSerial(timeOut_)) {
                           byte blue = Serial.read();
                           pixelColor_[row][column][0] = red;
                           pixelColor_[row][column][1] = green;
                           pixelColor_[row][column][2] = blue;
                        }
                     }
                  }
               }
            }
            Serial.write(8);
            break;


         case 30: // Gives identification of the device
            Serial.println("MM-NeoPixel");
            break;

         case 31: // Returns version string
            Serial.println(version_);
            break;

         case 32:  // Return Nr Rows
            Serial.write(NUMROWS);
            break;

         case 33: // Return Nr Columns
            Serial.write(NUMCOLUMNS);
            break;


         // The following provides keyboard (ascii) interface to various functions
         case 108:  // ascii 'l'
            on();
            Serial.println("Pixels On");
            break;

         case 100: // ascii 'd'
            off();
            Serial.println("Pixels Off");
            break;

         // sets all pixels on or off
         // second key needs to be typed within 3 seconds
         case 97:  // ascii 'a'
            if (waitForSerial(3000)) {
               bool state = Serial.read() == 49;
               for (int row = 0; row < NUMROWS; row++) {
                  for (int column = 0; column < NUMCOLUMNS; column++) {
                     pixelState_[row][column] = state;
                  }
               }
               Serial.print("Switched all pixels ");
               if (state) Serial.println("on");
               else Serial.println("off");
            }
            break;


         // sets the desired color for all pixels:  'cr" sets all red, 'cg' green, 'cb' blue
         // second key needs to be types in 3 seconds
         case 99:  // ascii 'c'
            if (waitForSerial(3000)) { int color = Serial.read();
               byte red = 0; byte green = 0; byte blue = 0;
               if (color == 114) red = 255;  // ascii 'r'
               if (color == 103) green = 255; // ascii 'g'
               if (color == 98) blue = 255; // ascii 'b'
               for (int row = 0; row < NUMROWS; row++) {
                  for (int column = 0; column < NUMCOLUMNS; column++) {
                     pixelColor_[row][column][0] = red;
                     pixelColor_[row][column][1] = green;
                     pixelColor_[row][column][2] = blue;
                  }
               }
               Serial.println("Pixel color set");
            }
            break;



            // sets a given pixel on or off
            // p - row - columns - 0/1
            // example:  pb21  switches pixel B2 on
            // switching takes effect immediately
            case 112:  // ascii 'p'
            if (waitForSerial(3000)) {
               int row = Serial.read() - 97;
               if (row >= 0 && row < NUMROWS && waitForSerial(3000)) {
                  int column = Serial.read() - 49;  // TODO: allow more than 9 columns...
                  if (column >= 0 && column < NUMCOLUMNS && waitForSerial(3000)) {
                     bool state = Serial.read() == 49;
                     pixelState_[row][column] = state;
                     on();
                     Serial.println("Set a Pixel");
                  }
               }
            }
            break;

            
      }
   }
}

void on() {
   for (int row = 0; row < NUMROWS; row++) {
      for (int column = 0; column < NUMCOLUMNS; column++) {
         if (pixelState_[row][column]) {
            pixels_.setPixelColor(row * NUMCOLUMNS + column,
                                  pixelColor_[row][column][1],  // correct for GRB order here
                                  pixelColor_[row][column][0],
                                  pixelColor_[row][column][2]);
         } else {
            pixels_.setPixelColor(row * NUMCOLUMNS + column,  0, 0, 0);
         }
      }
   }
   pixels_.show();
}

void off() {
   for (int row = 0; row < NUMROWS; row++) {
      for (int column = 0; column < NUMCOLUMNS; column++) {
         pixels_.setPixelColor(row * NUMCOLUMNS + column,
                               0, 0, 0);
      }
   }
   pixels_.show();
}


bool waitForSerial(unsigned long timeOut)
{
   unsigned long startTime = millis();
   while (Serial.available() == 0 && (millis() - startTime < timeOut) ) {}
   if (Serial.available() > 0)
      return true;
   return false;
}
