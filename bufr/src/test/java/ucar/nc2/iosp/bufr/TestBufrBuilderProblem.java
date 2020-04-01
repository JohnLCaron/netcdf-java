package ucar.nc2.iosp.bufr;

import static org.junit.Assert.fail;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Formatter;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.util.CompareNetcdf2;
import ucar.unidata.util.test.TestDir;
import ucar.unidata.util.test.category.NeedsCdmUnitTest;

@Category(NeedsCdmUnitTest.class)
public class TestBufrBuilderProblem {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void problem() throws Exception {
    String filename =
        TestDir.cdmUnitTestDir + "/formats/bufr/userExamples/US058MCUS-BUFtdp.SPOUT_00011_buoy_20091101021700.bufr";
    // showOrg(filename);
    // showNew(filename);
    compareWithBuilder(filename);
  }

  private void compareWithBuilder(String filename) throws IOException {
    logger.info("TestBuilders on {}%n", filename);
    try (NetcdfFile org = NetcdfFiles.open(filename)) {
      try (NetcdfFile withBuilder = NetcdfFiles.open(filename)) {
        Formatter f = new Formatter();
        CompareNetcdf2 compare = new CompareNetcdf2(f, false, false, true);
        if (!compare.compare(org, withBuilder, null)) {
          System.out.printf("Compare %s%n%s%n", filename, f);
          fail();
        }
      }
    }
  }

  private void showOrg(String filename) throws IOException {

    try (NetcdfFile org = NetcdfFiles.open(filename)) {
      // Variable v = org.findVariable("catchments_part_node_count");
      // Array data = v.read();
      System.out.printf("org = %s%n", org);
    }
  }

  private void showNew(String filename) throws IOException {

    try (NetcdfFile withBuilder = NetcdfFiles.open(filename)) {
      // Variable v = withBuilder.findVariable("catchments_x");
      // Array data = v.read();
      System.out.printf("withBuilder = %s%n", withBuilder);
    }
  }
}

