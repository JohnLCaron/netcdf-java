

/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.grib.grib1.tables;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.constants.CDM;
import ucar.nc2.grib.coord.VertCoordType;
import ucar.unidata.util.StringUtil2;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.*;

/**
 * Read NCEP html files to extract the GRIB tables.
 *
 * @author caron
 * @since 11/21/11
 */
public class NcepHtmlScraper {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final boolean debug = false;
  private static final boolean show = false;

  private static final String dirOut = "build/tables/grib1/";

  //////////////////////////////////////////////////////////////////
  // https://www.nco.ncep.noaa.gov/pmb/docs/on388/tablea.html
  // LOOK the table is hand edited to add the units (!)
  void parseTable3() throws IOException {
    String url = "https://www.nco.ncep.noaa.gov/pmb/docs/on388/table3.html";
    Document doc = Jsoup.parse(new URL(url), 5 * 1000); // 5 sec timeout
    System.out.printf("%s%n", doc);

    Set<String> abbrevSet = new HashSet<>();
    Element table = doc.select("table").get(2);
    List<VertCoordType> stuff = new ArrayList<>();
    Elements rows = table.select("tr");
    for (Element row : rows) {
      Elements cols = row.select("td");
      if (debug) {
        System.out.printf(" %d=", cols.size());
        for (Element col : cols)
          System.out.printf("%s:", col.text());
        System.out.printf("%n");
      }

      if (cols.size() == 3) {
        String snum = StringUtil2.cleanup(cols.get(0).text()).trim();
        try {
          int pnum = Integer.parseInt(snum);
          String desc = StringUtil2.cleanup(cols.get(1).text()).trim();
          String abbrev = StringUtil2.cleanup(cols.get(2).text()).trim();
          if (desc.startsWith("Reserved")) {
            System.out.printf("*** Skip Reserved %s%n", row.text());
            continue;
          } else {
            System.out.printf("%d == %s, %s%n", pnum, desc, abbrev);
            if (abbrevSet.contains(abbrev))
              System.out.printf("DUPLICATE ABBREV %s%n", abbrev);
            else
              stuff.add(new VertCoordType(pnum, desc, abbrev, null, null, false, false));
          }
          // result.add(new Param(pnum, desc, cols.get(2).text(), cols.get(3).text()));
        } catch (NumberFormatException e) {
          System.out.printf("*** Cant parse %s == %s%n", snum, row.text());
        }

      }
    }
    writeTable3Xml("NCEP GRIB-1 Table 3", url, "ncepTable3.xml", stuff);
  }

  private void writeTable3Xml(String name, String source, String filename, List<VertCoordType> stuff)
      throws IOException {
    org.jdom2.Element rootElem = new org.jdom2.Element("table3");
    org.jdom2.Document doc = new org.jdom2.Document(rootElem);
    rootElem.addContent(new org.jdom2.Element("title").setText(name));
    rootElem.addContent(new org.jdom2.Element("source").setText(source));

    for (VertCoordType p : stuff) {
      org.jdom2.Element paramElem = new org.jdom2.Element("parameter");
      paramElem.setAttribute("code", Integer.toString(p.getCode()));
      paramElem.addContent(new org.jdom2.Element("description").setText(p.getDesc()));
      paramElem.addContent(new org.jdom2.Element("abbrev").setText(p.getAbbrev()));
      String units = p.getUnits();
      if (units == null)
        units = handcodedUnits(p.getCode());
      if (units != null)
        paramElem.addContent(new org.jdom2.Element("units").setText(units));
      if (p.getDatum() != null)
        paramElem.addContent(new org.jdom2.Element("datum").setText(p.getDatum()));
      if (p.isPositiveUp())
        paramElem.addContent(new org.jdom2.Element("isPositiveUp").setText("true"));
      if (p.isLayer())
        paramElem.addContent(new org.jdom2.Element("isLayer").setText("true"));
      rootElem.addContent(paramElem);
    }

    XMLOutputter fmt = new XMLOutputter(Format.getPrettyFormat());
    String x = fmt.outputString(doc);

    try (FileOutputStream fout = new FileOutputStream(dirOut + filename)) {
      fout.write(x.getBytes(StandardCharsets.UTF_8));
    }

    if (show)
      System.out.printf("%s%n", x);
  }

  private String handcodedUnits(int code) {
    switch (code) {
      case 216:
      case 217:
      case 237:
      case 238:
        return "m";
      case 235:
        return ".1 degC";
    }
    return null;
  }

  /*
   * Grib1LevelTypeTable will go away soon
   * void writeWmoTable3() throws IOException {
   * 
   * Set<String> abbrevSet = new HashSet<String>();
   * List<Grib1Tables.LevelType> stuff = new ArrayList<Grib1Tables.LevelType>();
   * for (int code = 1; code < 255; code++) {
   * String desc = Grib1LevelTypeTable.getLevelDescription(code);
   * if (desc.startsWith("Unknown")) continue;
   * String abbrev = Grib1LevelTypeTable.getNameShort(code);
   * String units = Grib1LevelTypeTable.getUnits(code);
   * String datum = Grib1LevelTypeTable.getDatum(code);
   * 
   * if (abbrevSet.contains(abbrev))
   * System.out.printf("DUPLICATE ABBREV %s%n", abbrev);
   * 
   * Grib1Tables.LevelType level = new Grib1Tables.LevelType(code, desc, abbrev, units, datum);
   * level.isLayer = Grib1LevelTypeTable.isLayer(code);
   * level.isPositiveUp = Grib1LevelTypeTable.isPositiveUp(code);
   * stuff.add(level);
   * }
   * 
   * writeTable3Xml("WMO GRIB-1 Table 3", "Unidata transcribe WMO306_Vol_I.2_2010_en.pdf", "wmoTable3.xml", stuff);
   * }
   */

  //////////////////////////////////////////////////////////////////
  // https://www.nco.ncep.noaa.gov/pmb/docs/on388/tablea.html

  void parseTableA() throws IOException {
    String source = "https://www.nco.ncep.noaa.gov/pmb/docs/on388/tablea.html";

    Document doc = Jsoup.parse(new URL(source), 10 * 1000);

    Element table = doc.select("table").first();
    if (table == null)
      return;
    List<Stuff> stuff = new ArrayList<>();
    Elements rows = table.select("tr");
    for (Element row : rows) {
      Elements cols = row.select("td");
      if (debug) {
        System.out.printf(" %d=", cols.size());
        for (Element col : cols)
          System.out.printf("%s:", col.text());
        System.out.printf("%n");
      }

      if (cols.size() == 2) {
        String snum = StringUtil2.cleanup(cols.get(0).text()).trim();
        try {
          int pnum = Integer.parseInt(snum);
          String desc = StringUtil2.cleanup(cols.get(1).text()).trim();
          if (desc.startsWith("Reserved")) {
            System.out.printf("*** Skip Reserved %s%n", row.text());
          } else {
            System.out.printf("%d == %s%n", pnum, desc);
            stuff.add(new Stuff(pnum, desc));
          }
          // result.add(new Param(pnum, desc, cols.get(2).text(), cols.get(3).text()));
        } catch (NumberFormatException e) {
          System.out.printf("*** Cant parse %s == %s%n", snum, row.text());
        }

      }
    }
    writeTableAXml("NCEP GRIB-1 Table A", source, "ncepTableA.xml", stuff);
  }

  private static class Stuff {
    int no;
    String desc;

    private Stuff(int no, String desc) {
      this.no = no;
      this.desc = desc;
    }
  }

  private void writeTableAXml(String name, String source, String filename, List<Stuff> stuff) throws IOException {
    org.jdom2.Element rootElem = new org.jdom2.Element("tableA");
    org.jdom2.Document doc = new org.jdom2.Document(rootElem);
    rootElem.addContent(new org.jdom2.Element("title").setText(name));
    rootElem.addContent(new org.jdom2.Element("source").setText(source));

    for (Stuff p : stuff) {
      org.jdom2.Element paramElem = new org.jdom2.Element("parameter");
      paramElem.setAttribute("code", Integer.toString(p.no));
      paramElem.addContent(new org.jdom2.Element("description").setText(p.desc));
      rootElem.addContent(paramElem);
    }

    XMLOutputter fmt = new XMLOutputter(Format.getPrettyFormat());
    String x = fmt.outputString(doc);

    try (FileOutputStream fout = new FileOutputStream(dirOut + filename)) {
      fout.write(x.getBytes(StandardCharsets.UTF_8));
    }

    if (show)
      System.out.printf("%s%n", x);
  }



  //////////////////////////////////////////////////////////////////
  private int[] tableVersions = new int[] {2, 0, 128, 129, 130, 131, 133, 140, 0, 0, 141};

  void parseTable2() throws IOException {
    String source = "https://www.nco.ncep.noaa.gov/pmb/docs/on388/table2.html";

    Document doc = Jsoup.parse(new URL(source), 10 * 1000);

    int count = 0;
    for (Element e : doc.select("big"))
      System.out.printf("%d == %s%n=%n", count++, e.text());

    Element body = doc.select("body").first();
    if (body == null)
      return;

    Elements tables = body.select("table");
    for (int i = 0; i < tableVersions.length; i++) {
      if (tableVersions[i] == 0)
        continue;
      Element table = tables.select("table").get(i);
      List<Param> params = readTable2(table);

      String name = "NCEP Table Version " + tableVersions[i];
      String filename = "ncepGrib1-" + tableVersions[i];
      writeTable2Wgrib(name, source, filename + ".tab", params);
      writeTable2Xml(name, source, filename + ".xml", params);
    }
  }

  private List<Param> readTable2(Element table) {
    List<Param> result = new ArrayList<>();
    Elements rows = table.select("tr");
    for (Element row : rows) {
      Elements cols = row.select("td");
      if (debug) {
        System.out.printf(" %d=", cols.size());
        for (Element col : cols)
          System.out.printf("%s:", col.text());
        System.out.printf("%n");
      }

      if (cols.size() == 4) {
        String snum = StringUtil2.cleanup(cols.get(0).text()).trim();
        try {
          int pnum = Integer.parseInt(snum);
          String desc = StringUtil2.cleanup(cols.get(1).text()).trim();
          if (desc.startsWith("Reserved")) {
            System.out.printf("*** Skip Reserved %s%n", row.text());
            continue;
          }
          result.add(new Param(pnum, desc, cols.get(2).text(), cols.get(3).text()));
        } catch (NumberFormatException e) {
          System.out.printf("*** Cant parse %s == %s%n", snum, row.text());
        }

      }

    }
    return result;
  }

  private static class Param {
    int pnum;
    String desc, unit, name;

    private Param(int pnum, String desc, String unit, String name) {
      this.pnum = pnum;
      this.desc = desc;
      this.unit = StringUtil2.cleanup(unit);
      this.name = StringUtil2.cleanup(name);
    }
  }

  /////////////////////////////////////////////////////////

  private void writeTable2Xml(String name, String source, String filename, List<Param> params) throws IOException {
    org.jdom2.Element rootElem = new org.jdom2.Element("parameterMap");
    org.jdom2.Document doc = new org.jdom2.Document(rootElem);
    rootElem.addContent(new org.jdom2.Element("title").setText(name));
    rootElem.addContent(new org.jdom2.Element("source").setText(source));

    for (Param p : params) {
      org.jdom2.Element paramElem = new org.jdom2.Element("parameter");
      paramElem.setAttribute("code", Integer.toString(p.pnum));
      paramElem.addContent(new org.jdom2.Element("shortName").setText(p.name));
      paramElem.addContent(new org.jdom2.Element("description").setText(p.desc));
      paramElem.addContent(new org.jdom2.Element("units").setText(p.unit));
      rootElem.addContent(paramElem);
    }

    XMLOutputter fmt = new XMLOutputter(Format.getPrettyFormat());
    String x = fmt.outputString(doc);

    try (FileOutputStream fout = new FileOutputStream(dirOut + filename)) {
      fout.write(x.getBytes(StandardCharsets.UTF_8));
    }

    if (show)
      System.out.printf("%s%n", x);
  }

  private void writeTable2Wgrib(String name, String source, String filename, List<Param> params) throws IOException {
    Formatter f = new Formatter();
    f.format("# %s%n", name);
    f.format("# %s%n", source);
    for (Param p : params)
      f.format("%3d:%s:%s [%s]%n", p.pnum, p.name, p.desc, p.unit); // 1:PRES:Pressure [Pa]

    try (FileOutputStream fout = new FileOutputStream(dirOut + filename)) {
      fout.write(f.toString().getBytes(StandardCharsets.UTF_8));
    }

    if (show)
      System.out.printf("%s%n", f);
  }

  public static void main(String[] args) throws IOException {
    Files.createDirectories(Paths.get(dirOut));
    NcepHtmlScraper scraper = new NcepHtmlScraper();
    scraper.parseTable2();
    scraper.parseTableA();
    scraper.parseTable3();
  }
}
