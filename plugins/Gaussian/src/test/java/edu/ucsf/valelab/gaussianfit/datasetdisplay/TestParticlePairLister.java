package edu.ucsf.valelab.gaussianfit.datasetdisplay;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.data.LoadAndSave;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.internal.MMStudio;

/** @author nico */
public class TestParticlePairLister {

  @Test
  public void testPairLister() throws InterruptedException {
    MMStudio studio = new MMStudio(false);
    DataCollectionForm df = DataCollectionForm.getInstance();
    InputStream resource =
        TestParticlePairLister.class.getResourceAsStream(
            "/edu/ucsf/valelab/gaussianfit/testdata/does-not-list-pairs-if-start-not-at-1.txt");
    try {
      LoadAndSave.loadTextFromBufferedReader(
          new BufferedReader(new InputStreamReader(resource, "UTF-8")));
    } catch (UnsupportedEncodingException ex) {
      Assert.fail("UTF-8 encoding is not supported");
    }
    Assert.assertEquals(1, df.getResultsTable().getRowCount());
    ParticlePairLister.Builder pb = new ParticlePairLister.Builder();
    int[] selectedRows = {0};
    ParticlePairLister pp =
        pb.maxDistanceNm(100.0)
            .showPairs(true)
            .rows(selectedRows)
            .p2d(true)
            .p2dSingleFrames(true)
            .build();
    pp.listParticlePairTracks();
    Thread.sleep(5000);
  }
}
