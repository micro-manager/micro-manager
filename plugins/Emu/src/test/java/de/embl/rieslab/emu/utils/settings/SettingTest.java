package de.embl.rieslab.emu.utils.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.utils.settings.BoolSetting;
import de.embl.rieslab.emu.utils.settings.IntSetting;
import de.embl.rieslab.emu.utils.settings.Setting;
import de.embl.rieslab.emu.utils.settings.StringSetting;

public class SettingTest {

  @Test
  public void intSettingTest() {
    final String NAME = "mysetting";
    final String DESC = "description";
    final int defaultval = 3;

    IntSetting mysetting = new IntSetting(NAME, DESC, defaultval);

    // general methods
    assertEquals(NAME, mysetting.getName());
    assertEquals(DESC, mysetting.getDescription());
    assertEquals(defaultval, mysetting.getValue().intValue());
    assertEquals(String.valueOf(defaultval), mysetting.getStringValue());
    assertEquals(String.valueOf(defaultval), mysetting.toString());
    assertEquals(Setting.SettingType.INTEGER, mysetting.getType());
    assertEquals(Setting.SettingType.INTEGER.toString(), mysetting.getType().toString());

    // compatibility
    assertTrue(mysetting.isValueCompatible("1"));
    assertTrue(mysetting.isValueCompatible("-981"));
    assertFalse(mysetting.isValueCompatible("1.7"));
    assertFalse(mysetting.isValueCompatible("trgd"));
    assertFalse(mysetting.isValueCompatible("true"));
    assertFalse(mysetting.isValueCompatible(null));
    assertFalse(mysetting.isValueCompatible(""));

    // conversion
    final int val = -12;
    final String valstr = "-12";
    assertEquals(valstr, mysetting.getStringValue(val));
    assertEquals(new Integer(val), mysetting.getTypedValue(valstr));

    // set and get
    final String val2str = "48";
    final Integer val2 = new Integer(48);
    mysetting.setStringValue(val2str);
    assertEquals(val2, mysetting.getValue());
    assertEquals(val2str, mysetting.getStringValue());

    mysetting.setStringValue("");
    assertEquals(val2, mysetting.getValue());

    mysetting.setStringValue(null);
    assertEquals(val2, mysetting.getValue());

    mysetting.setStringValue("true");
    assertEquals(val2, mysetting.getValue());

    mysetting.setStringValue("1.56");
    assertEquals(val2, mysetting.getValue());
  }

  @Test
  public void boolSettingTest() {
    final String NAME = "mysetting";
    final String DESC = "description";
    final boolean defaultval = false;

    BoolSetting mysetting = new BoolSetting(NAME, DESC, defaultval);

    // general methods
    assertEquals(NAME, mysetting.getName());
    assertEquals(DESC, mysetting.getDescription());
    assertEquals(defaultval, mysetting.getValue().booleanValue());
    assertEquals(String.valueOf(defaultval), mysetting.getStringValue());
    assertEquals(String.valueOf(defaultval), mysetting.toString());
    assertEquals(Setting.SettingType.BOOL, mysetting.getType());
    assertEquals(Setting.SettingType.BOOL.toString(), mysetting.getType().toString());

    // compatibility
    assertTrue(mysetting.isValueCompatible("true"));
    assertTrue(mysetting.isValueCompatible("false"));
    assertFalse(mysetting.isValueCompatible("1.7"));
    assertFalse(mysetting.isValueCompatible("dfsdf"));
    assertFalse(mysetting.isValueCompatible("1"));
    assertFalse(mysetting.isValueCompatible(null));
    assertFalse(mysetting.isValueCompatible(""));

    // conversion
    final boolean val = true;
    final String valstr = "true";
    assertEquals(valstr, mysetting.getStringValue(val));
    assertEquals(new Boolean(val), mysetting.getTypedValue(valstr));

    // set and get
    final String val2str = "true";
    final Boolean val2 = new Boolean(true);
    mysetting.setStringValue(val2str);
    assertEquals(val2, mysetting.getValue());
    assertEquals(val2str, mysetting.getStringValue());

    mysetting.setStringValue("");
    assertEquals(val2, mysetting.getValue());

    mysetting.setStringValue(null);
    assertEquals(val2, mysetting.getValue());

    mysetting.setStringValue("45");
    assertEquals(val2, mysetting.getValue());

    mysetting.setStringValue("1.56");
    assertEquals(val2, mysetting.getValue());
  }

  @Test
  public void stringSettingTest() {
    final String NAME = "mysetting";
    final String DESC = "description";
    final String defaultval = "dfsfsdgf";

    StringSetting mysetting = new StringSetting(NAME, DESC, defaultval);

    // general methods
    assertEquals(NAME, mysetting.getName());
    assertEquals(DESC, mysetting.getDescription());
    assertEquals(defaultval, mysetting.getValue());
    assertEquals(defaultval, mysetting.getStringValue());
    assertEquals(defaultval, mysetting.toString());
    assertEquals(Setting.SettingType.STRING, mysetting.getType());
    assertEquals(Setting.SettingType.STRING.toString(), mysetting.getType().toString());

    // compatibility
    assertTrue(mysetting.isValueCompatible("true"));
    assertTrue(mysetting.isValueCompatible("false"));
    assertTrue(mysetting.isValueCompatible("1.7"));
    assertTrue(mysetting.isValueCompatible("dfsdf"));
    assertTrue(mysetting.isValueCompatible("1"));
    assertFalse(mysetting.isValueCompatible(null));
    assertTrue(mysetting.isValueCompatible(""));

    // conversion
    final String valstr = "esfsfse";
    assertEquals(valstr, mysetting.getStringValue(valstr));
    assertEquals(valstr, mysetting.getTypedValue(valstr));

    // set and get
    final String val2str = "trfdsgrue";
    mysetting.setStringValue(val2str);
    assertEquals(val2str, mysetting.getValue());
    assertEquals(val2str, mysetting.getStringValue());

    mysetting.setStringValue("");
    assertEquals("", mysetting.getValue());

    mysetting.setStringValue(null);
    assertEquals("", mysetting.getValue());

    mysetting.setStringValue("45");
    assertEquals("45", mysetting.getValue());

    mysetting.setStringValue("dfsdfsd");
    assertEquals("dfsdfsd", mysetting.getValue());
  }
}
