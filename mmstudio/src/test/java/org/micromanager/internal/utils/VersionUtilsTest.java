package org.micromanager.internal.utils;

import org.junit.Assert;
import org.junit.Test;

public class VersionUtilsTest {
  @Test
  public void testVersions() {
    String[] olds = new String[] {"10", "9", "9", "17", "1.2", "1.2.3"};
    String[] news = new String[] {"11.0.0", "10", "10.0.0", "18.1.2", "1.2.3", "1.2.4"};
    for (int i = 0; i < olds.length; ++i) {
      Assert.assertTrue(VersionUtils.isOlderVersion(olds[i], news[i]));
      Assert.assertFalse(VersionUtils.isOlderVersion(news[i], olds[i]));
    }
  }
}
