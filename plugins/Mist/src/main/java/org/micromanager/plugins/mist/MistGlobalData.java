package org.micromanager.plugins.mist;

public class MistGlobalData {
   private final String fileName_;
   private final int siteNr_;
   private final String well_;
   private final int positionX_;
   private final int positionY_;
   private final int rowNr_;
   private final int colNr_;

   public MistGlobalData(String fileName, int siteNr, String well, int positionX, int positionY, int rowNr, int colNr) {
      fileName_ = fileName;
      siteNr_ = siteNr;
      well_ = well;
      positionX_ = positionX;
      positionY_ = positionY;
      rowNr_ = rowNr;
      colNr_ = colNr;
   }

   public String getFileName() {
      return fileName_;
   }

   public int getSiteNr() {
      return siteNr_;
   }

   public String getWell() {
      return well_;
   }

   public int getPositionX() {
      return positionX_;
   }

   public int getPositionY() {
      return positionY_;
   }

   public int getRowNr() {
      return rowNr_;
   }

   public int getColNr() {
      return colNr_;
   }

}
