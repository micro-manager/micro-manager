// Arduino Due waveform generator using Analog Devices AD660
//
// Copyright 2013-4 University of California, San Francisco
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the source distribution; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
//
// This file is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// FOR A PARTICULAR PURPOSE.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//
// Author: Mark Tsuchida


//
// Introduction
//

// Most of the code below is written directly against Atmel and ARM's API, as
// opposed to the Arduino library. This is because the Arduino library, in
// wrapping and simplifying the vendor APIs, makes it impossible to allow the
// microcontroller to handle multiple tasks at the same time (in particular,
// most of the Arduino API I/O calls block, and the interrupt handling is very
// limited).
//
// This unfortunately means that this code is tied to a specific
// microcontroller (the SAM3X8E that comes on the Due), although care has been
// taken to keep it easy to adapt to related microcontrollers (especially other
// models in the SAM3X series).
//
// To read or modify this code, you will need to know (at least) about
// - C and Arduino programming,
// - The basics of microcontroller programming (registers, memory addressing,
//   I/O, etc.),
// - How interrupts work and how they are used for time-critical control,
// - How SPI works,
// - The AD660's SPI-compatible digital interface,
// - The details of the SAM3X8E as described on Atmel's SAM3X/A datasheet:
//   especially, but not limited to,
//   * the interrupt controller (Section 12.20),
//   * SPI (Chapter 33), and
//   * Timer Counter (TC; Chapter 37).
// - The ARM CMSIS API (a common interface to ARM Cortex microcontroller
//   peripherals), and
// - The Atmel SAM libraries shipped with the Arduino IDE.
//
// CMSIS documentation can be found in the
// hardware/arduino/sam/system/CMSIS/CMSIS/Documentation directory in the
// Arduino IDE distribution (inside Arduino.app/Contents/Resources/Java on
// OS X); details are best discovered from the headers in
// hardware/arduino/sam/system/CMSIS/Device/ATMEL/sam3xa/include.
//
// The Atmel SAM libraries, which provide utility macros and functions for the
// SAM peripherals, is in hardware/arduino/sam/system/libsam. The best
// documentation is the code itself (and the comments). The API of this "SAM
// Software Package" shipped with the Arduino IDE resembles but differs from
// that of the Atmel Software Framework (ASF).


//
// Design
//

// The goal here is to send sampled (i.e. precomputed and stored) waveforms to
// 2 (or more) SPI daisy-chained AD660s. The waveforms will be uploaded from
// the computer. We also want to be able to respond to commands from the
// computer to start, stop, or switch waveforms.
//
// In order to accurately generate the waveforms, we perform the SPI
// transmission to the AD660s in interrupt service routines. We use a periodic
// timer interrupt to kick off the transmission of the channel 0 sample, and we
// use an interrupt indicating the completion of SPI transmission to kick off
// the transmission of the channel 1 sample (and so on, if there are more than
// 2 channels, i.e. more than two DACs). The samples for all channels are sent
// in a single SPI transmission (chip select assertion), so that all DACs will
// load their samples at the same moment. This way, the ISRs (interrupt service
// routines) are restricted to doing minimal work, and the MCU is free to
// communicate with the computer over the serial port (and such communication
// code can be written in a blocking fashion using the Arduino library).
//
// (If we had many DAC channels, it would have made sense to use the DMA
// controller for SPI output.)
//
// To enable switching between waveforms, we introduce the concept of a
// waveform bank (see struct WaveformBank), which specifies the waveform for
// each channel (a per-channel starting offset is also included, so that the
// same waveform table can be shared among channels whose output is to differ
// only in phase). The current bank can be switched at any time, and the output
// will switch to the new bank starting at the next timer interrupt.
//
//
// Coding assumptions:
// - Timer (TC) interrupts and SPI TDRE interrupts do not coincide (this is
//   true as long as the sampling frequency is not too high).
//
//
// Limitations:
// - There is no protection against the heap and stack running into each other.
//   Currently, this can result in the waveforms becoming corrupted.
// - The Due only has 96 KB of RAM, which can quickly become limiting if more
//   than a handful of waveforms are required. If that becomes an issue, it
//   might make sense to read waveforms from an SD card via SPI. The SPI
//   interface of an SD (MMC) card can be clocked at 25 MHz, so it is probably
//   fast enough for many applications.


//
// Hardware requirements
//

// The two AD660s are assumed to be configured in serial data mode, with the
// DAC for channel 0 connected to the MCU and the channel 1 DAC daisy-chained
// from the serial output of the channel 0 DAC.
//
// The following connections between the MCU board (Arduino Due) and the DACs
// are assumed:
// - SPI header MOSI (PA26) -> SIN (DAC 0)
// - SPI header SCK (PA27) -> nCS (all DACs)
// - Digital pin 10 (PA28 (and PA29)) -> LDAC (all DACs)
// - Digital pin 2 (PB25) -> nCLR (all DACs)


//
// Serial communication protocol
//

// Commands are terminated with CR. Extra spaces are not allowed.
//
// ID - identify the device
// ID\r
// -> OK18569\r
//
// DM - set waveform length, number of banks, and number of tables
// DM512,8,4\r (error if generating waveform)
//
// ED - enable or disable all DACs
// ED0\r (disable all DACs)
// ED1\r (enable all DACs)
//
// FQ - set sampling frequency
// FQ1000\r (1 kHz)
//
// LW - load waveform into table
// LW0\r
// -> EX1024\r
// <- 1024 bytes (512 little-endian samples)
// -> OK\r
//
// AW - assign waveform
// AW2,0,1\r (use waveform in table 2 for bank 0, channel 1)
//
// PH - set phase offset
// PH0,127\r (channel 0 starts at sample 127)
//
// SB - switch bank
// SB2\r (switch to bank 2)
//
// RN - start generating waveform
// RN\r
//
// HL - halt waveform generation
// HL\r
//
// MN - return sampling frequency minimum
//
// MV - set absolute DAC value on channel
// MV0,32767\r (error if generating waveform)
//
// MX - return sampling frequency maximum
//
// WH - query DAC value on channel
// WH0\r (error if generating waveform)
//
//
// Replies are also termianted with CR
// Host should not send next command before receiving complete reply
//
// OK\r
// OK128\r (response to a query)
// EX512\r (expecting 512 bytes of binary data)
// ER12\r (error code 12)


//
// Serial communication
//

#include "SerialProtocol.h"

const int SERIAL_BAUDRATE = 115200;
const int SERIAL_TEXT_TIMEOUT = 60000;
const int SERIAL_BINARY_TIMEOUT = 5000;

// Command and response terminator
const char SERIAL_TERMINATOR = '\r';

// Length of command buffer
const size_t MAX_CMD_LEN = 32;

const size_t DEBUG_BUFFER_LEN = 64;
static char g_debug_buffer[DEBUG_BUFFER_LEN] = "";


//
// Hardware configuration
//

// Which TC to use.
// Note that what Atmel calls TC1, channel 0 is referred to as TC3 in CMSIS
#define ID_TC_SAMPLER ID_TC3
Tc *const TC_SAMPLER = TC1;
#define TC_SAMPLER_CHANNEL 0
const IRQn_Type TC_SAMPLER_IRQn = TC3_IRQn;
#define TC_Sampler_Handler TC3_Handler

// TC clock frequency
#define TC_SAMPLER_TIMER_CLOCK TC_CMR_TCCLKS_TIMER_CLOCK3
#define TC_SAMPLER_MCK_DIVIDER 32  // for TIMER_CLOCK3
#define TC_SAMPLER_TICK_FREQ (VARIANT_MCK / TC_SAMPLER_MCK_DIVIDER)
#define MIN_SAMPLE_FREQ (1 + TC_SAMPLER_TICK_FREQ / UINT16_MAX)
#define MAX_SAMPLE_FREQ TC_SAMPLER_TICK_FREQ

// Which SPI peripheral to use
#define ID_SPI_DAC ID_SPI0
Spi *const SPI_DAC = SPI0;
const IRQn_Type SPI_DAC_IRQn = SPI0_IRQn;
#define SPI_DAC_Handler SPI0_Handler

// SPI peripheral chip select line
const uint32_t SPI_DAC_NPCS = 0; // This is PA28, which is Arduino board pin 10

// Number of daisy-chained DACs
const int NUM_CHANNELS = 2;

// Arduino pin number for asynchronous clear (nCLR)
const int ASYNC_CLEAR_PIN = 2;


//
// Waveform data and state
//

// Static descriptions of the waveforms for each channel
struct WaveformBank {
  // Pointer to the start of the waveform table, for each channel. (Channels
  // may share the same table.)
  uint16_t *channel_waveform[NUM_CHANNELS];

  // Starting offset (phase) into the table, for each channel. (The actual
  // array is stored outside of this struct, since it may be shared between
  // multiple banks.)
  size_t *channel_offset;
};

// We use a fixed length for waveform tables; the length may only be changed
// when waveform generation is paused. This makes it easy to switch between
// waveform banks at any time during the scanning, without large disruptions in
// the output (if the waveforms from the two banks are phase-matched).
size_t g_waveform_length = 0;

uint8_t g_n_banks = 0;
uint8_t g_n_tables = 0;

struct WaveformBank *g_banks = NULL;
uint16_t *g_tables = NULL;

inline uint16_t *waveform_table(uint8_t index) {
  return g_tables + (index * g_waveform_length);
}

// For now, we have one global setting for per-channel starting sample indices.
// We could conceivably extend this to allow multiple offset lists.
size_t g_channel_offset[NUM_CHANNELS];

// The current waveform bank. Flip this pointer to switch between banks. IRQ
// for the TC must be disabled while writing to this pointer (so use
// set_bank()). The TC ISR reads but does not modify this pointer. Other ISRs
// do not access this pointer.
struct WaveformBank *volatile g_current_bank;
inline void set_bank(struct WaveformBank *new_bank) {
  NVIC_DisableIRQ(TC_SAMPLER_IRQn);
  g_current_bank = new_bank;
  NVIC_EnableIRQ(TC_SAMPLER_IRQn);
}

// The bank used for the current iteration through the samples. This gets
// updated from g_current_bank before sending channel 0 (so that we do not mix
// channel samples from different banks). Accessed only from ISRs.
struct WaveformBank *g_current_sample_bank = NULL;

// The index of the current or next sample (0 through g_waveform_length - 1)
size_t g_sample_index = 0;

// The next channel whose sample should be sent over SPI. The value is in the
// range 0 to NUM_CHANNELS (inclusive); the value is NUM_CHANNELS while sending
// the sample value for the last channel and 0 while waiting for the next timer
// interrupt.
volatile uint8_t g_next_channel = 0;

bool g_tc_sampler_active = false;

// State for single-value setting (g_next_channel is shared with waveform
// generation).
volatile uint8_t g_sending_adhoc_sample = false;
uint16_t g_adhoc_sample[NUM_CHANNELS];

// The last set values for each DAC channel
volatile uint16_t g_last_sample[NUM_CHANNELS];
inline uint16_t get_last_sample(size_t channel) {
  __disable_irq();
  uint16_t s = g_last_sample[channel];
  __enable_irq();
  return s;
}


//
// Memory management
//

// To avoid heap fragmentation, we allocate in one step the memory for waveform
// tables and banks.
bool reset_waveform_data(size_t waveform_length, uint8_t n_banks, uint8_t n_tables) {
  if (g_tables) {
    free(g_tables);
  }
  if (g_banks) {
    free(g_banks);
  }
  g_current_bank = 0;
  g_banks = NULL;
  g_tables = NULL;
  g_waveform_length = 0;
  g_n_banks = 0;
  g_n_tables = 0;

  if (!waveform_length) {
    return true;
  }

  // Put the banks in lower memory, where there is lower risk of corruption due
  // to clashing with the stack
  if (n_banks) {
    g_banks = (struct WaveformBank *)calloc(n_banks, sizeof(struct WaveformBank));
    if (!g_banks) {
      return false;
    }
  }
  if (n_tables) {
    g_tables = (uint16_t *)calloc(n_tables * waveform_length, sizeof(uint16_t));
    if (!g_tables) {
      free(g_banks);
      return false;
    }
  }

  g_current_bank = g_banks;

  g_waveform_length = waveform_length;
  g_n_banks = n_banks;
  g_n_tables = n_tables;

  // Set default values for banks.
  for (size_t i = 0; i < n_banks; i++) {
    if (n_tables > 0) {
      for (size_t ch = 0; ch < NUM_CHANNELS; ch++) {
        g_banks[i].channel_waveform[ch] = waveform_table(0);
      }
    }
    g_banks[i].channel_offset = g_channel_offset;
  }

  // By default, the waveform tables initially contain invalid data. To
  // facilitate testing from a terminal (without loading a binary waveform),
  // load a test "waveform" if length <= 4 is requested.
  if (waveform_length <= 4) {
    for (uint8_t t = 0; t < n_tables; t++) {
      if (waveform_length >= 1)
        waveform_table(t)[0] = 32768;
      if (waveform_length >= 2)
        waveform_table(t)[1] = 65535;;
      if (waveform_length >= 3)
        waveform_table(t)[2] = 32768;;
      if (waveform_length >= 4)
        waveform_table(t)[3] = 0;;
    }
  }

  return true;
}


//
// Peripheral setup
//

void setup_spi_dac() {
  // Assign the relevant pins to the SPI controller
  uint32_t spi0_pins =
      PIO_ABSR_P25 |  // SPI0_MISO
      PIO_ABSR_P26 |  // SPI0_MOSI
      PIO_ABSR_P27 |  // SPI0_SPCK
      PIO_ABSR_P28 |  // SPI0_NPCS0
      PIO_ABSR_P29 |  // SPI0_NPCS1
      PIO_ABSR_P30 |  // SPI0_NPCS2
      PIO_ABSR_P31;   // SPI0_NPCS3
  PIO_Configure(PIOA, PIO_PERIPH_A, spi0_pins, PIO_DEFAULT);

  SPI_Configure(SPI_DAC, ID_SPI_DAC, SPI_MR_MSTR | SPI_MR_PS);
  SPI_Enable(SPI_DAC);
  
  SPI_ConfigureNPCS(SPI_DAC, SPI_DAC_NPCS,
      SPI_CSR_CPOL | (SPI_CSR_NCPHA & 0) |  // SPI Mode 3
      SPI_DLYBCT(0, VARIANT_MCK) | SPI_DLYBS(60, VARIANT_MCK) |
      SPI_SCBR(8400000, VARIANT_MCK) | SPI_CSR_CSAAT | SPI_CSR_BITS_16_BIT);

  SPI_DAC->SPI_IDR = ~SPI_IDR_TDRE;
  SPI_DAC->SPI_IER = SPI_IER_TDRE;
  
  // We do not enable the IRQ line itself here; we only want TDRE interrupts
  // when we are actully sending someting words.
}


void setup_tc_sampler() {
  // Turn on power for the TC
  pmc_enable_periph_clk(ID_TC_SAMPLER);

  // Set the TC to count up to the value of RC (TC register C), and generate RC
  // compare interrupts.

  TC_Configure(TC_SAMPLER, TC_SAMPLER_CHANNEL,
      TC_CMR_WAVE | TC_CMR_WAVSEL_UP_RC | TC_SAMPLER_TIMER_CLOCK);

  TC_SAMPLER->TC_CHANNEL[TC_SAMPLER_CHANNEL].TC_IDR = ~TC_IDR_CPCS;
  TC_SAMPLER->TC_CHANNEL[TC_SAMPLER_CHANNEL].TC_IER = TC_IER_CPCS;

  // Default to the lowest possible sample rate
  TC_SetRC(TC_SAMPLER, TC_SAMPLER_CHANNEL, 0xFFFF);
}


void start_tc_sampler() {
  g_tc_sampler_active = true;
  NVIC_ClearPendingIRQ(TC_SAMPLER_IRQn);
  TC_Start(TC_SAMPLER, TC_SAMPLER_CHANNEL);
  NVIC_EnableIRQ(TC_SAMPLER_IRQn);
}


void stop_tc_sampler() {
  NVIC_DisableIRQ(TC_SAMPLER_IRQn);
  TC_Stop(TC_SAMPLER, TC_SAMPLER_CHANNEL);
  g_tc_sampler_active = false;
}


void set_tc_sampler_freq(uint32_t freq) {
  uint32_t tick_count = TC_SAMPLER_TICK_FREQ / freq;

  bool tc_active = g_tc_sampler_active;
  if (tc_active) {
    stop_tc_sampler();
  }
  TC_SetRC(TC_SAMPLER, TC_SAMPLER_CHANNEL, tick_count);
  if (tc_active) {
    start_tc_sampler();
  }
}


// Send the sample for the next channel, and increment the next-channel index.
// May be called from ISRs
void send_channel_sample() {
  uint16_t sample;
  if (g_sending_adhoc_sample) {
    sample = g_adhoc_sample[g_next_channel];
  }
  else {
    uint16_t *waveform = g_current_sample_bank->channel_waveform[g_next_channel];
    size_t offset = g_current_sample_bank->channel_offset[g_next_channel];

    size_t index = offset + g_sample_index;
    if (index >= g_waveform_length) {
      index -= g_waveform_length;
    }

    sample = waveform[index];
  }

  g_last_sample[g_next_channel] = sample;

  uint32_t td = sample | SPI_PCS(SPI_DAC_NPCS);
  if (++g_next_channel == NUM_CHANNELS) {
    td |= SPI_TDR_LASTXFER;
  }

  SPI_DAC->SPI_TDR = td;
  NVIC_EnableIRQ(SPI_DAC_IRQn);
}


//
// Interrupt service routines for SPI transmission
//

// On the timer interrupt, we determine the current waveform bank and start
// sending the sample for channel 0.
void TC_Sampler_Handler() {
  // Access the TC to deassert (acknowledge) the IRQ
  TC_GetStatus(TC_SAMPLER, TC_SAMPLER_CHANNEL);

  g_current_sample_bank = g_current_bank;
  g_next_channel = 0;

  send_channel_sample();
}


// Since we have enabled the SPI IRQ for TDRE (transmit data register empty),
// this ISR gets called every time we finish sending a word (sample).
// If we have not finished, we send the sample for the next channel. If we have
// finished with all channels, we reset the channel counter and increment the
// sample index. Note that g_next_channel > 0 always holds when this ISR is
// called.
void SPI_DAC_Handler() {
  NVIC_DisableIRQ(SPI_DAC_IRQn);
  
  if (g_next_channel >= NUM_CHANNELS) {
    g_next_channel = 0;

    if (g_sending_adhoc_sample) {
      g_sending_adhoc_sample = false;
    }
    else {
      g_sample_index++;
      if (g_sample_index >= g_waveform_length) {
        g_sample_index = 0;
      }
    }

    return;
  }

  send_channel_sample();
}


//
// Non-ISR routines for SPI transmission
//

// Start sending a single, ad hoc sample.
void send_adhoc_sample(uint16_t *channel_samples) {
  for (size_t i = 0; i < NUM_CHANNELS; i++) {
    g_adhoc_sample[i] = channel_samples[i];
  }
  g_sending_adhoc_sample = true;

  g_next_channel = 0;
  send_channel_sample();
}


// Cannot be called from ISRs (obviously)
void wait_for_spi_dac() {
  while (g_next_channel != 0) {
    // Wait until we finish sending the last channel's sample
  }
}


//
// Asynchronous clearing of the DAC (AD660 nCLR pin)
//

// This operates completely independently of the SPI transmission.
void set_dac_cleared(bool clear) {
  digitalWrite(ASYNC_CLEAR_PIN, (clear ? LOW : HIGH));
}


//
// Debug buffer
//

void clear_debug_message() {
  g_debug_buffer[0] = '\0';
}


void append_debug_message(const char* str) {
  strncat(g_debug_buffer, str,
      DEBUG_BUFFER_LEN - 1 - strlen(g_debug_buffer));
}


void append_debug_message(uint32_t num) {
  int len = strlen(g_debug_buffer);
  snprintf(g_debug_buffer + len, DEBUG_BUFFER_LEN - 1 - len,
      "%u", num);
}


//
// Serial command handling
//

void respond(const char *str, const char *msg) {
  Serial.print(str);
  Serial.print(msg);
  Serial.write(SERIAL_TERMINATOR);
}


void respond(const char *str, uint32_t num) {
  Serial.print(str);
  Serial.print(num);
  Serial.write(SERIAL_TERMINATOR);
}


void respond(const char *str) {
  Serial.print(str);
  Serial.write(SERIAL_TERMINATOR);
}


inline void respond_error(uint32_t code) {
  respond("ER", code);
}


inline void respond_data_request(size_t bytes) {
  respond("EX", bytes);
}


inline void respond_ok(const char* response) {
  respond("OK", response);
}


inline void respond_ok(uint32_t response) {
  respond("OK", response);
}


inline void respond_ok() {
  respond("OK");
}


int parse_uint(const char *buffer, size_t *p, uint32_t *result) {
  *result = 0;
  if (!isdigit(buffer[*p])) {
    return DFGERR_EXPECTED_UINT;
  }

  do {
    if (*result >= UINT32_MAX / 10) {
      return DFGERR_OVERFLOW;
    }
    *result *= 10;
    *result += buffer[(*p)++] - '0';
  } while (isdigit(buffer[*p]));
  return DFGERR_OK;
}


int parse_uint_params(const char *buffer, uint32_t *params, size_t num_params) {
  size_t p = 0;
  for (int i = 0; i < num_params; i++) {
    if (int err = parse_uint(buffer, &p, &params[i])) {
      return err;
    }

    if (i == num_params - 1) {
      break;
    }

    if (buffer[p++] != ',') {
      return DFGERR_TOO_FEW_PARAMS;
    }
  }

  if (buffer[p] == ',') {
    return DFGERR_TOO_MANY_PARAMS;
  }
  if (buffer[p] != '\0') {
    return DFGERR_BAD_PARAMS;
  }
  return DFGERR_OK;
}


// Command handlers in alphabetical order


void handle_AW(const char *param_str) {
  uint32_t params[3];
  if (int err = parse_uint_params(param_str, params, 3)) {
    respond_error(err);
    return;
  }

  if (params[0] >= g_n_tables) {
    respond_error(DFGERR_INVALID_TABLE);
    return;
  }

  if (params[1] >= g_n_banks) {
    respond_error(DFGERR_INVALID_BANK);
    return;
  }

  if (params[2] >= NUM_CHANNELS) {
    respond_error(DFGERR_INVALID_CHANNEL);
    return;
  }

  if (g_tc_sampler_active) {
    respond_error(DFGERR_BUSY);
    return;
  }

  g_banks[params[1]].channel_waveform[params[2]] = waveform_table(params[0]);
  respond_ok();
}


void handle_DM(const char *param_str) {
  uint32_t params[3];
  if (int err = parse_uint_params(param_str, params, 3)) {
    respond_error(err);
    return;
  }

  size_t waveform_length = params[0];
  if (params[1] > UINT8_MAX) {
    respond_error(DFGERR_OVERFLOW);
    return;
  }
  uint8_t n_banks = params[1];
  if (params[2] > UINT8_MAX) {
    respond_error(DFGERR_OVERFLOW);
    return;
  }
  uint8_t n_tables = params[2];

  if (g_tc_sampler_active) {
    respond_error(DFGERR_BUSY);
    return;
  }

  if (reset_waveform_data(waveform_length, n_banks, n_tables)) {
    respond_ok();
  }
  else {
    respond_error(DFGERR_BAD_ALLOC);
  }
}


void handle_ED(const char *param_str) {
  uint32_t params[1];
  if (int err = parse_uint_params(param_str, params, 1)) {
    respond_error(err);
    return;
  }

  bool enable = params[0];
  set_dac_cleared(!enable);
  respond_ok();
}


void handle_FQ(const char *param_str) {
  uint32_t params[1];
  if (int err = parse_uint_params(param_str, params, 1)) {
    respond_error(err);
    return;
  }

  if (params[0] < MIN_SAMPLE_FREQ || params[0] > MAX_SAMPLE_FREQ) {
    respond_error(DFGERR_FREQ_OUT_OF_RANGE);
    return;
  }

  set_tc_sampler_freq(params[0]);
  respond_ok();
}


void handle_GM(const char *param_str) {
  if (param_str[0] != '\0') {
    // respond_error(DFGERR_TOO_MANY_PARAMS);
    // return;
  }

  char buf[DEBUG_BUFFER_LEN];
  char *p = g_debug_buffer, *q = buf;
  char *end = q + DEBUG_BUFFER_LEN - 1;

  while (*p && q < end) {
    if (*p != SERIAL_TERMINATOR)
      *q++ = *p++;
    else {
      if (end - q < 2)
        break;
      p++;
      *q++ = '\\';
      *q++ = (SERIAL_TERMINATOR == '\r' ? 'r' : 'n');
    }
  }
  *q = '\0';

  respond_ok(buf);
}


void handle_HL(const char *param_str) {
  if (param_str[0] != '\0') {
    respond_error(DFGERR_TOO_MANY_PARAMS);
    return;
  }

  stop_tc_sampler();
  wait_for_spi_dac();
  respond_ok();
}


void handle_ID(const char *param_str) {
  if (param_str[0] != '\0') {
    respond_error(DFGERR_TOO_MANY_PARAMS);
    return;
  }
  respond_ok(DFG_SERIAL_MAGIC_ID);
}


void handle_LW(const char *param_str) {
  uint32_t params[1];
  if (int err = parse_uint_params(param_str, params, 1)) {
    respond_error(err);
    return;
  }

  if (params[0] >= g_n_tables) {
    respond_error(DFGERR_INVALID_TABLE);
    return;
  }

  if (g_tc_sampler_active) {
    respond_error(DFGERR_BUSY);
    return;
  }

  uint16_t *table = waveform_table(params[0]);
  size_t length = sizeof(uint16_t) * g_waveform_length;

  Serial.setTimeout(SERIAL_BINARY_TIMEOUT);
  respond_data_request(length);
  size_t bytes = Serial.readBytes((char *)table, length);
  Serial.setTimeout(SERIAL_TEXT_TIMEOUT);
  if (bytes < length) {
    respond_error(DFGERR_TIMEOUT);
    return;
  }
  respond_ok();
}


void handle_MN(const char *param_str) {
  if (param_str[0] != '\0') {
    respond_error(DFGERR_TOO_MANY_PARAMS);
    return;
  }

  respond_ok(MIN_SAMPLE_FREQ);
}


void handle_MV(const char *param_str) {
  uint32_t params[2];
  if (int err = parse_uint_params(param_str, params, 2)) {
    respond_error(err);
    return;
  }

  if (params[0] >= NUM_CHANNELS) {
    respond_error(DFGERR_INVALID_CHANNEL);
    return;
  }

  if (params[1] > UINT16_MAX) {
    respond_error(DFGERR_INVALID_SAMPLE_VALUE);
    return;
  }

  if (g_tc_sampler_active) {
    respond_error(DFGERR_BUSY);
    return;
  }

  uint16_t sample[NUM_CHANNELS];
  for (size_t i = 0; i < NUM_CHANNELS; i++) {
    sample[i] = get_last_sample(i);
  }
  sample[params[0]] = params[1];
  send_adhoc_sample(sample);
  wait_for_spi_dac();
  respond_ok();
  return;
}


void handle_MX(const char *param_str) {
  if (param_str[0] != '\0') {
    respond_error(DFGERR_TOO_MANY_PARAMS);
    return;
  }

  respond_ok(MAX_SAMPLE_FREQ);
}


void handle_PH(const char *param_str) {
  uint32_t params[2];
  if (int err = parse_uint_params(param_str, params, 2)) {
    respond_error(err);
    return;
  }

  if (params[0] >= NUM_CHANNELS) {
    respond_error(DFGERR_INVALID_CHANNEL);
    return;
  }

  if (params[1] >= g_waveform_length) {
    respond_error(DFGERR_INVALID_PHASE_OFFSET);
    return;
  }

  if (g_tc_sampler_active) {
    respond_error(DFGERR_BUSY);
    return;
  }

  g_channel_offset[params[0]] = params[1];
  respond_ok();
}


void handle_RN(const char *param_str) {
  if (param_str[0] != '\0') {
    respond_error(DFGERR_TOO_MANY_PARAMS);
    return;
  }

  if (g_n_banks == 0 || g_n_tables == 0) {
    respond_error(DFGERR_NO_WAVEFORM);
    return;
  }

  start_tc_sampler();
  respond_ok();
}


void handle_SB(const char *param_str) {
  uint32_t params[1];
  if (int err = parse_uint_params(param_str, params, 1)) {
    respond_error(err);
    return;
  }

  if (params[0] >= g_n_banks) {
    respond_error(DFGERR_INVALID_BANK);
    return;
  }

  struct WaveformBank *bank = &g_banks[params[0]];
  set_bank(bank);
  respond_ok();
}


void handle_WH(const char *param_str) {
  uint32_t params[1];
  if (int err = parse_uint_params(param_str, params, 1)) {
    respond_error(err);
    return;
  }

  if (params[0] >= NUM_CHANNELS) {
    respond_error(DFGERR_INVALID_CHANNEL);
    return;
  }

  if (g_tc_sampler_active) {
    respond_error(DFGERR_BUSY);
    return;
  }

  respond_ok(get_last_sample(params[0]));
}


void dispatch_cmd(char first, char second, const char *param_str) {
  if (first == 'G' && second == 'M') {
    handle_GM(param_str); // Must call before clearing debug message buffer
    return;
  }

  clear_debug_message();

  if (first == 'A') {
    if (second == 'W') { handle_AW(param_str); return; }
  }
  else if (first == 'D') {
    if (second == 'M') { handle_DM(param_str); return; }
  }
  else if (first == 'E') {
    if (second == 'D') { handle_ED(param_str); return; }
  }
  else if (first == 'F') {
    if (second == 'Q') { handle_FQ(param_str); return; }
  }
  else if (first == 'H') {
    if (second == 'L') { handle_HL(param_str); return; }
  }
  else if (first == 'I') {
    if (second == 'D') { handle_ID(param_str); return; }
  }
  else if (first == 'L') {
    if (second == 'W') { handle_LW(param_str); return; }
  }
  else if (first == 'M') {
    if (second == 'N') { handle_MN(param_str); return; }
    if (second == 'V') { handle_MV(param_str); return; }
    if (second == 'X') { handle_MX(param_str); return; }
  }
  else if (first == 'P') {
    if (second == 'H') { handle_PH(param_str); return; }
  }
  else if (first == 'R') {
    if (second == 'N') { handle_RN(param_str); return; }
  }
  else if (first == 'S') {
    if (second == 'B') { handle_SB(param_str); return; }
  }
  else if (first == 'W') {
    if (second == 'H') { handle_WH(param_str); return; }
  }

  respond_error(DFGERR_BAD_CMD);

  char cmd[3];
  cmd[0] = first; cmd[1] = second; cmd[2] = '\0';
  append_debug_message(cmd);
  append_debug_message("|");
  append_debug_message(param_str);
}


void handle_cmd() {
  char cmd_buffer[MAX_CMD_LEN];
  size_t cmd_len = Serial.readBytesUntil(SERIAL_TERMINATOR, cmd_buffer, MAX_CMD_LEN);

  if (cmd_len == 0) {
    // Empty command - ignore
    return;
  }

  if (cmd_len == MAX_CMD_LEN) {
    while (Serial.readBytesUntil(SERIAL_TERMINATOR, cmd_buffer, MAX_CMD_LEN) > 0) {
      // Discard the rest of the command
    }
    clear_debug_message();
    respond_error(DFGERR_CMD_TOO_LONG);
    return;
  }

  // Append a null terminator to the command string
  cmd_buffer[cmd_len] = '\0';

  if (cmd_len < 2) {
    clear_debug_message();
    respond_error(DFGERR_BAD_CMD);
    append_debug_message(cmd_len);
    append_debug_message("|");
    append_debug_message(cmd_buffer);
    return;
  }

  dispatch_cmd(cmd_buffer[0], cmd_buffer[1], cmd_buffer + 2);
}


//
// Arduino main program
//

void setup() {
  pinMode(ASYNC_CLEAR_PIN, OUTPUT);
  set_dac_cleared(true);

  clear_debug_message();

  // Set the UART (USB-serial) IRQ priority lower than the TC and SPI
  // interrupts. Without this, UART interrupts can disrupt waveform generation.
  NVIC_SetPriority(UART_IRQn, 1);

  Serial.begin(SERIAL_BAUDRATE);
  Serial.setTimeout(SERIAL_TEXT_TIMEOUT);

  setup_spi_dac();
  setup_tc_sampler();

  // Set DAC values to default (midpoint) and bring g_last_sample into sync
  uint16_t sample[NUM_CHANNELS];
  for (size_t i = 0; i < NUM_CHANNELS; i++) {
    sample[i] = UINT16_MAX / 2;
  }
  send_adhoc_sample(sample);
  wait_for_spi_dac();
}

void loop() {
  // Kind of silly to have to poll, but otherwise we'd need to forgo the
  // Arduino serial library and things would get much more complicated.
  if (Serial.available()) {
    handle_cmd();
  }
}
