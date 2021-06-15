/* Copyright Unidata */
package ucar.nc2.grid;

import com.google.common.base.Preconditions;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.array.Array;
import ucar.array.Arrays;
import ucar.array.Index;
import ucar.nc2.calendar.CalendarDate;
import ucar.nc2.grib.collection.Grib;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.LatLonRect;
import ucar.unidata.geoloc.Projection;
import ucar.unidata.geoloc.ProjectionPoint;
import ucar.unidata.geoloc.ProjectionRect;
import ucar.unidata.util.test.TestDir;
import ucar.unidata.util.test.category.NeedsCdmUnitTest;

import java.lang.invoke.MethodHandles;
import java.util.Formatter;

import static com.google.common.truth.Truth.assertThat;

public class TestGridHorizSubset {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  @Category(NeedsCdmUnitTest.class)
  public void testMSG() throws Exception {
    String filename = TestDir.cdmUnitTestDir + "transforms/Eumetsat.VerticalPerspective.grb";
    System.out.printf("open %s%n", filename);

    Formatter errlog = new Formatter();
    try (GridDataset gds = GridDatasetFactory.openGridDataset(filename, errlog)) {
      assertThat(gds).isNotNull();
      String gribId = "VAR_3-0-8";
      Grid coverage = gds.findGridByAttribute(Grib.VARIABLE_ID_ATTNAME, gribId).orElseThrow(); // "Pixel_scene_type");
      assertThat(coverage).isNotNull();

      GridCoordinateSystem cs = coverage.getCoordinateSystem();
      assertThat(cs).isNotNull();
      GridHorizCoordinateSystem hcs = cs.getHorizCoordSystem();
      assertThat(hcs).isNotNull();

      // bbox = ll: 16.79S 20.5W+ ur: 14.1N 20.09E
      LatLonRect bbox = new LatLonRect(-16.79, -20.5, 14.1, 20.9);

      Projection p = hcs.getProjection();
      ProjectionRect prect = p.latLonToProjBB(bbox); // must override default implementation
      System.out.printf("%s -> %s %n", bbox, prect);

      ProjectionRect expected =
          new ProjectionRect(ProjectionPoint.create(-2129.5688, -1793.0041), 4297.8453, 3308.3885);
      // assert prect.nearlyEquals(expected);
      assertThat(prect).isEqualTo(expected);

      LatLonRect bb2 = p.projToLatLonBB(prect);
      System.out.printf("%s -> %s %n", prect, bb2);
      GridReferencedArray geo = coverage.getReader().setLatLonBoundingBox(bbox).read();

      int[] expectedShape = new int[] {363, 479};
      assertThat(geo.csSubset().getHorizCoordSystem().getShape()).isEqualTo(expectedShape);
    }
  }

  @Test
  @Category(NeedsCdmUnitTest.class)
  public void testLatLonSubset() throws Exception {
    String filename = TestDir.cdmUnitTestDir + "conventions/problem/SUPER-NATIONAL_latlon_IR_20070222_1600.nc";
    System.out.printf("open %s%n", filename);

    Formatter errlog = new Formatter();
    try (GridDataset gds = GridDatasetFactory.openGridDataset(filename, errlog)) {
      assertThat(gds).isNotNull();
      String gribId = "micron11";
      Grid coverage = gds.findGrid(gribId).orElseThrow();
      assertThat(coverage).isNotNull();

      GridCoordinateSystem cs = coverage.getCoordinateSystem();
      assertThat(cs).isNotNull();
      GridHorizCoordinateSystem hcs = cs.getHorizCoordSystem();
      assertThat(hcs).isNotNull();

      LatLonRect bbox = new LatLonRect.Builder(LatLonPoint.create(40.0, -100.0), 10.0, 20.0).build();
      checkLatLonSubset(hcs, coverage, bbox, new int[] {141, 281});

      bbox = new LatLonRect.Builder(LatLonPoint.create(-40.0, -180.0), 120.0, 300.0).build();
      checkLatLonSubset(hcs, coverage, bbox, new int[] {800, 1300});
    }
  }

  // longitude subsetting (CoordAxis1D regular) }
  @Test
  @Category(NeedsCdmUnitTest.class)
  public void testLongitudeSubset() throws Exception {
    String filename = TestDir.cdmUnitTestDir + "tds/ncep/GFS_Global_onedeg_20100913_0000.grib2";
    System.out.printf("open %s%n", filename);

    Formatter errlog = new Formatter();
    try (GridDataset gds = GridDatasetFactory.openGridDataset(filename, errlog)) {
      assertThat(gds).isNotNull();

      String gribId = "VAR_0-3-0_L1";
      Grid coverage = gds.findGridByAttribute(Grib.VARIABLE_ID_ATTNAME, gribId).orElseThrow(); // "Pressure_Surface");
      assertThat(coverage).isNotNull();

      GridCoordinateSystem cs = coverage.getCoordinateSystem();
      assertThat(cs).isNotNull();
      GridHorizCoordinateSystem hcs = cs.getHorizCoordSystem();
      assertThat(hcs).isNotNull();

      LatLonRect bbox = new LatLonRect.Builder(LatLonPoint.create(40.0, -100.0), 10.0, 20.0).build();
      checkLatLonSubset(hcs, coverage, bbox, new int[] {1, 11, 21});
    }
  }

  @Test
  @Category(NeedsCdmUnitTest.class)
  public void testCrossLongitudeSeam() throws Exception {
    String filename = TestDir.cdmUnitTestDir + "tds/ncep/GFS_Global_0p5deg_20100913_0000.grib2";
    System.out.printf("open %s%n", filename);

    Formatter errlog = new Formatter();
    try (GridDataset gds = GridDatasetFactory.openGridDataset(filename, errlog)) {
      assertThat(gds).isNotNull();

      String gribId = "VAR_2-0-0_L1";
      Grid coverage = gds.findGridByAttribute(Grib.VARIABLE_ID_ATTNAME, gribId).orElseThrow(); // "Land_cover_0__sea_1__land_surface");
      assertThat(coverage).isNotNull();

      GridCoordinateSystem cs = coverage.getCoordinateSystem();
      assertThat(cs).isNotNull();
      GridHorizCoordinateSystem hcs = cs.getHorizCoordSystem();
      assertThat(hcs).isNotNull();

      LatLonRect bbox = LatLonRect.builder(40.0, -100.0, 10.0, 120.0).build();
      checkLatLonSubset(hcs, coverage, bbox, new int[] {1, 61, 441});
    }
  }

  @Test
  @Category(NeedsCdmUnitTest.class)
  public void testLongitudeSubsetWithHorizontalStride() throws Exception {
    String filename = TestDir.cdmUnitTestDir + "tds/ncep/GFS_Global_onedeg_20100913_0000.grib2";
    System.out.printf("open %s%n", filename);

    Formatter errlog = new Formatter();
    try (GridDataset gds = GridDatasetFactory.openGridDataset(filename, errlog)) {
      assertThat(gds).isNotNull();

      String gribId = "VAR_0-3-0_L1";
      Grid coverage = gds.findGridByAttribute(Grib.VARIABLE_ID_ATTNAME, gribId).orElseThrow();
      assertThat(coverage).isNotNull();

      GridCoordinateSystem cs = coverage.getCoordinateSystem();
      assertThat(cs).isNotNull();
      GridHorizCoordinateSystem origHcs = cs.getHorizCoordSystem();
      assertThat(origHcs).isNotNull();

      // Next, create the subset param and make the request
      final CalendarDate validTime = CalendarDate.fromUdunitIsoDate(null, "2010-09-21T00:00:00Z").orElseThrow();
      // subset across the seam
      final LatLonRect subsetLatLonRequest = new LatLonRect.Builder(LatLonPoint.create(-15, -10), 30, 20).build();
      final int stride = 2;

      // make subset
      GridReferencedArray geoArray = coverage.getReader().setTime(validTime).setLatLonBoundingBox(subsetLatLonRequest)
          .setHorizStride(stride).read();

      // Check that TimeAxis is 1D, has one coordinate, and it's equal to the time we requested
      GridCoordinateSystem dataCS = geoArray.csSubset();
      assertThat(dataCS).isNotNull();
      GridAxis1DTime timeAxis1d = dataCS.getTimeAxis();
      assertThat(timeAxis1d).isNotNull();
      assertThat(timeAxis1d.getNcoords()).isEqualTo(1);
      assertThat(timeAxis1d.getCalendarDates().get(0)).isEqualTo(validTime);

      // make sure the bounding box requested by subset is contained within the
      // horizontal coordinate system of the GeoReferencedArray produced by the subset
      GridHorizCoordinateSystem subsetHcs = dataCS.getHorizCoordSystem();
      assertThat(subsetLatLonRequest.containedIn(subsetHcs.getLatLonBoundingBox())).isTrue();

      // make sure resolution of the lat and lon grids of the subset take into account the stride
      // by comparing the resolution
      GridAxis1D origLonAxis = Preconditions.checkNotNull(origHcs.getXHorizAxis());
      GridAxis1D origLatAxis = Preconditions.checkNotNull(origHcs.getYHorizAxis());
      GridAxis1D subsetLonAxis = Preconditions.checkNotNull(subsetHcs.getXHorizAxis());
      GridAxis1D subsetLatAxis = Preconditions.checkNotNull(subsetHcs.getYHorizAxis());
      final double tol = 0.001;
      assertThat(origLonAxis.getResolution()).isNotWithin(tol).of(subsetLonAxis.getResolution());
      assertThat(origLonAxis.getResolution()).isWithin(tol).of(subsetLonAxis.getResolution() / stride);
      assertThat(origLatAxis.getResolution()).isNotWithin(tol).of(subsetLatAxis.getResolution());
      assertThat(origLatAxis.getResolution()).isWithin(tol).of(subsetLatAxis.getResolution() / stride);

      // check to make sure we get data from both sides of the seam by testing that half of the array isn't empty.
      // slice along longitude in the middle of the array.
      Array<Number> geoData = geoArray.data();
      int middle = geoData.getShape()[1] / 2;
      Array<Number> dataSlice = Arrays.slice(geoData, 2, middle);
      // flip the array
      Array<Number> dataFlip = Arrays.flip(dataSlice, 0);
      Index sliceIndex = dataSlice.getIndex();
      Index flipIndex = dataFlip.getIndex();

      final double initialSumVal = 0;
      int numValsToSum = 3;
      double sumData = initialSumVal;
      double sumDataFlip = initialSumVal;
      for (int i = 0; i < numValsToSum - 1; i++) {
        double val = dataSlice.get(sliceIndex.set(i)).doubleValue();
        double valFlip = dataFlip.get(flipIndex.set(i)).doubleValue();
        // only sum if not missing
        if (!coverage.isMissing(val))
          sumData += val;
        if (!coverage.isMissing(valFlip))
          sumDataFlip += valFlip;
      }
      assertThat(sumData).isNotEqualTo(initialSumVal);
      assertThat(sumDataFlip).isNotEqualTo(initialSumVal);
    }
  }

  private void checkLatLonSubset(GridHorizCoordinateSystem hcs, Grid coverage, LatLonRect bbox, int[] expectedShape)
      throws Exception {
    System.out.printf(" coverage llbb = %s width=%f%n", hcs.getLatLonBoundingBox().toString2(),
        hcs.getLatLonBoundingBox().getWidth());
    System.out.printf(" constrain bbox= %s width=%f%n", bbox.toString2(), bbox.getWidth());

    GridReferencedArray geoArray = coverage.getReader().setLatLonBoundingBox(bbox).setTimePresent().read();
    GridCoordinateSystem gcs2 = geoArray.csSubset();
    assertThat(gcs2).isNotNull();
    GridHorizCoordinateSystem hcs2 = gcs2.getHorizCoordSystem();
    assertThat(hcs2).isNotNull();
    System.out.printf(" data cs shape=%s%n", java.util.Arrays.toString(hcs2.getShape()));
    System.out.printf(" data shape=%s%n", java.util.Arrays.toString(geoArray.data().getShape()));

    assertThat(hcs2.getShape()).isEqualTo(expectedShape);
    assertThat(geoArray.data().getShape()).isEqualTo(expectedShape);
  }

}
