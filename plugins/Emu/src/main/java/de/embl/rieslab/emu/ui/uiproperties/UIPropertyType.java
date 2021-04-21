package de.embl.rieslab.emu.ui.uiproperties;

public enum UIPropertyType {
  UIPROPERTY("UIProperty"),
  SINGLESTATE("Single-state"),
  TWOSTATE("Two-state"),
  MULTISTATE("Multi-state"),
  RESCALED("Rescaled"),
  IMMUTMULTISTATE("Immutable multi-state"),
  NONE("Not a UIProperty");

  private String value;

  private UIPropertyType(String value) {
    this.value = value;
  }

  public String getTypeValue() {
    return value;
  }
}
