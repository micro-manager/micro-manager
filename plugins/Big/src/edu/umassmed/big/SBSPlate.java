package edu.umassmed.big;



public class SBSPlate {
	
	public enum SBSPlateTypes { // currently only 96 well plates has been tested
		SBS_24_WELL (6,4,18000,"24 Microplate"),
		SBS_96_WELL (12,8,9000,"96 Microplate"),
		SBS_384_WELL (24,16,4500,"384 Microplate"),
		SBS_1536_WELL (48,32,2250,"1536 Microplate");
		
		SBSPlateTypes( int X, int Y, int wellSpacing, String Name) {
			this.X = X;
			this.Y = Y;
			this.wellSpacing = wellSpacing;
			this.Name = Name;
		}
		private int X; // 1 indexed
		private int Y; // 1 indexed
		private int wellSpacing;
		private String Name;
		public int getX() { return X; }
		public int getY() { return Y; }
		public int getWellSpacing() { return  wellSpacing; }
		public String getWellPlateName() { return  Name; }
	}
	
	private SBSPlateTypes plateSize = SBSPlateTypes.SBS_96_WELL;
	private int[] firstWell = {0,0}; // 0 indexed
	private int[] lastWell = {11,7}; // 0 indexed
	private int[] currentWell = {0,0};

	
	public SBSPlate () {
		initialize (SBSPlateTypes.SBS_96_WELL);	
	}
	public SBSPlate (SBSPlateTypes platesize) {
		initialize (platesize);
	}
	public SBSPlate (int Size) {
		SBSPlateTypes platesize;
		
		switch (Size) {
		case 24: {
			platesize = SBSPlate.SBSPlateTypes.SBS_24_WELL;;
			break;
		}
		default:
		case 96: {
			platesize = SBSPlate.SBSPlateTypes.SBS_96_WELL;
			break;
		}
		case 384: {
			platesize = SBSPlate.SBSPlateTypes.SBS_384_WELL;
			break;
		}
		case 1536: {
			platesize = SBSPlate.SBSPlateTypes.SBS_1536_WELL;
			break;
		}
		}
		
		initialize (platesize);
	}
	public void initialize (SBSPlateTypes platesize) {
		setPlateType(platesize);
		firstWell[0] = 0;
		firstWell[1] = 0;
		lastWell[0] = this.plateSize.getX() - 1;
		lastWell[1] = this.plateSize.getY() - 1;
	}
	public SBSPlateTypes getPlateType() {
		return this.plateSize;
	}
	public String getWellPlateName() {
		return this.plateSize.getWellPlateName();
	}
	public void setPlateType(SBSPlateTypes platesize) {
		this.plateSize = platesize;		
	}
	public int getWellSpacing() {
		return this.plateSize.getWellSpacing();
	}
	public int[] getFirstWell() {
		// switch to 1 index
		int[]firstWell = { 0, 0 };
		firstWell[0] = this.firstWell[0] + 1;
		firstWell[1] = this.firstWell[1] + 1;
		return firstWell;
	}
	public int[] getLastWell() {
		// switch to 1 index
		int[]lastWell = { 1, 1 };
		lastWell[0] = this.lastWell[0] + 1;
		lastWell[1] = this.lastWell[1] + 1;
		return lastWell;
	}
	public void setFirstWell(int x, int y) {
		if (x > this.plateSize.getX()) x = this.plateSize.getX();
		if (y > this.plateSize.getY()) y = this.plateSize.getY();	
		// switch to 0 indexed
		x--;y--;
		if (x < 0) x = 0;
		if (y < 0) y = 0;
		if (x >= lastWell[0]) x = lastWell[0];
		if (y >= lastWell[1]) y = lastWell[1];
		firstWell[0] = x;
		firstWell[1] = y;
		
		currentWell[0] = x;
		// if odd, we need to move the first well to the bottom
		if (currentWell[0] % 2 == 0 || currentWell[0] == 0) {
			currentWell[1] = y;
		}
		else {
			currentWell[1] = lastWell[1]; // start at the bottom of the column, instead of the top
		}
	}
	public void setFirstWell(int[] firstwell) {
		setFirstWell(firstwell[0],firstwell[1]);
	}
	public void setLastWell(int x, int y) {
		if (x > this.plateSize.getX()) x = this.plateSize.getX();
		if (y > this.plateSize.getY()) y = this.plateSize.getY();
		//switch to 0 index
		x--;y--;
		if (x <= firstWell[0]) x = firstWell[0];
		if (y <= firstWell[1]) y = firstWell[1];
		lastWell[0] = x;
		lastWell[1] = y;
		
		currentWell[0] = firstWell[0];
		currentWell[1] = firstWell[1]; 
	}
	public void setLastWell(int[] lastcell) {
		setLastWell(lastcell[0],lastcell[1]);
	}
	public int[] getWellPosition (int well) {
		int[] position = {0,0};
		
		//position = getPosition (well[0],well[1]); 		
		
		return position;
	}	
	public int[] getWellPosition (int X, int Y) {
		int[] position = {0,0};
		// switch to 0 indexed
		X--;Y--;
		position[0] = X * this.plateSize.getWellSpacing();
		position[1] = Y * this.plateSize.getWellSpacing();
		
		return position;
	}
	public String getWellLabel(int well) {
		String xLabel;
		String yLabel;
		// convert position into X and Y coordinates
		SBSPlateTypes plateType = getPlateType();
		int plateSize = plateType.getX() * plateType.getY();
		if (well > plateSize) {
			return ("Out of Bounds!");
		}
		else if (well < 1) return ("Out of Bounds!");
		else {
			int X = firstWell[0] + (((well - 1) / plateType.getY()) + 1);
			int Y = firstWell[1] + ((well % plateType.getY()));
			if (Y == 0) {
				Y = plateType.getY();
			}
			// reverse labels because we reverse direction on odd rows in reference to starting column, which may not be the first column
			int even = X - firstWell[0];
			if (even % 2 == 0) {
				Y = plateType.getY() - (Y - 1);  
			}
			if (X < 10) xLabel = "0" + Integer.toString(X); 
			else xLabel = Integer.toString(X); 
			
			if (Y <= 26) yLabel = "" + (char)(Y+64);
			else {
				yLabel = "A" + (char)(Y+38); 
			}
		}
		
		return (yLabel + xLabel);
	}
	public int getNumberOfWells() {
		int Positions;
		
		Positions = (1 + this.lastWell[1] - this.firstWell[1])  * (1 + this.lastWell[0] - this.firstWell[0]); 
		
		return Positions;
	}
	public int[] getNextPosition() {
		int[] position = {0,0}; // returns microns position
		
		if (firstWell[0] % 2 == 0 || firstWell[0] == 0) {
			if (currentWell[0] % 2 == 0 || currentWell[0] == 0) {
				currentWell[1]++;
				if (currentWell[1] > lastWell[1]) {
					currentWell[1] = lastWell[1];
					currentWell[0]++;
				}
			}
			else {
				currentWell[1]--;
				if (currentWell[1] < firstWell[1]) {
					//reset Y, increment X
					currentWell[1] = firstWell[1];
					currentWell[0]++;
				}
			}
		}
		else {
			if (currentWell[0] % 2 == 0 || currentWell[0] == 0) {
				currentWell[1]--;
				if (currentWell[1] < firstWell[1]) {
					//reset Y, increment X
					currentWell[1] = firstWell[1];
					currentWell[0]++;
				}
			}
			else {
				currentWell[1]++;
				if (currentWell[1] > lastWell[1]) {
					currentWell[1] = lastWell[1];
					currentWell[0]++;
				}
			}
		}
		if (currentWell[0] > lastWell[0]) {
			currentWell[0] = firstWell[0];
			currentWell[1] = firstWell[1];
		}	
		position[0] = (currentWell[0]) * this.plateSize.getWellSpacing();
		position[1] = (currentWell[1]) * this.plateSize.getWellSpacing();		
		
		return position;
	}
}
