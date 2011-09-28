package org.micromanager.utils;

public class AcqOrderMode {

	public static final int TIME_POS_CHANNEL_SLICE = 0;
	public static final int TIME_POS_SLICE_CHANNEL = 1;
	public static final int POS_TIME_CHANNEL_SLICE = 2;
	public static final int POS_TIME_SLICE_CHANNEL = 3;

	private int id_;
	private boolean timeEnabled_;
	private boolean posEnabled_;
	private boolean sliceEnabled_;
	private boolean channelEnabled_;

	public AcqOrderMode(int id) {
		id_ = id;
		timeEnabled_ = true;
		posEnabled_ = true;
		sliceEnabled_ = true;
		channelEnabled_ = true;
	}

	@Override
	public String toString() {
		StringBuffer name = new StringBuffer();
		if ( timeEnabled_ && posEnabled_ ) 
			if (id_ == TIME_POS_CHANNEL_SLICE || id_ == TIME_POS_SLICE_CHANNEL)
				name.append("Time, Position");
			else
				name.append("Position, Time");
		else if ( timeEnabled_ ) 
			name.append("Time");
		else if ( posEnabled_ ) 
			name.append("Position");

		if ( (timeEnabled_ || posEnabled_) && ( channelEnabled_ || sliceEnabled_ )  )
			name.append(", ");
		
		if ( channelEnabled_ && sliceEnabled_ ) 
			if (id_ == TIME_POS_CHANNEL_SLICE || id_ == POS_TIME_CHANNEL_SLICE)
				name.append("Channel, Slice");
			else
				name.append("Slice, Channel");
		else if ( channelEnabled_ ) 
			name.append("Channel");
		else if ( sliceEnabled_ ) 
			name.append("Slice");

		return name.toString();
	}  

	public void setEnabled(boolean time, boolean position, boolean slice, boolean channel) {
		timeEnabled_ = time;
		posEnabled_ = position;
		sliceEnabled_ = slice;
		channelEnabled_ = channel;
	}
	
	public int getID() {
		return id_;
	}
}