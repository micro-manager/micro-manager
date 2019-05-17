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

package org.micromanager.plugins.magellan.bdv;

import org.micromanager.plugins.magellan.acq.MultiResMultipageTiffStorage;
import java.io.File;
import java.io.IOException;
import org.micromanager.plugins.magellan.misc.MD;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;

@ImgLoaderIo( format = "micromagellan16bit", type = MagellanImgLoader16Bit.class)
public class XMLIOMagellanImageLoader16Bit implements XmlIoBasicImgLoader<MagellanImgLoader16Bit> {

   @Override
   public org.jdom2.Element toXml(MagellanImgLoader16Bit loader, File basePath) {
      throw new UnsupportedOperationException("not implmented");      
   }

   @Override
   public MagellanImgLoader16Bit fromXml(org.jdom2.Element elmnt, File file, AbstractSequenceDescription<?, ?, ?> asd) {     
      try {
         MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(file.getParent());
         return new MagellanImgLoader16Bit(storage);
      } catch (IOException ex) {
         throw new RuntimeException(ex.getMessage());
      }
   }

}
