// Assumes linear array of serial data as input
// First bytes: (3,222,7,# of rows in array, data reading colxrow)

// column length hard coded to 16 values per double line
// max rowlength set to 205

#include <elapsedMillis.h>

#define FRAMESYNC_PIN 23
#define LINESYNC_PIN 22
#define BAUD 57600

//float PATTERN_EDGE_TURN_OFF = 0.0015;
const byte GLOBAL_HEADER[] = {148,169};
const byte SHUTTER_HEADER[] = {19,119};
const byte PATTERN_HEADER[] = {27,44};
const byte COMMAND_SUCCESS[] = {176};
const int NUM_ACTIVE_LINES = 203;
const int MIN_ROW = 52;
const int MAX_ROW = MIN_ROW + NUM_ACTIVE_LINES;
const float TIME_PER_ROW_US = 126.206;
const byte LUT_SIZE = 127;
const byte NUM_ROWS = 16;
const byte NUM_COLS = 16;
float PHASE_SHIFT_US = 2.4;
byte lowerBoundIndex[LUT_SIZE];
byte weights[LUT_SIZE][2];
byte pattern[NUM_ROWS * NUM_COLS] = {0};

volatile boolean checkForInput = 0;
volatile byte linescanIndex = 0;
volatile byte patternRowLowerIndex= 0;
volatile byte patternRowLowerIndexWeight = 0;
volatile byte patternRowUpperIndexWeight = 4;
volatile boolean shutterOpen = 0;
elapsedMicros frameTime;
elapsedMicros lineTime;

void setup() {
//  makeTestPattern();
  Serial.begin(BAUD);
  calculateLUTs();
  pinMode(2,INPUT);
  pinMode(3,INPUT);
  pinMode(FRAMESYNC_PIN,INPUT);
  pinMode(LINESYNC_PIN,INPUT);
  pinMode(13,OUTPUT);
  analogWriteResolution(12);
  analogWrite(A14,0);
  attachInterrupt( digitalPinToInterrupt(FRAMESYNC_PIN), startFrame, FALLING);
  attachInterrupt( digitalPinToInterrupt(LINESYNC_PIN), startLine, FALLING);
}

void makeTestPattern() {
  //show without mirrors
  linescanIndex =  MIN_ROW+3;  
  shutterOpen = 1;      
  for (int r = 0; r < NUM_ROWS; r++) {
    for (int c = 0; c < NUM_COLS; c++) {
//       if (c % 3 == 0) {
//         pattern[r*NUM_COLS+c] = 180;
//       } else {
//          pattern[r*NUM_COLS+c] = 0; 
//       }
       pattern[r*NUM_COLS+c] = 45;
       if (c == NUM_COLS - 1 || c == 0) {
         pattern[r*NUM_COLS+c] = 0;
       }
   //     pattern[r][c] = sqrt(pow((float)r / (float) (NUM_ROWS-1),2) + pow((float)c / (float) (NUM_COLS-1),2))/sqrt(2)*160;
//        pattern[r][c] = ((float)r / (float) (NUM_ROWS-1))*160;
     //  pattern[r][c] = sqrt((float)c / (float) (NUM_COLS-1))*180;
//       float rad = sqrt( pow(c - NUM_COLS/2.0 +0.5,2 ) +  pow(r - NUM_ROWS/2.0 + 0.5,2));
//        pattern[r*NUM_COLS+c] = rad /sqrt(2) / (NUM_COLS/2)*180;
     //     pattern[r][c] = (1+sin(2*3.1415/ 3 * c))* 85 ;
    }
  }
}

void calculateLUTs() {
  float normalizedColSize = 1.0 / (float) NUM_COLS;
  for (int time_us = 0; time_us < LUT_SIZE; time_us++) {
    float spatialPos = min((1 + cos((time_us + PHASE_SHIFT_US) / TIME_PER_ROW_US * 2 * 3.141592))/2.0,0.9999999);
    //turn off pattern on edges
//    if (spatialPos < PATTERN_EDGE_TURN_OFF || spatialPos > 1 - PATTERN_EDGE_TURN_OFF ) {
//      weights[time_us][1] = 0;
//      weights[time_us][0] = 0;
//    } else {
       lowerBoundIndex[time_us] = (int) (spatialPos * (NUM_COLS - 1));
       weights[time_us][1] = (fmod(spatialPos, normalizedColSize) / normalizedColSize)*4;
       weights[time_us][0] = 4 -  weights[time_us][1];
//    }
  }
}

void loop() {
  //alternate between writing pattern and checking for new instructions
  writeNext();
  readSerial();
}

void writeNext(){
  //update row and column indices
  int elapsedTimeFromFrameStart = frameTime;
  int elapsedTimeFromLineStart = lineTime;
  //index into precomputed values for position alongg the linescan
  int timeIndex = min(elapsedTimeFromLineStart,LUT_SIZE-1);
  int firstCol = lowerBoundIndex[timeIndex];
    if (linescanIndex < MIN_ROW || linescanIndex >= MAX_ROW || !shutterOpen){
      //nothing to do
      analogWrite(A14,(int)(0));
    } else { 
        //in valid row range for modulation
        int lowerRowMod = (pattern[patternRowLowerIndex*NUM_COLS+firstCol]*weights[timeIndex][0] + pattern[patternRowLowerIndex*NUM_COLS+firstCol + 1]*weights[timeIndex][1]);
        int upperRowMod = (pattern[(patternRowLowerIndex+1)*NUM_COLS+firstCol]*weights[timeIndex][0] + pattern[(patternRowLowerIndex+1)*NUM_COLS+firstCol + 1]*weights[timeIndex][1]);
        int modulation = (lowerRowMod*patternRowLowerIndexWeight +  upperRowMod*patternRowUpperIndexWeight);
        analogWrite(A14,modulation);
      }   

}

//TTL-induced start new frame function
void startFrame(){
  frameTime = 0;
  linescanIndex = -1;
  //shutter
  analogWrite(A14,(int)(0)); 
}

void startLine() {
  lineTime = 0;
  //update info about which line we're on
  linescanIndex++;
 float fractionalRow = min(0.999999,(linescanIndex - MIN_ROW) / (float)NUM_ACTIVE_LINES) *(NUM_ROWS - 1);
  patternRowLowerIndex = (int)fractionalRow ;
  patternRowUpperIndexWeight  =  (fractionalRow - ((int) fractionalRow))*4;
  patternRowLowerIndexWeight  = 4 - patternRowUpperIndexWeight;
  }

void emptyBuffer(){
  while(Serial.available()){
    Serial.read();
  }
}

void readSerial(){
  if (!Serial.available()) {
    return;
  }
 // noInterrupts();
  //Read header
  byte header[2];
  Serial.readBytes((char*)header,2);
  if(header[0] != GLOBAL_HEADER[0] || header[1] != GLOBAL_HEADER[1]){
    emptyBuffer();
  //  interrupts();
    return;
  }
  byte instruction[3];
  Serial.readBytes((char*)instruction,3);
  if(instruction[0] == SHUTTER_HEADER[0] && instruction[1] == SHUTTER_HEADER[1]){
    shutterOpen = instruction[2];
    if (!shutterOpen) {
      analogWrite(A14,0); //immediately turn off laser
      digitalWrite(13,LOW);
    } else {
      digitalWrite(13,HIGH);
    }
    Serial.write(COMMAND_SUCCESS,1);
  } else if(instruction[0] == PATTERN_HEADER[0] && instruction[1] == PATTERN_HEADER[1]){
      Serial.readBytes((char*)pattern,NUM_ROWS*NUM_COLS);
//      emptyBuffer();
      if(instruction[2] == 1) {
        //immediately apply. in case sync signals are off tell its its in position
        linescanIndex =  MIN_ROW + 1;
      }
      Serial.write(COMMAND_SUCCESS,1);
  } else {
    emptyBuffer();
  }
 // interrupts();
}

