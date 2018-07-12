/*
 * Triggered sequencing of Sutter Lambda via parallel port
 * Arduino Uno firmware
 * Author: Mark A. Tsuchida <mark@open-imaging.com>
 *
 * Copyright (C) 2018 Applied Materials, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */


// See http://gammon.com.au/interrupts for interrupt handling on Arduino

#include <avr/io.h>
#include <avr/interrupt.h>


// Add a loop delay, and print state transitions
const bool DEBUG_MODE = false;


// Serial communication
const int SERIAL_BAUDRATE = 9600;
const int SERIAL_TIMEOUT_MS = 500;
const char SERIAL_TERMINATOR = '\r';

const int MAX_CMD_LEN = 32;
char serial_buffer[MAX_CMD_LEN + 1];

// Pin assignments
const int TRIGGER_INPUT_PIN = 2;
const int BUSY_INPUT_PIN = 3;
const int ERROR_INPUT_PIN = 4;
const int BUSY_OUTPUT_PIN = 5;
const int PARALLEL_OUTPUT_PINS[8] = { 6, 7, 8, 9, 10, 11, 12, 13 };

// Sutter bit assignments
const int SUTTER_POSITION_BIT_INDEX = 0;
const int SUTTER_POSITION_NR_BITS = 4;
const int SUTTER_SPEED_BIT_INDEX = 4;
const int SUTTER_SPEED_NR_BITS = 3;
const int SUTTER_WHEEL_BIT_INDEX = 7;
const int SUTTER_WHEEL_NR_BITS = 1;
const uint8_t SUTTER_POSITION_MASK = 0x0F;
const uint8_t SUTTER_SPEED_MASK = 0x70;
const uint8_t SUTTER_WHEEL_MASK = 0x80;

// 8-bit counters, which are wrapped around upon overflow
// We keep track of detected edges vs processed edges

// Incremented by ISR on pin change; odd when pin is high; even when low
// These counters are never reset
volatile uint8_t trigger_change_count = 0;
volatile uint8_t busy_change_count = 0;
volatile uint8_t error_change_count = 0;

// Incremented by synchronous code upon processing events
uint8_t trigger_handled_count = 0;
uint8_t busy_handled_count = 0;
uint8_t error_handled_count = 0;

// Other counters
uint16_t trigger_missed_count = 0;
uint16_t error_count = 0;

// State
enum State {
  // Quiescent states
  STATE_STANDBY, // can transition to WAIT_TRIGGER, WAIT_FOR_BUSY 
  STATE_WAIT_TRIGGER, // can transition to STANDBY, WAIT_FOR_BUSY

  // Busy states
  STATE_WAIT_FOR_BUSY, // can transition to WAIT_WHILE_BUSY, ERROR, or quiescent_state
  STATE_WAIT_WHILE_BUSY, // can transition to WAIT_FOR_BUSY or ERROR
  STATE_ERROR, // can transition to WAIT_FOR_BUSY or WAIT_WHILE_BUSY
};

State current_state = STATE_STANDBY;

// Either STATE_STANDBY or STATE_WAIT_TRIGGER
State quiescent_state = STATE_STANDBY;

// Misc
const uint16_t WAIT_FOR_BUSY_TIMEOUT_US = 60;

volatile bool timer_expired = false;

const int MAX_SEQUENCE_LENGTH = 16;
uint8_t sequence[MAX_SEQUENCE_LENGTH];
uint8_t sequence_length = 0;
uint8_t sequence_index = 0;

uint8_t current_output = 0;


inline void debug_print(const char *msg) {
  if (DEBUG_MODE) {
    Serial.print("[");
    Serial.print(msg);
    Serial.print("]");
  }
}


/*
 * Interrupt service routines
 */

// For use only from ISR
inline void monitor_pin_change(int pin, volatile uint8_t *counter) {
  bool v = digitalRead(pin);
  uint8_t c = *counter;
  if (v ^ (c & 0x01)) {
    *counter = c + 1;
  }
}

ISR (PCINT2_vect) {
  monitor_pin_change(TRIGGER_INPUT_PIN, &trigger_change_count);
  monitor_pin_change(BUSY_INPUT_PIN, &busy_change_count);
  monitor_pin_change(ERROR_INPUT_PIN, &error_change_count);
}

ISR (TIMER1_OVF_vect) {
  timer_expired = true;
}


/*
 * Interrupt control
 */

void enable_pin_interrupt() {
  cli();
  // Set counter parity in case we start with some input lines high
  trigger_change_count = digitalRead(TRIGGER_INPUT_PIN) ? 1 : 0;
  busy_change_count = digitalRead(BUSY_INPUT_PIN) ? 1 : 0;
  error_change_count = digitalRead(ERROR_INPUT_PIN) ? 1 : 0;

  PCMSK2 |= bit(PCINT18) | bit(PCINT19) | bit(PCINT20);
  PCIFR |= bit(PCIF2);
  PCICR |= bit(PCIE2);
  sei();
}

// Start the 16-bit Timer/Counter1
void start_timer_us(uint16_t microseconds) {
  // A quick reference for the Timer/Counter is here:
  // https://sites.google.com/site/qeewiki/books/avr-guide/timers-on-the-atmega328

  // At 16 MHz, the prescaler of 64 gives 4 us per count
  uint16_t count = microseconds >> 2;
  uint16_t start_value = 0xFFFF - count;

  cli();
  timer_expired = false;
  TCCR1A = TCCR1B = 0;
  TCNT1 = start_value;
  TCCR1B |= bit(CS10) | bit(CS11); // prescaler = 64
  TIMSK1 |= bit(TOIE1);
  sei();
}


/*
 * Output
 */

inline void update_parallel_bits(int lsb_index, int nr_bits) {
  for (uint8_t i = 0; i < nr_bits; ++i) {
    digitalWrite(PARALLEL_OUTPUT_PINS[lsb_index + i],
      (current_output & bit(lsb_index + i)) ? HIGH : LOW);
  }
}

void initialize_output() {
  update_parallel_bits(0, 8);
}

void flip_speed_bits() {
  current_output = current_output ^ SUTTER_SPEED_MASK;
  update_parallel_bits(SUTTER_SPEED_BIT_INDEX, SUTTER_SPEED_NR_BITS);
}

void set_position(uint8_t pos) { // pos <= 0x0A
  // We flip the speed bits before and after setting the position bits.
  // This is in order to avoid a race condition in which the Lamnda sees
  // a partial change in the position bits (and therefore commences a
  // move to the wrong position).
  // This relies on the following behaviors of the Lamnda (described in
  // its manual):
  // - The parallel bits are read every 50 us
  // - If the speed bits have changed, the speed change only is handled
  //   first
  // - A position change is only handled when the speed bits have not
  //   changed since the last read
  // Therefore, modifying the speed bits immediately before the position
  // bits ensures that the Lmanda will at most perform a speed change if
  // it sees a partially changed value.
  // In the worst case, the Lambda will performe two speed changes,
  // followed by a position change. Speed changes take < 1 ms according
  // to the manual.
  
  flip_speed_bits();
  
  current_output =
    (current_output & ~SUTTER_POSITION_MASK) |
    (pos << (SUTTER_POSITION_BIT_INDEX));
  update_parallel_bits(SUTTER_POSITION_BIT_INDEX, SUTTER_POSITION_NR_BITS);

  flip_speed_bits();
}

void set_speed(uint8_t speed) { // speed <= 0x07
  current_output =
    (current_output & ~SUTTER_SPEED_MASK) |
    (speed << (SUTTER_SPEED_BIT_INDEX));
  update_parallel_bits(SUTTER_SPEED_BIT_INDEX, SUTTER_SPEED_NR_BITS);
}

void sutter_special_command(uint8_t cmd) {
  uint8_t save_output = current_output;
  current_output = cmd;
  // Update in an order that probably has a low chance of races
  update_parallel_bits(SUTTER_WHEEL_BIT_INDEX, SUTTER_WHEEL_NR_BITS);
  update_parallel_bits(SUTTER_SPEED_BIT_INDEX, SUTTER_SPEED_NR_BITS);
  update_parallel_bits(SUTTER_POSITION_BIT_INDEX, SUTTER_POSITION_NR_BITS);

  delayMicroseconds(100);

  current_output = save_output;
  update_parallel_bits(SUTTER_SPEED_BIT_INDEX, SUTTER_SPEED_NR_BITS);
  update_parallel_bits(SUTTER_POSITION_BIT_INDEX, SUTTER_POSITION_NR_BITS);
  update_parallel_bits(SUTTER_WHEEL_BIT_INDEX, SUTTER_WHEEL_NR_BITS);
}

void sutter_reset() {
  sutter_special_command(0xFB);
}

void sutter_online() {
  sutter_special_command(0xEE);
}

void sutter_local() {
  sutter_special_command(0xEF);
}

void output_busy(bool busy) {
  digitalWrite(BUSY_OUTPUT_PIN, busy ? HIGH : LOW);
}

/*
 * State transitions
 */

void switch_quiescent(State state) {
  bool is_quiescent = (current_state == quiescent_state);
  quiescent_state = state;
  if (is_quiescent) {
    debug_print(quiescent_state == STATE_WAIT_TRIGGER ? "->w" : "->s");
    current_state = quiescent_state;
  }
}

void transition_to_nonsequencing() {
  switch_quiescent(STATE_STANDBY);
}

void transition_to_sequencing() {
  switch_quiescent(STATE_WAIT_TRIGGER);
}

void transition_to_quiescent() {
  debug_print("->Q");
  output_busy(false);
  current_state = quiescent_state;
}

void transition_to_wait_for_busy() {
  debug_print("->F");
  start_timer_us(WAIT_FOR_BUSY_TIMEOUT_US);
  current_state = STATE_WAIT_FOR_BUSY;
}

void transition_to_wait_while_busy() {
  debug_print("->B");
  current_state = STATE_WAIT_WHILE_BUSY;
}

void transition_to_error() {
  debug_print("->E");
  ++error_count;
  current_state = STATE_ERROR;
}


/*
 * Serial commands
 */

void clear_serial_buffer() {
  for (int i = 0; i < MAX_CMD_LEN; ++i) {
    serial_buffer[i] = 0;
  }
}

void reply(const char *msg) {
  Serial.print(msg);
  Serial.write(SERIAL_TERMINATOR);
}

void reply_error() {
  reply("E");
}

void reply_ok() {
  reply("K");
}

void command_B() { // busy?
  reply(current_state == quiescent_state ? "0" : "1");
}

void command_E() { // stop_sequence
  transition_to_nonsequencing();
  reply_ok();
}

void command_F() { // get_speed (how Fast)
  uint8_t pos = (current_output & SUTTER_SPEED_MASK) >> SUTTER_SPEED_BIT_INDEX;
  char r[2] = { '\0', '\0' };
  r[0] = '0' + pos;
  reply(r);
}

void command_L() { // local
  output_busy(true);
  sutter_local();
  transition_to_wait_for_busy();
  reply_ok();
}

void command_M() { // move_to (set_position)
  char pos_char = serial_buffer[1];
  if (pos_char < '0' || pos_char > '9') {
    return reply_error();
  }
  output_busy(true);
  set_position(pos_char - '0');
  transition_to_wait_for_busy();
  reply_ok();
}

void command_O() { // online
  output_busy(true);
  sutter_online();
  transition_to_wait_for_busy();
  reply_ok();
}

void command_Q() { // load_sequence
  sequence_length = 0;
  for (const char *p = serial_buffer + 1; *p; ++p) {
    if (sequence_length >= MAX_SEQUENCE_LENGTH) {
      return reply_error();
    }
    
    char ch = *p;
    if (ch < '0' || ch > '9') {
      return reply_error();
    }
    sequence[sequence_length++] = ch - '0';
  }
  reply_ok();
}

void command_R() { // run_sequence
  sequence_index = 0;
  trigger_missed_count = 0;
  error_count = 0;
  trigger_handled_count = trigger_change_count;

  transition_to_sequencing();
  reply_ok();
}

void command_S() { // set_speed
  char speed_char = serial_buffer[1];
  if (speed_char < '0' || speed_char > '7') {
    return reply_error();
  }
  output_busy(true);
  set_speed(speed_char - '0');
  transition_to_wait_for_busy();
  reply_ok();
}

void command_W() { // where (get_position)
  uint8_t pos = (current_output & SUTTER_POSITION_MASK) >> SUTTER_POSITION_BIT_INDEX;
  char r[2] = { '\0', '\0' };
  r[0] = '0' + pos;
  reply(r);
}

void command_Z() { // reset
  output_busy(true);
  sutter_reset();
  transition_to_wait_for_busy();
  reply_ok();
}

void command_b() { // Debug: print Lambda BUSY line status
  Serial.print("\r\nbusy count=");
  Serial.print(busy_change_count);
  Serial.print("; read=");
  Serial.print(digitalRead(BUSY_INPUT_PIN) ? "H" : "L");
  Serial.print("\r\n");
}

void command_e() { // Debug: print Lambda ERROR line status
  Serial.print("\r\nerror count=");
  Serial.print(error_change_count);
  Serial.print("; read=");
  Serial.print(digitalRead(ERROR_INPUT_PIN) ? "H" : "L");
  Serial.print("\r\n");
}

void command_q() { // Debug: print sequence
  Serial.print("\r\nsequence=");
  for (uint8_t i = 0; i < sequence_length; ++i) {
    Serial.write('0' + sequence[i]);
  }
  Serial.print("\r\n");
}

void command_t() { // Debug: print TRIGGER input line status
  Serial.print("\r\ntrigger count=");
  Serial.print(trigger_change_count);
  Serial.print("; read=");
  Serial.print(digitalRead(TRIGGER_INPUT_PIN) ? "H" : "L");
  Serial.print("\r\n");
}

void dispatch_command() {
  char first_char = serial_buffer[0];

  if (quiescent_state == STATE_WAIT_TRIGGER) {
    switch (first_char) {
      case 'E': return command_E();
      default: return reply_error();
    }
  }

  switch (first_char) {
    case 'B': return command_B();
    case 'E': return command_E();
    case 'F': return command_F();
    case 'L': return command_L();
    case 'M': return command_M();
    case 'O': return command_O();
    case 'Q': return command_Q();
    case 'R': return command_R();
    case 'S': return command_S();
    case 'W': return command_W();
    case 'Z': return command_Z();
    // Debug commands
    case 'b': return command_b();
    case 'e': return command_e();
    case 'q': return command_q();
    case 't': return command_t();
    default: return reply_error();
  }
}

void handle_command() {
  size_t cmd_len = Serial.readBytesUntil(SERIAL_TERMINATOR, serial_buffer, MAX_CMD_LEN);
  if (cmd_len == 0)
    return;
  if (cmd_len == MAX_CMD_LEN) {
    reply_error();
    return;
  }
  serial_buffer[cmd_len] = '\0';

  dispatch_command();
}


/*
 * Main program
 */

void setup() {
  pinMode(TRIGGER_INPUT_PIN, INPUT);
  pinMode(BUSY_INPUT_PIN, INPUT);
  pinMode(ERROR_INPUT_PIN, INPUT);
  pinMode(BUSY_OUTPUT_PIN, OUTPUT);
  for (uint8_t i = 0; i < 8; ++i) {
    pinMode(PARALLEL_OUTPUT_PINS[i], OUTPUT);
  }

  initialize_output();
  enable_pin_interrupt();

  if (error_change_count & 0x01) {
    current_state = STATE_ERROR;
  }
  else if (busy_change_count & 0x01) {
    current_state = STATE_WAIT_WHILE_BUSY;
  }

  clear_serial_buffer();
  Serial.begin(SERIAL_BAUDRATE);
  Serial.setTimeout(SERIAL_TIMEOUT_MS);

  output_busy(true);
  sutter_reset();
  transition_to_wait_for_busy();

  // Provide 5V pins for testing
  for (uint8_t i = 14; i < 20; ++i) {
    pinMode(i, OUTPUT);
    digitalWrite(i, (i & 0x01) ? HIGH : LOW);
  }

  debug_print("START");
}

void loop_standby() {
  debug_print("S");
  // Nothing to do beyond processing serial commands
}

void loop_wait_trigger() {
  debug_print("W");
  uint8_t changes = trigger_change_count;
  // This works even if the change count has wrapped around, given that
  // we force the difference back to uint8_t
  uint8_t outstanding_edges = changes - trigger_handled_count;

  uint8_t outstanding_rising_edges;
  if ((changes & 0x01) && !(trigger_handled_count & 0x01)) {
    outstanding_rising_edges = (outstanding_edges - 1) / 2 + 1;
  }
  else {
    outstanding_rising_edges = outstanding_edges / 2;
  }

  if (outstanding_rising_edges > 1) {
    trigger_missed_count += outstanding_rising_edges - 1;
  }

  if (outstanding_rising_edges > 0) {
    output_busy(true);
    set_position(sequence[sequence_index++]);
    if (sequence_index >= sequence_length) {
      sequence_index = 0;
    }
    transition_to_wait_for_busy();
  }

  trigger_handled_count += outstanding_edges;
}

void loop_wait_for_busy() {
  debug_print("F");
  if (!timer_expired) {
    return;
  }

  bool is_error = error_change_count & 0x01;
  bool is_busy = busy_change_count & 0x01;
  if (is_error) {
    transition_to_error();
  }
  else if (is_busy) {
    transition_to_wait_while_busy();
  }
  else {
    transition_to_quiescent();
  }
}

void loop_wait_while_busy() {
  debug_print("B");
  bool is_error = error_change_count & 0x01;
  bool is_busy = busy_change_count & 0x01;
  if (is_error) {
    transition_to_error();
  }
  else if (!is_busy) {
    // Rather than going directly back to the quiescent state, we
    // wait a little more to make sure the Lambda doesn't go back
    // into the busy state. This can happen if multiple commands
    // were pending.
    transition_to_wait_for_busy();
  }
}

void loop_error() {
  debug_print("E");
  bool is_error = error_change_count & 0x01;
  bool is_busy = busy_change_count & 0x01;
  if (!is_error) {
    if (is_busy) {
      transition_to_wait_while_busy();
    }
    else {
      // The Lambda sets ERROR to low once recovery (homing) is finished,
      // but then starts a move to the correct position, so we will still
      // be busy. Therefore, we should not reach this code path. However,
      // allow for a short delay anyway and check again if busy.
      transition_to_wait_for_busy();
    }
  }
}

void loop() {
  debug_print("\r\n");
  if (DEBUG_MODE) {
    delay(500);
  }
  
  if (Serial.available()) {
    handle_command();
  }

  switch (current_state) {
    case STATE_STANDBY:         return loop_standby();
    case STATE_WAIT_TRIGGER:    return loop_wait_trigger();
    case STATE_WAIT_FOR_BUSY:   return loop_wait_for_busy();
    case STATE_WAIT_WHILE_BUSY: return loop_wait_while_busy();
    case STATE_ERROR:           return loop_error();
  }
}
