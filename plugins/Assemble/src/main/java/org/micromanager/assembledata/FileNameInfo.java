package org.micromanager.assembledata;

import java.util.Objects;
import org.micromanager.assembledata.exceptions.MalFormedFileNameException;

/**
 * @author nico
 *     <p>* Filenames are expected to be formatted as" TIRF-B2-Site_0-7 Where the last number is the
 *     sequence in the total acquistion series, the number before the site number in the well the
 *     letter/number before Site is the well indicator and the text at the beginning the modality
 *     (here: TIRF or Confocal, but can be more or less anything without an underscore
 *     <p>There should be an underscore behind "Site", and dashes separating the other parts
 */
public class FileNameInfo implements Comparable {
  private final String fullName_;
  private final String root_;
  private final String well_;
  private final Integer site_;
  private final Integer sequence_;

  public FileNameInfo(String input) throws MalFormedFileNameException {
    fullName_ = input;
    String[] lSplit = input.split("_");
    if (lSplit.length != 2) {
      throw new MalFormedFileNameException(
          "The DataSetNames must contain exactly one underscore (_)");
    }
    int lI = lSplit[0].lastIndexOf("-");
    int lI2 = lSplit[0].substring(0, lI).lastIndexOf("-");
    root_ = lSplit[0].substring(0, lI2);
    well_ = lSplit[0].substring(lI2 + 1, lI);
    String[] numbers = lSplit[1].split("-");
    if (numbers.length != 2) {
      throw new MalFormedFileNameException(
          "The DataSetNames must contain 2 numbers sepearated by a dash behind the underscore");
    }
    site_ = Integer.parseInt(numbers[0]);
    sequence_ = Integer.parseInt(numbers[1]);
  }

  public String fileName() {
    return fullName_;
  }

  public String root() {
    return root_;
  }

  public String well() {
    return well_;
  }

  public Integer site() {
    return site_;
  }

  public Integer sequence() {
    return sequence_;
  }

  @Override
  public int compareTo(Object o) {
    if (!(o instanceof FileNameInfo)) {
      throw new ClassCastException();
    }
    FileNameInfo other = (FileNameInfo) o;
    if (Objects.equals(other.sequence(), this.sequence_)) {
      return this.root_.compareTo(other.root());
    }
    return other.sequence() > this.sequence_ ? -1 : 1;
  }
}
