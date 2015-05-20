/**
 * 
 * Karl Bellve
 * Biomedical Imaging Group
 * University of Massachusetts Medical School
 * Karl.Bellve@umassmed.edu
 * http://big.umassmed.edu/
 *
 */

package edu.umassmed.big;
import java.util.Vector;


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
		public int getX() { return X; }
		public int getY() { return Y; }
		public int getXY() { return  X * Y; }
		public int getWellSpacing() { return  wellSpacing; }
		public String getWellPlateName() { return  Name; }
		private int X; // 1 indexed
		private int Y; // 1 indexed
		private int wellSpacing;
		private String Name;
	}
	
	class Well{
		private Boolean bSkip = false;
		double[] position;
		private Vector<double[]> wellPositionList = new Vector<double[]>();
		Well(Boolean skip) {
			this.bSkip = skip;
		}
		Well(double X, double Y, double Z) {
			this.bSkip = false;			
			position = new double[3];
			position[0] = X;
			position[1] = Y;
			position[2] = Z;
			this.wellPositionList.add(position);
		}
		void addPosition(double X, double Y,double Z) {
			this.bSkip = false;
			position = new double[3];
			position[0] = X;
			position[1] = Y;
			position[2] = Z;
			this.wellPositionList.add(position);
			
		}
		Boolean skipWell()
		{
			return bSkip;
		}
		void skipWell(Boolean skip)
		{
			bSkip = skip;
		}
	}
	
	private SBSPlateTypes plateSize = SBSPlateTypes.SBS_96_WELL;
	private int[] firstWell = {0,0}; // 0 indexed
	private int[] lastWell = {11,7}; // 0 indexed
	private int[] currentWell = {0,0};
	private Well[][] wellArray;
	private double[] A1Position = {0,0}; // if stage can't be zeroed, then use this an an offset, in microns.
	private Vector<double[]> globalPositionList = new Vector<double[]>();
	
	public SBSPlate () {
		initialize (SBSPlateTypes.SBS_96_WELL, 0, 0);	
	}
	public SBSPlate (SBSPlateTypes platesize) {
		initialize (platesize,0,0);
	}
	public SBSPlate (SBSPlateTypes platesize, double x, double y) {
		initialize (platesize,x,y);
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
		
		initialize (platesize, 0, 0);
	}
	public SBSPlate (int Size, double x, double y) {
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
		
		initialize (platesize, x, y);
	}
	public void initialize (SBSPlateTypes platesize, double x, double y) {
		setPlateType(platesize);
		firstWell[0] = 0;
		firstWell[1] = 0;
		lastWell[0] = this.plateSize.getX() - 1;
		lastWell[1] = this.plateSize.getY() - 1;
		A1Position[0] = x;
		A1Position[1] = y;
		wellArray = new Well[this.plateSize.getX()][this.plateSize.getY()];
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
		int[] firstWell = { 0, 0 };
		firstWell[0] = this.firstWell[0] + 1;
		firstWell[1] = this.firstWell[1] + 1;
		return firstWell;
	}
	public int[] getLastWell() {
		// switch to 1 index
		int[] lastWell = { 1, 1 };
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
	/**
	 * Sets the Absolute position, in microns, of well A1. This needs to be set if the center of well A1 is not at 0,0.
	 * 
	 * @param x micron coordinate the longest dimension of the wellplate at well A1
	 * @param y micron coordinate the shortest dimension of the wellplate at well A1
	 */
	public void setPositionA1(double x, double y) {
		A1Position[0] = x;
		A1Position[1] = y;
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
	
	/**
	 * Adds global offset, values need to be relative to center of well
	 * 
	 * @param X coordinate of the longest dimension of the well plate in microns
	 * @param Y coordinate of the shortest dimension of the well plate in microns
	 * @param Z focus depth in microns
	 */
	public void addPosition (double X, double Y, double Z) {
		double[] position ={0,0,0};
		
		position[0] = X;
		position[1] = Y;
		position[2] = Z;
	
		globalPositionList.add(position);		
	}	
	
	/**
	 * Adds local offset of the current position, values need to be relative to center of well
	 * 
	 * @param X coordinate of the longest dimension of the well plate in microns
	 * @param Y coordinate of the shortest dimension of the well plate in microns
	 * @param Z focus depth in microns
	 * @param well number within the plate, or within region of the plate if first and last wells are set
	 */
	public void addPosition (double X, double Y, double Z, int well) {
		// switch to 0 indexed
		int[] coordinates = {0,0};
		double[] position = {0,0,0};
		
		coordinates = getPlateCoordinates(well); // returns 1 indexed
		// convert to 0 indexed
		coordinates[0]--;
		coordinates[1]--;
		
		position[0] = X;
		position[1] = Y;
		position[2] = Z;
		
		if (wellArray[coordinates[0]][coordinates[1]] == null) {
			wellArray[coordinates[0]][coordinates[1]] = new Well(X,Y,Z);
			}
		else {
			wellArray[coordinates[0]][coordinates[1]].wellPositionList.add(position);		
		}
	}
	/**
	 * Clears all global positions
	 *  
	 */
	public void clearPositions(){
		globalPositionList.clear();
	}
	/** 
	 * Clears all position for a given well if they exist
	 * 
	 * @param well
	 */
	public void clearPositions(int well){
		int[] coordinates = {0,0};
		
		coordinates = getPlateCoordinates(well); // returns 1 indexed
		// convert to 0 indexed
		coordinates[0]--;
		coordinates[1]--;
		
		if (wellArray[coordinates[0]][coordinates[1]] != null) {
			wellArray[coordinates[0]][coordinates[1]].wellPositionList.clear();		
		}
	}
	/**
	 * 
	 * @param skip set to TRUE if you want ignore the current well.
	 */
	public void skipWell(Boolean skip) {
		if (wellArray[currentWell[0]][currentWell[1]] == null){
			wellArray[currentWell[0]][currentWell[1]] = new Well(skip);
		}
		else {
			wellArray[currentWell[0]][currentWell[1]].skipWell(skip);
		}
	}
	/**
	 * 
	 * @param well 
	 * @return returns TRUE if a well should be skipped, or false if it shouldn't be skipped
	 */
	public Boolean skipWell(int well) {
		int[] coordinates = {0,0};
		
		coordinates = getPlateCoordinates(well); // returns 1 indexed
		// convert to 0 indexed
		coordinates[0]--;
		coordinates[1]--;
		
		if (wellArray[coordinates[0]][coordinates[1]] == null){
			// if Well class doesn't exist, then don't skip well
			return false;
		}
		else {
			return (wellArray[coordinates[0]][coordinates[1]].skipWell());
		}
	}
	/**
	 * Returns the SBS label associated with a well
	 * @param well number within the plate, or within region of the plate if first and last wells are set
	 * @return returns String SBS name
	 */
	public String getWellLabel(int well) {
		int[] coordinates = {0,0};
		int X,Y;
		String xLabel;
		String yLabel;
		// convert position into X and Y coordinates
		
		coordinates = getPlateCoordinates(well);
		X = coordinates[0];
		Y = coordinates[1];
		
		if (X < 0 || Y < 0) {
			return ("Out of Bounds!");
		}
				
		if (X < 10) xLabel = "0" + Integer.toString(X); 
		else xLabel = Integer.toString(X); 
		
		if (Y <= 26) yLabel = "" + (char)(Y+64);
		else yLabel = "A" + (char)(Y+38); 

		return (yLabel + xLabel);
	}

	public int getNumberOfWells() {
		int wells;
		
		wells = (1 + this.lastWell[1] - this.firstWell[1])  * (1 + this.lastWell[0] - this.firstWell[0]); 
		
		return wells;
	}
	/**
	 * checks the number of positions of the current well. It will check the position list for the current well and if that exits, return that. 
	 * <p>
	 * Otherwise it will return the number of positions of the global list.
	 * 
	 * @return number of positions within then current well
	 */
	public int getNumberOfWellPositions() {
		
		if (wellArray[currentWell[0]][currentWell[1]] == null){
			// if Well class doesn't exist, then check global list
			return ((globalPositionList.size() > 0) ? globalPositionList.size() : 1);
		}
		else {
			if (wellArray[currentWell[0]][currentWell[1]].wellPositionList.size() > 0) return (wellArray[currentWell[0]][currentWell[1]].wellPositionList.size());
			else return ((globalPositionList.size() > 0) ? globalPositionList.size() : 1);
		}
	}
	/**
	 * checks the number of positions of a given well. It will check the position list for the current well and if that exits, return that. 
	 * <p>
	 * Otherwise it will return the number of positions of the global list.
	 * 
	 * @param well number within the plate, or within region of the plate if first and last wells are set
	 * @return number of positions within a well
	 */
	public int getNumberOfWellPositions(int well) {
		int[] coordinates = {0,0};
		
		coordinates = getPlateCoordinates(well); // returns 1 indexed
		// convert to 0 indexed
		coordinates[0]--;
		coordinates[1]--;
		
			
		if (wellArray[coordinates[0]][coordinates[1]] != null){
			if (wellArray[coordinates[0]][coordinates[1]].wellPositionList.size() > 0) 
				return (wellArray[coordinates[0]][coordinates[1]].wellPositionList.size());
		}	
		
		return ((globalPositionList.size() > 0) ? globalPositionList.size() : 1);
	}
	/**
	 * Returns next well position within the current well. Position is absolute position including plate position. 
	 * 
	 * @param index
	 * @return X,Y,Z in microns
	 */
	public double[] getNextWellPosition(int index) {
		double[] position = {0,0,0}; // returns microns position
		
		position[0] = ((currentWell[0]) * this.plateSize.getWellSpacing())  + A1Position[0];
		position[1] = ((currentWell[1]) * this.plateSize.getWellSpacing())  + A1Position[1];		
		position[2] = 0;
		
		if (index < 0) {
			System.err.println("getNextWellPosition: Well index out of bounds");
			return position;
		}
		
		if (wellArray[currentWell[0]][currentWell[1]] != null) {
			if (index < wellArray[currentWell[0]][currentWell[1]].wellPositionList.size()) {
				position[0] += wellArray[currentWell[0]][currentWell[1]].wellPositionList.get(index)[0];
				position[1] += wellArray[currentWell[0]][currentWell[1]].wellPositionList.get(index)[1];
				position[2] += wellArray[currentWell[0]][currentWell[1]].wellPositionList.get(index)[2];
			} else {
				if (index < globalPositionList.size()) {
					position[0] += globalPositionList.get(index)[0];
					position[1] += globalPositionList.get(index)[1];
					position[2] += globalPositionList.get(index)[2];
				} else System.err.println("getNextWellPosition: Global Well index out of bounds " + globalPositionList.size());
			}
		}
		else {
			if (index < globalPositionList.size()) {
				position[0] += globalPositionList.get(index)[0];
				position[1] += globalPositionList.get(index)[1];
				position[2] += globalPositionList.get(index)[2];
			} else System.err.println("getNextWellPosition: Global Well index out of bounds " + globalPositionList.size());
		}
		
		return (position);
	}
	public double[] getFirstPlatePosition(){
		double[] position = {0,0,0}; // returns microns position

		position = getPlatePosition(firstWell[0],firstWell[1]);
		
		return position;
	}
	/**
	 * Returns the X, and Y coordinates of the center of the current well in microns.
	 * 
	 * @param X coordinate position of the current well in microns
	 * @param Y coordinate position of the current well in microns
	 * @return returns an two double array containing X, Y coordinates of a well in microns
	 */
	public double[] getPlatePosition (int X, int Y) {
		double[] position = {0,0,0}; // X, Y, Z
		// switch to 0 indexed
		X--;Y--;
		position[0] = (X * this.plateSize.getWellSpacing()) + A1Position[0];
		position[1] = (Y * this.plateSize.getWellSpacing()) + A1Position[1];
		position[2] = 0;
		
		
		if (wellArray[X][Y] == null){
			// if Well class doesn't exist, then check global list
			if (globalPositionList.size() >= 1) {
				position[0] += globalPositionList.get(0)[0];
				position[1] += globalPositionList.get(0)[1];
				position[2] += globalPositionList.get(0)[2];
			}
		}
		else {
			if (wellArray[firstWell[0]][firstWell[1]].wellPositionList.size() >= 1) {
				position[0] += wellArray[X][Y].wellPositionList.get(0)[0];
				position[1] += wellArray[X][Y].wellPositionList.get(0)[1];
				position[2] += wellArray[X][Y].wellPositionList.get(0)[2];
			}
		}
		
		return position;
	}
	/**
	 * This function is deprecated. Please use getNextPlatePosition() 
	 * @return
	 */
	public double[] getNextPosition() {
		return (getNextPlatePosition());
	}
	/**
	 * Returns the position, in microns, of the next well, based on position of the last well. 
	 * 
	 * @return either the first position for that well, and if it that does not exist, returns the first global position of that well, which
	 * is usually the center of that well, if not defined.
	 * 
	 */
	public double[] getNextPlatePosition() {
		double[] position = {0,0,0}; // returns microns position
		
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
		position[0] = ((currentWell[0]) * this.plateSize.getWellSpacing())  + A1Position[0];
		position[1] = ((currentWell[1]) * this.plateSize.getWellSpacing())  + A1Position[1];		
		position[2] = 0;
	
		if (wellArray[currentWell[0]][currentWell[1]] == null){
			// if Well class doesn't exist, then check global list
			if (globalPositionList.size() >= 1) {
				position[0] += globalPositionList.get(0)[0];
				position[1] += globalPositionList.get(0)[1];
				position[2] += globalPositionList.get(0)[2];
			}
		}
		else {
			if (wellArray[currentWell[0]][currentWell[1]].wellPositionList.size() >= 1) {
				position[0] += wellArray[currentWell[0]][currentWell[1]].wellPositionList.get(0)[0];
				position[1] += wellArray[currentWell[0]][currentWell[1]].wellPositionList.get(0)[1];
				position[2] += wellArray[currentWell[0]][currentWell[1]].wellPositionList.get(0)[2];
			}
		}
		
		return position;
	}
	/**
	 * Returns the X and Y coordinates of a well
	 * 
	 * @param well number within the plate, or within region of the plate if first and last wells are set
	 * @return 1 indexed coordinates based on well plate or sub region of well plate 
	 */
	public int[] getPlateCoordinates(int well) {
		// out of bounds set to 0,0
		int[] coordinates = {-1,-1}; 
		
		int plateSize = this.plateSize.getXY();
		if (well >= plateSize) {
			return coordinates;
		}
		else if (well < 0) return coordinates;
		else {
			// Well is 0 indexed, while lastWell is 0 indexed
			coordinates[0] = 1 + firstWell[0] + (well/ (lastWell[1] - firstWell[1] + 1));  
			// Well is 0 indexed, while lastWell is 0 indexed
			coordinates[1] = 1 + firstWell[1] + (well% (lastWell[1] - firstWell[1] + 1));
		}
		
		// reverse well position because we reverse direction on odd rows in reference to starting column, which may not be the first column
		int even = coordinates[0]- firstWell[0]; // position is 1 indexed, firstWell is 0 indexed. 
		if (even % 2 == 0) {
			coordinates[1] = (lastWell[1] + 1) - (coordinates[1] - firstWell[1] - 1);
		}
		
		return coordinates;	
	}
}




