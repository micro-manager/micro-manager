/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.model.devices;

import com.asiimaging.plogic.PLogicControlFrame;
import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.ui.utils.DialogUtils;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.micromanager.Studio;

/**
 * The ASI Tiger Programmable Logic Card is a field-programmable card for digital logic.
 *
 * <p>Documentation:
 *      <a href="http://asiimaging.com/docs/tiger_programmable_logic_card">ASI PLogic</a>
 */
public class ASIPLogic extends ASITigerBase {

   public static final String DEVICE_LIBRARY = "ASITiger";
   public static final String DEVICE_DESC_PREFIX = "ASI Programmable Logic";

   private int pointerPosition_;
   private PLogicState state_;
   private boolean isRefreshed_;

   public ASIPLogic(final Studio studio, final String deviceName) {
      super(studio, deviceName);
      state_ = new PLogicState(numCells());
      // always init to false, this will be true when all cells
      // have been updated from the controller
      isRefreshed_ = false;
      // cache initial pointer position
      pointerPosition_ = pointerPosition();
   }

   public PLogicState state() {
      return state_;
   }

   /**
    * Set the {@code PLogicState} created from a json {@code String}.
    *
    * @param json the state as json {@code String}
    */
   public void state(final String json) {
      final PLogicState state = PLogicState.fromJson(json);
      // validate that the data works with the firmware build
      final int stateNumCells = state.numCells();
      if (numCells() != stateNumCells) {
         DialogUtils.showMessage(null, "Number of Cells Mismatch",
               "The loaded data is for a " + stateNumCells
                     + " cell PLogic build, but you have a " + numCells() + " cell build.");
      } else {
         state_ = state;
      }
   }

   /**
    * Return {@code true} if all cells have been queried through serial commands. This ensures that
    * the internal state representation and the {@code ASIPLogic} state are in sync and the object
    * can be serialized. The {@code updateState()} method sets this state to {@code true}.
    *
    * @return {@code true} if in a serializable state
    */
   public boolean isRefreshed() {
      return isRefreshed_;
   }

   /**
    * Query the controller with serial commands, updating the internal state representation for
    * the PLogic device. The {@code isRefreshed()} method queries if this method has been run
    * at least once for this device.
    */
   public void updateState(final PLogicControlModel model, final PLogicControlFrame frame) {
      state_.updateCells(model, frame);
      isRefreshed_ = true;
   }

   // Read-only properties

   public int numCells() {
      return getPropertyInt(Properties.ReadOnly.NUM_LOGIC_CELLS);
   }

   public String axisLetter() {
      return getProperty(Properties.ReadOnly.AXIS_LETTER);
   }

   public int backplaneOutputState() {
      return getPropertyInt(Properties.ReadOnly.BACKPLANE_OUTPUT_STATE);
   }

   public int frontpanelOutputState() {
      return getPropertyInt(Properties.ReadOnly.FRONTPANEL_OUTPUT_STATE);
   }

   public int plogicOutputState() {
      return getPropertyInt(Properties.ReadOnly.PLOGIC_OUTPUT_STATE);
   }

   // Properties

   public void saveSettings() {
      setProperty(Properties.SAVE_CARD_SETTINGS, ASIPLogic.SaveSettings.Z.toString());
   }

   public void clearAllCellStates() {
      setProperty(Properties.CLEAR_ALL_CELL_STATES, Values.DO_IT);
   }

   public void triggerSource(final TriggerSource triggerSource) {
      setProperty(Properties.TRIGGER_SOURCE, triggerSource.toString());
      state_.triggerSource(triggerSource);
   }

   public TriggerSource triggerSource() {
      final TriggerSource source = TriggerSource.fromString(getProperty(Properties.TRIGGER_SOURCE));
      state_.triggerSource(source);
      return source;
   }

   public void pointerPosition(final int position) {
      setPropertyInt(Properties.POINTER_POSITION, position);
      pointerPosition_ = position;
   }

   public int pointerPosition() {
      final int pointerPosition = getPropertyInt(Properties.POINTER_POSITION);
      pointerPosition_ = pointerPosition;
      return pointerPosition;
   }

   public void cellType(final CellType type) {
      setProperty(Properties.EDIT_CELL_TYPE, type.toString());
      state_.cell(pointerPosition_).type(type);
   }

   public CellType cellType() {
      final CellType type = CellType.fromString(getProperty(Properties.EDIT_CELL_TYPE));
      state_.cell(pointerPosition_).type(type);
      return type;
   }

   public void ioType(final IOType type) {
      setProperty(Properties.EDIT_CELL_TYPE, type.toString());
      state_.io(pointerPosition_).type(type);
   }

   public IOType ioType() {
      final IOType type = IOType.fromString(getProperty(Properties.EDIT_CELL_TYPE));
      state_.io(pointerPosition_).type(type);
      return type;
   }

   // TODO: track this? make it a setting?
   public OutputChannel outputChannel() {
      return OutputChannel.fromString(getProperty(Properties.OUTPUT_CHANNEL));
   }

   public void sourceAddress(final int value) {
      setPropertyInt(Properties.EDIT_CELL_CONFIG, value);
      state_.io(pointerPosition_).sourceAddress(value);
   }

   public int sourceAddress() {
      final int sourceAddr = getPropertyInt(Properties.EDIT_CELL_CONFIG);
      state_.io(pointerPosition_).sourceAddress(sourceAddr);
      return sourceAddr;
   }

   public void cellConfig(final int value) {
      setPropertyInt(Properties.EDIT_CELL_CONFIG, value);
      state_.cell(pointerPosition_).config(value);
   }

   public int cellConfig() {
      final int config = getPropertyInt(Properties.EDIT_CELL_CONFIG);
      state_.cell(pointerPosition_).config(config);
      return config;
   }

   public void isAutoUpdateCellsOn(final boolean state) {
      setProperty(Properties.EDIT_CELL_UPDATE_AUTO, state ? Values.YES : Values.NO);
   }

   public boolean isAutoUpdateCellsOn() {
      return getProperty(Properties.EDIT_CELL_UPDATE_AUTO).equals(Values.YES);
   }

   public void preset(final int code) {
      setProperty(Properties.SET_CARD_PRESET, Preset.fromCode(code));
   }

   // TODO: presets besides ALL_CELLS_ZERO will not update the internal PLogicState,
   //   it may be complicated because you have to know how to update the state unless
   //   you want to query the controller, which is slow.
   public void preset(final Preset preset) {
      setProperty(Properties.SET_CARD_PRESET, preset.toString());
      if (preset == Preset.ALL_CELLS_ZERO) {
         state_.clearLogicCells();
      }
   }

   public Preset preset() {
      return Preset.fromString(getProperty(Properties.SET_CARD_PRESET));
   }

   public int presetCode() {
      return Preset.fromString(getProperty(Properties.SET_CARD_PRESET)).toCode();
   }

   public void cellInput(final int num, final int address) {
      if (num < 1 || num > 4) {
         throw new IllegalArgumentException("Each cell only has inputs 1-4.");
      }
      setPropertyInt(Properties.EDIT_CELL_INPUT + num, address);
      state_.cell(pointerPosition_).input(num, address);
   }

   /**
    * Return the value of the input.
    *
    * @param input input 1-4
    * @return input value
    */
   public int cellInput(final int input) {
      if (input < 1 || input > 4) {
         throw new IllegalArgumentException("Each cell only has inputs 1-4.");
      }
      final int value = getPropertyInt(Properties.EDIT_CELL_INPUT + input);
      state_.cell(pointerPosition_).input(input, value);
      return value;
   }

   public ShutterMode shutterMode() {
      return ShutterMode.fromString(getProperty(Properties.PLOGIC_MODE));
   }

   // Properties Names
   public static class Properties extends ASITigerBase.Properties {
      public static final String PLOGIC_MODE = "PLogicMode"; // pre-init property

      public static final String CLEAR_ALL_CELL_STATES = "ClearAllCellStates";
      public static final String EDIT_CELL_UPDATE_AUTO = "EditCellUpdateAutomatically";
      public static final String POINTER_POSITION = "PointerPosition";
      public static final String TRIGGER_SOURCE = "TriggerSource";
      public static final String SET_CARD_PRESET = "SetCardPreset";
      public static final String SAVE_CARD_SETTINGS = "SaveCardSettings";
      public static final String OUTPUT_CHANNEL = "OutputChannel";
      public static final String EDIT_CELL_TYPE = "EditCellCellType";
      public static final String EDIT_CELL_CONFIG = "EditCellConfig";
      public static final String EDIT_CELL_INPUT = "EditCellInput"; // EditCellInput 1 - 4

      public static class ReadOnly {
         public static final String BACKPLANE_OUTPUT_STATE = "BackplaneOutputState";
         public static final String FRONTPANEL_OUTPUT_STATE = "FrontpanelOutputState";
         public static final String PLOGIC_OUTPUT_STATE = "PLogicOutputState";
         public static final String NUM_LOGIC_CELLS = "NumLogicCells";
         public static final String AXIS_LETTER = "AxisLetter";
      }
   }

   // Property Values
   public static class Values extends ASITigerBase.Values {
      public static final String NO = "No";
      public static final String YES = "Yes";
      public static final String DO_IT = "Do it";
   }

   public enum Preset {
      NO_PRESET("no preset", -1),
      ALL_CELLS_ZERO("0 - cells all 0", 0),
      ORIGINAL_SPIM_TTL_CARD("1 - original SPIM TTL card", 1),
      CELL_1_LOW("2 - cell 1 low", 2),
      CELL_1_HIGH("3 - cell 1 high", 3),
      COUNTER_16BIT("4 - 16 bit counter", 4),
      BNC5_ENABLED("5 - BNC5 enabled", 5),
      BNC6_ENABLED("6 - BNC6 enabled", 6),
      BNC7_ENABLED("7 - BNC7 enabled", 7),
      BNC8_ENABLED("8 - BNC8 enabled", 8),
      BNC5_TO_BNC8_DISABLED("9 - BNC5-BNC8 all disabled", 9),
      CELL_8_LOW("10 - cell 8 low", 10),
      CELL_8_HIGH("11 - cell 8 high", 11),
      PRESET_12("12 - cell 10 = (TTL1 AND cell 8)", 12),
      PRESET_13("13 - BNC4 source = (TTL3 AND (cell 10 or cell 1))", 13),
      DISPIM_TLL("14 - diSPIM TTL", 14),
      MOD4_COUNTER("15 - mod4 counter", 15),
      MOD3_COUNTER("16 - mod3 counter", 16),
      COUNTER_CLOCK_FALLING_TTL1("17 - counter clock = falling TTL1", 17),
      COUNTER_CLOCK_FALLING_TTL3("18 - counter clock = falling TTL3", 18),
      BNC1_8_ON_9_16("19 - cells 9-16 on BNC1-8", 19),
      BNC5_8_ON_13_16("20 - cells 13-16 on BNC5-8", 20), // CELLS_13_TO_16_ON_BNC5_TO_BNC8
      MOD2_COUNTER("21 - mod2 counter", 21),
      NO_COUNTER("22 - no counter", 22),
      TTL0_7_ON_BNC1_8("23 - TTL0-7 on BNC1-8", 23),
      BNC3_SOURCE_CELL_1("24 - BNC3 source = cell 1", 24),
      BNC3_SOURCE_CELL_8("25 - BNC3 source = cell 8", 25),
      COUNTER_CLOCK_RISING_TTL3("26 - counter clock = rising TTL3", 26),
      BNC3_SOURCE_EQ_CELL_10("27 - BNC3 source = cell 10", 27),
      BNC6_AND_BNC7_ENABLED("28 - BNC6 and BNC7 enabled", 28),
      BNC5_TO_BNC7_ENABLED("29 - BNC5-BNC7 enabled", 29),
      BNC5_TO_BNC8_ENABLED("30 - BNC5-BNC8 enabled", 30),
      BNC5_7_SIDEA_BNC6_8_SIDEB("31 - BNC5/7 side A, BNC6/8 side B", 31),
      BNC1_2_AS_CAMERA_A_AND_B("32 - BNC1/2 as cameras A/B", 32),
      BNC1_2_AS_CAMERA_A_OR_B("33 - BNC1/2 as cameras A or B", 33),
      CELL_11_TRIGGER_DIV_2("34 - cell 11 as trigger/2", 34),
      BNC3_SOURCE_CELL_11("35 - BNC3 source = cell 11", 35),
      CELL_10_EQ_CELL_8("36 - cell 10 = cell 8", 36),
      BNC1_8_ON_17_24("51 - cells 17-24 on BNC1-8", 51),
      BNC3_SOURCE_TTL5("52 - BNC3 source = TTL5", 52);

      private final String text_;
      private final int code_;

      private static final Map<String, Preset> stringToEnum =
            Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

      private static final Map<Integer, String> presets =
            Stream.of(values()).collect(Collectors.toMap(Preset::toCode, Object::toString));

      Preset(final String text, final int code) {
         text_ = text;
         code_ = code;
      }

      @Override
      public String toString() {
         return text_;
      }

      public int toCode() {
         return code_;
      }

      public static String[] toArray() {
         return Arrays.stream(values())
               .map(Preset::toString)
               .toArray(String[]::new);
      }

      public static Preset fromString(final String symbol) {
         return stringToEnum.getOrDefault(symbol, Preset.NO_PRESET);
      }

      public static String fromCode(final int code) {
         return presets.getOrDefault(code, "NONE");
      }
   }

   public enum CellType {
      CONSTANT(
            "0 - constant", 0, "Configuration", "", "", "", "", false),
      D_FLOP(
            "1 - D flop", 4, "", "Din", "Clock", "Reset", "Preset", true),
      LUT2(
            "2 - 2-input LUT", 2, "LUT Value", "A", "B", "", "", false),
      LUT3(
            "3 - 3-input LUT", 3, "LUT Value", "A", "B", "C", "", false),
      LUT4(
            "4 - 4-input LUT", 4, "LUT Value", "A", "B", "C", "D", false),
      AND2(
            "5 - 2-input AND", 2, "", "A", "B", "", "", false),
      OR2(
            "6 - 2-input OR", 2, "", "A", "B", "", "", false),
      XOR2(
            "7 - 2-input XOR", 2, "", "A", "B", "", "", false),
      ONE_SHOT(
            "8 - one shot", 3, "Duration", "Trigger", "Clock", "Reset", "", true),
      DELAY(
            "9 - delay", 3, "Delay", "Trigger", "Clock", "Reset", "", true),
      AND4(
            "10 - 4-input AND", 4, "", "A", "B", "C", "D", false),
      OR4(
            "11 - 4-input OR", 4, "", "A", "B", "C", "D", false),
      D_FLOP_SYNC(
            "12 - D flop (sync)", 4, "", "Din", "Clock", "Reset", "Preset", true),
      JK_FLOP(
            "13 - JK flop", 3, "", "J", "K", "Clock", "", true),
      ONE_SHOT_NRT(
            "14 - one shot (NRT)", 3, "Duration", "Trigger", "Clock", "Reset", "", true),
      DELAY_NRT(
            "15 - delay (NRT)", 3, "Delay", "Trigger", "Clock", "Reset", "", true),
      ONE_SHOT_OR2_NRT(
            "16 - one shot OR2 (NRT)",
            4, "Duration", "Trigger A", "Clock", "Reset", "Trigger B", true),
      DELAY_OR2_NRT(
            "17 - delay OR2 (NRT)",
            4, "Delay", "Trigger A", "Clock", "Reset", "Trigger B", true);

      private final String propertyName_;
      private final int numInputs_;
      private final String configName_;
      private final String input1_;
      private final String input2_;
      private final String input3_;
      private final String input4_;
      private final boolean hasState_;

      private static final Map<String, CellType> stringToEnum =
            Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

      CellType(final String propertyName,
               final int numInputs,
               final String configName,
               final String input1,
               final String input2,
               final String input3,
               final String input4,
               final boolean hasState) {
         propertyName_ = propertyName;
         numInputs_ = numInputs;
         configName_ = configName;
         input1_ = input1;
         input2_ = input2;
         input3_ = input3;
         input4_ = input4;
         hasState_ = hasState;
      }

      @Override
      public String toString() {
         return propertyName_;
      }

      public int numInputs() {
         return numInputs_;
      }

      public String configName() {
         return configName_;
      }

      public String inputName(final int num) {
         switch (num) {
            case 1:
               return input1_;
            case 2:
               return input2_;
            case 3:
               return input3_;
            case 4:
               return input4_;
            default:
               return "";
         }
      }

      // TODO: make this faster and more robust, for example do not rely on startsWith
      public boolean isEdgeSensitive(final int inputNum) {
         final String inputName = inputName(inputNum);
         return inputName.startsWith("Trigger") || inputName.startsWith("Clock");
      }

      public boolean hasState() {
         return hasState_;
      }

      public static String[] toArray() {
         return Arrays.stream(values())
               .map(CellType::toString)
               .toArray(String[]::new);
      }

      public static CellType fromString(final String symbol) {
         return stringToEnum.getOrDefault(symbol, CellType.CONSTANT);
      }
   }

   public enum SaveSettings {
      X("X - reload factory default on startup to card"),
      Y("Y - restore last saved settings from card"),
      Z("Z - save settings to card (partial)"),
      NO_ACTION("no action"),
      SAVE_SETTINGS_DONE("save settings done");

      private final String text_;

      private static final Map<String, SaveSettings> stringToEnum =
            Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

      SaveSettings(final String text) {
         text_ = text;
      }

      @Override
      public String toString() {
         return text_;
      }

      public static String[] toArray() {
         return Arrays.stream(values())
               .map(SaveSettings::toString)
               .toArray(String[]::new);
      }

      public static SaveSettings fromString(final String symbol) {
         return stringToEnum.getOrDefault(symbol, SaveSettings.NO_ACTION);
      }
   }

   public enum TriggerSource {
      INTERNAL("0 - internal 4kHz"),
      MICRO_MIRROR_CARD("1 - Micro-mirror card"),
      BACKPLANE_TTL5("2 - backplane TTL5"),
      BACKPLANE_TTL7("3 - backplane TTL7"),
      FRONTPANEL_BNC1("4 - frontpanel BNC 1");

      private final String text_;

      private static final Map<String, TriggerSource> stringToEnum =
            Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

      TriggerSource(final String text) {
         text_ = text;
      }

      @Override
      public String toString() {
         return text_;
      }

      public static String[] toArray() {
         return Arrays.stream(values())
               .map(TriggerSource::toString)
               .toArray(String[]::new);
      }

      public static TriggerSource fromString(final String symbol) {
         return stringToEnum.getOrDefault(symbol, TriggerSource.INTERNAL);
      }
   }

   // pre-init property
   public enum ShutterMode {
      NONE("None"),
      FOUR_CHANNEL_SHUTTER("Four-channel shutter"),
      DISPIM_SHUTTER("diSPIM Shutter"),
      SEVEN_CHANNEL_SHUTTER("Seven-channel shutter"),
      SEVEN_CHANNEL_TTL_SHUTTER("Seven-channel TTL shutter");

      private final String text_;

      private static final Map<String, ShutterMode> stringToEnum =
            Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

      ShutterMode(final String text) {
         text_ = text;
      }

      @Override
      public String toString() {
         return text_;
      }

      public static ShutterMode fromString(final String symbol) {
         return stringToEnum.getOrDefault(symbol, ShutterMode.NONE);
      }
   }

   public enum IOType {
      INPUT("0 - input"),
      OUTPUT_OPEN_DRAIN("1 - output (open-drain)"),
      OUTPUT_PUSH_PULL("2 - output (push-pull)");

      private final String text_;

      private static final Map<String, IOType> stringToEnum =
            Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

      IOType(final String text) {
         text_ = text;
      }

      @Override
      public String toString() {
         return text_;
      }

      public static String[] toArray() {
         return Arrays.stream(values())
               .map(IOType::toString)
               .toArray(String[]::new);
      }

      public static IOType fromString(final String symbol) {
         return stringToEnum.getOrDefault(symbol, IOType.INPUT);
      }
   }

   public enum OutputChannel {
      NONE_OF_5_TO_8("none of outputs 5-8"),
      OUTPUT_5_ONLY("output 5 only"),
      OUTPUT_6_AND_7("output 6 and 7"),
      OUTPUT_6_ONLY("output 6 only"),
      OUTPUT_7_ONLY("output 7 only"),
      OUTPUT_8_ONLY("output 8 only"),
      OUTPUTS_5_TO_7("outputs 5-7"),
      OUTPUTS_5_TO_8("outputs 5-8");

      private final String text_;

      private static final Map<String, OutputChannel> stringToEnum =
            Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

      OutputChannel(final String text) {
         text_ = text;
      }

      @Override
      public String toString() {
         return text_;
      }

      public static String[] toArray() {
         return Arrays.stream(values())
               .map(OutputChannel::toString)
               .toArray(String[]::new);
      }

      public static OutputChannel fromString(final String symbol) {
         return stringToEnum.getOrDefault(symbol, OutputChannel.NONE_OF_5_TO_8);
      }
   }
}
