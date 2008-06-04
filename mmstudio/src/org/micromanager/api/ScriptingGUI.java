package org.micromanager.api;

public interface ScriptingGUI {
   public void displayMessage(String message);
   public void displayError(String text);
   public void displayError(String text, int lineNumber);
}
