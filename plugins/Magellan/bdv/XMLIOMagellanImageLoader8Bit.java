///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package bdv;

import acq.MultiResMultipageTiffStorage;
import java.io.File;
import java.io.IOException;
import misc.MD;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;

@ImgLoaderIo( format = "micromagellan8bit", type = MagellanImgLoader8Bit.class)
public class XMLIOMagellanImageLoader8Bit implements XmlIoBasicImgLoader<MagellanImgLoader8Bit> {

   @Override
   public org.jdom2.Element toXml(MagellanImgLoader8Bit loader, File basePath) {
      throw new UnsupportedOperationException("not implmented");      
   }

   @Override
   public MagellanImgLoader8Bit fromXml(org.jdom2.Element elmnt, File file, AbstractSequenceDescription<?, ?, ?> asd) {     
      try {
         MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(file.getParent());
         return new MagellanImgLoader8Bit(storage);
      } catch (IOException ex) {
         throw new RuntimeException(ex.getMessage());
      }
   }

}
