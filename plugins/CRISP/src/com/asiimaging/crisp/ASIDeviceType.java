package com.asiimaging.crisp;

public enum ASIDeviceType {
	TIGER("TIGER"), 
	MS2000("MS2000"),
	;
	
	private final String text;
    
	ASIDeviceType(final String text) {
		this.text = text;
	}
    
	@Override
	public String toString() {
		return text;
	}
}
