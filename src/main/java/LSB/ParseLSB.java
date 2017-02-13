package LSB;

import LittleEndian.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by bozhao on 08/02/2017.
 */
public class ParseLSB {
    private static final int headerSize = 512;
    private static final int subHeaderSize = 32;
    private static final int logSize = 64;

    private static int fileLength;

    private static int xDataStartPosition, xDataEndPosition;

    private static int subFileStartPosition, subFileEndPosition;

    private static int dataFormatCode;

    private static ArrayList xDataList;
    private static ArrayList<SubFile> subFiles;

    private static Header header;
    private static FlagBits flagBits;

    private static int year, month, day, hour, minute;

    public static void toTSV(byte[] bytes, String outputPath, int numberOfDecimals, boolean enablePeakGroup, double peakRangeStart, double peakRangeEnd) {
        fileLength = bytes.length;
        System.out.println("Input file array size: " + fileLength);
        header = new Header(bytes);
        System.out.println("Header process completed.");
        flagBits = new FlagBits(bytes);
        System.out.println("Flagbits process completed.");
        subFileStartPosition = headerSize;
        dataFormatCode = flagBits.getDataFormat();
        getDataX(bytes, flagBits, numberOfDecimals);
        System.out.println("Global X axis process completed.");
        getSubFiles(bytes, header, flagBits, numberOfDecimals, enablePeakGroup, peakRangeStart, peakRangeEnd);
        System.out.println("Sub files process completed.");
        generateTSV(subFiles, header, outputPath, numberOfDecimals, enablePeakGroup);
        System.out.println("File export process completed.");

    }

    private static void getDataX(byte[] bytes, FlagBits flagBits, int numberOfDecimals) {
        switch (flagBits.getDataFormat()) {
            case 1: // has individual x data
                //System.out.println("Will be using individual x data.");
                break;
            case 2: // has global x data
                xDataStartPosition = headerSize;
                xDataEndPosition = headerSize + 4 * header.getFnpts();
                //System.out.println("X data start from: " + xDataStartPosition + " and end at: " + xDataEndPosition);
                xDataList = new ArrayList();
                int readPosition = xDataStartPosition;
                while (readPosition < xDataEndPosition) {
                    //System.out.println(readPosition);
                    xDataList.add(Precision.round(Utils.getFloat(bytes, readPosition, 4), numberOfDecimals));
                    readPosition += 4;
                }
                subFileStartPosition = xDataEndPosition;
                break;
            case 3: // no global or individual x data, generate one
                xDataList = new ArrayList();
                //System.out.println("--------------------");
                //System.out.println("Generating x data:");
                //System.out.println("X data start from: " + header.getFfirst() + " and end at: " + header.getFlast());
                for (double i = header.getFfirst(); i < header.getFlast(); i = i + (header.getFlast() - header.getFfirst()) / (header.getFnpts() - 1)) {
                    //System.out.println(Precision.round(i, 2));
                    xDataList.add(Precision.round(i, numberOfDecimals));
                }
                break;
        }
    }

    private static SubFile getSubFile(byte[] bytes, Header header, FlagBits flagBits, int numberOfDecimals, boolean enablePeakGroup, double peakRangeStart, double peakRangeEnd) {
        SubFile subFile = new SubFile();
        SubFileHeader subFileHeader = new SubFileHeader(bytes);
        subFile.setHeader(subFileHeader);

        int subnpts;
        int subexp;
        int xDataStartPosition;
        int yDataStartPosition = 32;

        if (flagBits.isTxyxys()) {
            subnpts = subFileHeader.getSubnpts();
        } else {
            subnpts = header.getFnpts();
        }

        if (flagBits.isTmulti()) {
            subexp = subFileHeader.getSubexp();
        } else {
            subexp = header.getFexp();
        }

        // TODO may not need this
        if (subexp > 127 || subexp < -128) {
            subexp = 0;
        }

        if (flagBits.isTxyxys()) { // has x data in subfile, get x data
            xDataStartPosition = yDataStartPosition;
            int xDataEndPosition = xDataStartPosition + 4 * subnpts;

            xDataList = new ArrayList();
            int readPosition = xDataStartPosition;
            while (readPosition < xDataEndPosition) {
                int xDataPoint = Utils.getInt(bytes, readPosition, 4);
                double xData = Math.pow(2, subexp - 32) * xDataPoint;
                xDataList.add(Precision.round(xData, numberOfDecimals));
                readPosition += 4;
            }
            subFile.setX(xDataList);
            yDataStartPosition = xDataEndPosition;
        }

        ArrayList yDataList = new ArrayList();
        // FloatY = (2^Exponent)*IntegerY/(2^32)		  -if 32-bit values
        // FloatY = (2^Exponent)*IntegerY/(2^16)		  -if 16-bit values
        if (subexp == -128 || !flagBits.isTsprec()) {
            // 32bit
            int yDataEndPosition = yDataStartPosition + 4 * subnpts;
            int readPosition = yDataStartPosition;
            while (readPosition < yDataEndPosition) {
                int yDataPoint = Utils.getInt(bytes, readPosition, 4);
                int yData = (int) (Math.pow(2, subexp - 32) * yDataPoint);
                yDataList.add(Precision.round(yData, numberOfDecimals));
                readPosition += 4;
            }
            subFile.setY(yDataList);
            if (enablePeakGroup) {
                subFile.setPeakY(groupPeak(yDataList, header, peakRangeStart, peakRangeEnd));
            }
        } else {
            // 16bit
            //System.out.println("16bit mode");
            int yDataEndPosition = yDataStartPosition + 2 * subnpts;
            int readPosition = yDataStartPosition;
            while (readPosition < yDataEndPosition) {
                int yDataPoint = Utils.getInt(bytes, readPosition, 2);
                double yData = Math.pow(2, subexp - 16) * yDataPoint;
                yDataList.add(Precision.round(yData, numberOfDecimals));
                readPosition += 2;
            }
            subFile.setY(yDataList);
            if (enablePeakGroup) {
                subFile.setPeakY(groupPeak(yDataList, header, peakRangeStart, peakRangeEnd));
            }
        }
        return subFile;
    }

    private static void getSubFiles(byte[] bytes, Header header, FlagBits flagBits, int numberOfDecimals, boolean enablePeakGroup, double peakRangeStart, double peakRangeEnd) {
        int fnpts = header.getFnpts();
        int fnsub = header.getFnsub();
        subFiles = new ArrayList<SubFile>();
        if (flagBits.getDataFormat() == 1 && fnpts > 0) {
            for (int i = 0; i < fnpts; i++) {
                int ssfposn = Utils.getInt(bytes, fnpts + i * 12, 4);
                int ssfsize = Utils.getInt(bytes, fnpts + i * 12 + 4, 4);
                float ssftime = Utils.getFloat(bytes, fnpts + i * 12 + 8, 4);
                byte[] subBytes = Arrays.copyOfRange(bytes, ssfposn, ssfposn + ssfsize);
                SubFile subFile = getSubFile(subBytes, header, flagBits, numberOfDecimals, enablePeakGroup, peakRangeStart, peakRangeEnd);
                subFiles.add(subFile);
            }
        } else {
            byte[] subBytes = Arrays.copyOfRange(bytes, subFileStartPosition, fileLength);
            int subnpts;
            int subfsize;
            for (int i = 0; i < fnsub; i++) {
                if (flagBits.isTxyxys()) {
                    SubFileHeader subFileHeader = new SubFileHeader(subBytes);
                    subnpts = subFileHeader.getSubnpts();
                    subfsize = 8 * subnpts + 32;
                } else {
                    subnpts = fnpts;
                    subfsize = 4 * subnpts + 32;
                }
                subFileEndPosition = subFileStartPosition + subfsize;

                byte[] subFileBytes = Arrays.copyOfRange(bytes, subFileStartPosition, subFileEndPosition);
                SubFile subFile = getSubFile(subFileBytes, header, flagBits, numberOfDecimals, enablePeakGroup, peakRangeStart, peakRangeEnd);
                subFiles.add(subFile);
                subFileStartPosition = subFileEndPosition;
            }
        }
    }

    private static void generateTSV(ArrayList<SubFile> subFiles, Header header, String outputPath, int numberOfDecimals, boolean enablePeakGroup) {
        ArrayList csvHeader = new ArrayList();
        if (enablePeakGroup) {
            for (double i = header.getFfirst(); i <= header.getFlast(); i++) {
                csvHeader.add(Precision.round(i, numberOfDecimals));
            }
        } else {
            for (double i = header.getFfirst(); i <= header.getFlast(); ) {
                csvHeader.add(Precision.round(i, numberOfDecimals));
                i = Precision.round(i + ((header.getFlast() - header.getFfirst()) / (header.getFnpts() - 1)), numberOfDecimals);
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("biubiubiu test comment part!").append("\n").append("\n");
        stringBuilder.append(StringUtils.join(csvHeader, "\t"));
        stringBuilder.append("\n");

        for (SubFile subFile : subFiles) {
            if (enablePeakGroup) {
                stringBuilder.append(StringUtils.join(subFile.getPeakY(), "\t"));
            } else {
                stringBuilder.append(StringUtils.join(subFile.getY(), "\t"));
            }
            stringBuilder.append("\n");
        }
        try {
            System.out.println("Preparing output to: " + outputPath);
            PrintWriter printWriter = new PrintWriter(new FileWriter(outputPath));
            printWriter.print(stringBuilder);
            printWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ArrayList<Integer> groupPeak(ArrayList<Float> input, Header header, double peakRangeStart, double peakRangeEnd) {
        ArrayList<Integer> peak = new ArrayList();
        int fnpts = header.getFnpts();
        double rangeStart = header.getFfirst();
        double rangeEnd = header.getFlast();
        double resolution = (rangeEnd - rangeStart) / (fnpts - 1);
        int peakGap = (int) (1 / resolution);
        int dataPointsBeforePeak = (int) Math.round(peakRangeStart / resolution);
        int dataPointsAfterPeak = (int) Math.round(peakRangeEnd / resolution);
        int i = 0;
        while (i <= rangeEnd) {
            int peakCount = 0;
            if (i == 0) {
                int peakPosition = 0;
                for (int m = peakPosition; m <= peakPosition + rangeEnd; m++) {
                    float yDataPoint = input.get(m);
                    peakCount += yDataPoint;
                }
                peak.add(peakCount);
            } else if (i == rangeEnd) {
                int peakPosition = i * peakGap;
                for (int m = peakPosition - dataPointsBeforePeak; m <= peakPosition; m++) {
                    float yDataPoint = input.get(m);
                    peakCount += yDataPoint;
                }
                peak.add(peakCount);
            } else {
                int peakPosition = i * peakGap;
                for (int m = peakPosition - dataPointsBeforePeak; m <= peakPosition + dataPointsAfterPeak; m++) {
                    float yDataPoint = input.get(m);
                    peakCount += yDataPoint;
                }
                peak.add(peakCount);
            }
            i++;
        }
        return peak;
    }

}

class Header {
    private int ftflg;
    private int fversn;
    private int fexper;
    private int fexp;
    private int fnpts;
    private double ffirst;
    private double flast;
    private int fnsub;
    private int fxtype;
    private int fytype;
    private int fztype;
    private int fpost;
    private int fdate;
    private String fres;
    private String fsource;
    private int fpeakpt;
    private String fspare;
    private String fcmnt;
    private String fcatxt;
    private int flogoff;
    private int fmods;
    private int fprocs;
    private int flevel;
    private int fsampin;
    private float ffactor;
    private String fmethod;
    private float fzinc;
    private int fwplanes;
    private float fwinc;
    private int fwtype;
    private String freserv;

    public Header() {
    }

    public Header(byte[] bytes) {
        this.ftflg = Utils.getInt(bytes[0]); //ftflg, flagbits
        this.fversn = Utils.getInt(bytes[1]); // fversn, file format
        this.fexper = Utils.getInt(bytes[2]); //fexper, instrument technical code
        this.fexp = Utils.getInt(bytes[3]); // fexp, fraction scaling exponent integer, range -128 ~ 127
        this.fnpts = Utils.getInt(bytes, 4, 4); // fnpts, interger number of points, or txyxys directory position
        this.ffirst = Utils.getDouble(bytes, 8, 8); // ffirst, floating x coordinate of first point
        this.flast = Utils.getDouble(bytes, 16, 8); // flast, floating x coordinate of last point
        this.fnsub = Utils.getInt(bytes, 24, 4); // fnsub, integer number of subfiles, set to 1 if not tmulti
        this.fxtype = Utils.getInt(bytes, 28, 1); // fxtype, type of X axis units, range 0 ~ 255
        this.fytype = Utils.getInt(bytes, 29, 1); // fytype, type of Y axis units, range 0 ~ 255
        this.fztype = Utils.getInt(bytes, 30, 1); // fztype, type of Z axis units, range 0 ~ 255
        this.fpost = Utils.getInt(bytes, 31, 1); // fpost, posting disposition
        this.fdate = Utils.getInt(bytes, 32, 4); // fdate, date/time
        this.fres = Utils.getString(bytes, 36, 9); // fres, resolution description text
        this.fsource = Utils.getString(bytes, 45, 9); // fsource, source instrument description
        this.fpeakpt = Utils.getInt(bytes, 54, 2); // fpeakpt, peak point number for interferograms, 0=unknown
        this.fspare = Utils.getString(bytes, 56, 32); // fspare, used for array basic storage
        this.fcmnt = Utils.getString(bytes, 88, 130); // fcmnt, ASCII comment string
        this.fcatxt = Utils.getString(bytes, 218, 30); // fcatxt, axis labels for x, y, z if talabs is true in flagbits
        this.flogoff = Utils.getInt(bytes, 248, 4); // flogoff, offset to log block if not 0
        this.fmods = Utils.getInt(bytes, 252, 4); // fmods, spectral modification flags
        this.fprocs = Utils.getInt(bytes[256]); // fprocs, processing code
        this.flevel = Utils.getInt(bytes[257]); // flevel, calibration level plus 1
        this.fsampin = Utils.getInt(bytes, 258, 2); // fsampin, sub-method sample injection number, 1 = first or only
        this.ffactor = Utils.getFloat(bytes, 260, 4); // ffactor, floating data multiplier concentration factor, IEEE-32
        this.fmethod = Utils.getString(bytes, 264, 48); // fmethod, method/program/data filename with extension comma list
        this.fzinc = Utils.getFloat(bytes, 312, 4); // fzinc, Z subfile increment, 0 = use 1st subnext - subfirst
        this.fwplanes = Utils.getInt(bytes, 316, 4); // fwplanes, number of planes for 4D with W dimension, 0 = normal
        this.fwinc = Utils.getFloat(bytes, 320, 4); // fwinc, W plane increment, only if fwplanes not zero
        this.fwtype = Utils.getInt(bytes, 324, 1); // fwtype, type of W axis units, range 0 ~ 255
        this.freserv = Utils.getString(bytes, 325, 187); // freserv, reserved space, must be set to 0
    }

    public Header(int ftflg, int fversn, int fexper, int fexp, int fnpts, double ffirst, double flast, int fnsub, int fxtype, int fytype, int fztype, int fpost, int fdate, String fres, String fsource, int fpeakpt, String fspare, String fcmnt, String fcatxt, int flogoff, int fmods, int fprocs, int flevel, int fsampin, float ffactor, String fmethod, float fzinc, int fwplanes, float fwinc, int fwtype, String freserv) {
        this.ftflg = ftflg;
        this.fversn = fversn;
        this.fexper = fexper;
        this.fexp = fexp;
        this.fnpts = fnpts;
        this.ffirst = ffirst;
        this.flast = flast;
        this.fnsub = fnsub;
        this.fxtype = fxtype;
        this.fytype = fytype;
        this.fztype = fztype;
        this.fpost = fpost;
        this.fdate = fdate;
        this.fres = fres;
        this.fsource = fsource;
        this.fpeakpt = fpeakpt;
        this.fspare = fspare;
        this.fcmnt = fcmnt;
        this.fcatxt = fcatxt;
        this.flogoff = flogoff;
        this.fmods = fmods;
        this.fprocs = fprocs;
        this.flevel = flevel;
        this.fsampin = fsampin;
        this.ffactor = ffactor;
        this.fmethod = fmethod;
        this.fzinc = fzinc;
        this.fwplanes = fwplanes;
        this.fwinc = fwinc;
        this.fwtype = fwtype;
        this.freserv = freserv;
    }

    public int getFtflg() {
        return ftflg;
    }

    public void setFtflg(int ftflg) {
        this.ftflg = ftflg;
    }

    public int getFversn() {
        return fversn;
    }

    public void setFversn(int fversn) {
        this.fversn = fversn;
    }

    public int getFexper() {
        return fexper;
    }

    public void setFexper(int fexper) {
        this.fexper = fexper;
    }

    public int getFexp() {
        return fexp;
    }

    public void setFexp(int fexp) {
        this.fexp = fexp;
    }

    public int getFnpts() {
        return fnpts;
    }

    public void setFnpts(int fnpts) {
        this.fnpts = fnpts;
    }

    public double getFfirst() {
        return ffirst;
    }

    public void setFfirst(double ffirst) {
        this.ffirst = ffirst;
    }

    public double getFlast() {
        return flast;
    }

    public void setFlast(double flast) {
        this.flast = flast;
    }

    public int getFnsub() {
        return fnsub;
    }

    public void setFnsub(int fnsub) {
        this.fnsub = fnsub;
    }

    public int getFxtype() {
        return fxtype;
    }

    public void setFxtype(int fxtype) {
        this.fxtype = fxtype;
    }

    public int getFytype() {
        return fytype;
    }

    public void setFytype(int fytype) {
        this.fytype = fytype;
    }

    public int getFztype() {
        return fztype;
    }

    public void setFztype(int fztype) {
        this.fztype = fztype;
    }

    public int getFpost() {
        return fpost;
    }

    public void setFpost(int fpost) {
        this.fpost = fpost;
    }

    public int getFdate() {
        return fdate;
    }

    public void setFdate(int fdate) {
        this.fdate = fdate;
    }

    public String getFres() {
        return fres;
    }

    public void setFres(String fres) {
        this.fres = fres;
    }

    public String getFsource() {
        return fsource;
    }

    public void setFsource(String fsource) {
        this.fsource = fsource;
    }

    public int getFpeakpt() {
        return fpeakpt;
    }

    public void setFpeakpt(int fpeakpt) {
        this.fpeakpt = fpeakpt;
    }

    public String getFspare() {
        return fspare;
    }

    public void setFspare(String fspare) {
        this.fspare = fspare;
    }

    public String getFcmnt() {
        return fcmnt;
    }

    public void setFcmnt(String fcmnt) {
        this.fcmnt = fcmnt;
    }

    public String getFcatxt() {
        return fcatxt;
    }

    public void setFcatxt(String fcatxt) {
        this.fcatxt = fcatxt;
    }

    public int getFlogoff() {
        return flogoff;
    }

    public void setFlogoff(int flogoff) {
        this.flogoff = flogoff;
    }

    public int getFmods() {
        return fmods;
    }

    public void setFmods(int fmods) {
        this.fmods = fmods;
    }

    public int getFprocs() {
        return fprocs;
    }

    public void setFprocs(int fprocs) {
        this.fprocs = fprocs;
    }

    public int getFlevel() {
        return flevel;
    }

    public void setFlevel(int flevel) {
        this.flevel = flevel;
    }

    public int getFsampin() {
        return fsampin;
    }

    public void setFsampin(int fsampin) {
        this.fsampin = fsampin;
    }

    public float getFfactor() {
        return ffactor;
    }

    public void setFfactor(float ffactor) {
        this.ffactor = ffactor;
    }

    public String getFmethod() {
        return fmethod;
    }

    public void setFmethod(String fmethod) {
        this.fmethod = fmethod;
    }

    public float getFzinc() {
        return fzinc;
    }

    public void setFzinc(float fzinc) {
        this.fzinc = fzinc;
    }

    public int getFwplanes() {
        return fwplanes;
    }

    public void setFwplanes(int fwplanes) {
        this.fwplanes = fwplanes;
    }

    public float getFwinc() {
        return fwinc;
    }

    public void setFwinc(float fwinc) {
        this.fwinc = fwinc;
    }

    public int getFwtype() {
        return fwtype;
    }

    public void setFwtype(int fwtype) {
        this.fwtype = fwtype;
    }

    public String getFreserv() {
        return freserv;
    }

    public void setFreserv(String freserv) {
        this.freserv = freserv;
    }
}

class FlagBits {
    private boolean tsprec; // true for single precision 16bit Y data, false for 32 bit
    private boolean tcgram; // enable fexper in older software, CGM if false
    private boolean tmulti; // true for more than one subfiles
    private boolean trandm; // if tmulti && trandm both true, then arbitrary time Z values
    private boolean tordrd; // if tmulti && tordrd both true, then ordered but uneven subtimes
    private boolean talabs; // true to use fcatxt axis lables instead of fxtype, fytype, fztype
    private boolean txyxys; // if tmulti && txyxys, then each subfile has own X data
    private boolean txvals; // true for floating x value array preceeds Y's

    public FlagBits() {
    }

    public FlagBits(byte[] bytes) {
        boolean[] flags = Utils.getFlagBits(bytes);
        this.tsprec = flags[0];
        this.tcgram = flags[1];
        this.tmulti = flags[2];
        this.trandm = flags[3];
        this.tordrd = flags[4];
        this.talabs = flags[5];
        this.txyxys = flags[6];
        this.txvals = flags[7];
    }

    public FlagBits(boolean tsprec, boolean tcgram, boolean tmulti, boolean trandm, boolean tordrd, boolean talabs, boolean txyxys, boolean txvals) {
        this.tsprec = tsprec;
        this.tcgram = tcgram;
        this.tmulti = tmulti;
        this.trandm = trandm;
        this.tordrd = tordrd;
        this.talabs = talabs;
        this.txyxys = txyxys;
        this.txvals = txvals;
    }

    public int getDataFormat() {
        if (this.txyxys) {
            return 1; // each subfile has its own x data
        } else if (this.txvals) {
            return 2; // has global x data in one subfile
        } else {
            return 3; // no x data found
        }
    }

    public boolean isTsprec() {
        return tsprec;
    }

    public void setTsprec(boolean tsprec) {
        this.tsprec = tsprec;
    }

    public boolean isTcgram() {
        return tcgram;
    }

    public void setTcgram(boolean tcgram) {
        this.tcgram = tcgram;
    }

    public boolean isTmulti() {
        return tmulti;
    }

    public void setTmulti(boolean tmulti) {
        this.tmulti = tmulti;
    }

    public boolean isTrandm() {
        return trandm;
    }

    public void setTrandm(boolean trandm) {
        this.trandm = trandm;
    }

    public boolean isTordrd() {
        return tordrd;
    }

    public void setTordrd(boolean tordrd) {
        this.tordrd = tordrd;
    }

    public boolean isTalabs() {
        return talabs;
    }

    public void setTalabs(boolean talabs) {
        this.talabs = talabs;
    }

    public boolean isTxyxys() {
        return txyxys;
    }

    public void setTxyxys(boolean txyxys) {
        this.txyxys = txyxys;
    }

    public boolean isTxvals() {
        return txvals;
    }

    public void setTxvals(boolean txvals) {
        this.txvals = txvals;
    }
}

class SubFile {
    private SubFileHeader header;
    private ArrayList x;
    private ArrayList y;

    private ArrayList peakX;
    private ArrayList peakY;

    public ArrayList getPeakX() {
        return peakX;
    }

    public void setPeakX(ArrayList peakX) {
        this.peakX = peakX;
    }

    public ArrayList getPeakY() {
        return peakY;
    }

    public void setPeakY(ArrayList peakY) {
        this.peakY = peakY;
    }

    public void printHeader() {
        header.printHeader();
    }

    public void printX() {
        //System.out.println("------------------------------");
        //System.out.println("Subfile X Data:");
        //System.out.println(x);
    }

    public void printY() {
        //System.out.println("------------------------------");
        //System.out.println("Subfile Y Data:");
        //System.out.println(y);
    }

    public void printPeakY() {
        //System.out.println("------------------------------");
        //System.out.println("Subfile Peak Y Data:");
        //System.out.println(peakY);
    }

    public SubFileHeader getHeader() {
        return header;
    }

    public void setHeader(SubFileHeader header) {
        this.header = header;
    }

    public ArrayList getX() {
        return x;
    }

    public void setX(ArrayList x) {
        this.x = x;
    }

    public ArrayList getY() {
        return y;
    }

    public void setY(ArrayList y) {
        this.y = y;
    }
}

class SubFileHeader {
    private int subflgs; // subfile flagbits
    private int subexp; // exponent for sub file Y values
    private int subindx; // Integer index number of trace subfile (0=first)
    private float subtime; // Floating time for trace (Z axis corrdinate)
    private float subnext; // Floating time for next trace (May be same as beg)
    private float subnois; // Floating peak pick noise level if high byte nonzero
    private int subnpts; // Integer number of subfile points for TXYXYS type
    private int subscan; // Integer number of co-added scans or 0 (for collect)
    private float subwlevel; // Floating W axis value (if fwplanes non-zero)
    private String subresv; // Reserved area (must be set to zero)

    public SubFileHeader() {
    }

    public SubFileHeader(byte[] bytes) {
        this.subflgs = Utils.getInt(bytes[0]);
        this.subexp = Utils.getInt(bytes[1]);
        this.subindx = Utils.getInt(bytes, 2, 2);
        this.subtime = Utils.getFloat(bytes, 4, 4);
        this.subnext = Utils.getFloat(bytes, 8, 4);
        this.subnois = Utils.getFloat(bytes, 12, 4);
        this.subnpts = Utils.getInt(bytes, 16, 4);
        this.subscan = Utils.getInt(bytes, 20, 4);
        this.subwlevel = Utils.getInt(bytes, 24, 4);
        this.subresv = Utils.getString(bytes, 28, 4);
    }


    public SubFileHeader(int subflgs, int subexp, int subindx, float subtime, float subnext, float subnois, int subnpts, int subscan, float subwlevel, String subresv) {
        this.subflgs = subflgs;
        this.subexp = subexp;
        this.subindx = subindx;
        this.subtime = subtime;
        this.subnext = subnext;
        this.subnois = subnois;
        this.subnpts = subnpts;
        this.subscan = subscan;
        this.subwlevel = subwlevel;
        this.subresv = subresv;
    }

    public void printHeader() {
        //System.out.println("------------------------------");
        //System.out.println("Subfile Header Information:");
        //System.out.println("subflgs: " + subflgs);
        //System.out.println("subexp: " + subexp);
        //System.out.println("subindx: " + subexp);
        //System.out.println("subtime: " + subtime);
        //System.out.println("subnext: " + subnext);
        //System.out.println("subnois: " + subnois);
        //System.out.println("subnpts: " + subnpts);
        //System.out.println("subscan: " + subscan);
        //System.out.println("subwlevel: " + subwlevel);
        //System.out.println("subresv: " + subresv);
    }

    public int getSubflgs() {
        return subflgs;
    }

    public void setSubflgs(int subflgs) {
        this.subflgs = subflgs;
    }

    public int getSubexp() {
        return subexp;
    }

    public void setSubexp(int subexp) {
        this.subexp = subexp;
    }

    public int getSubindx() {
        return subindx;
    }

    public void setSubindx(int subindx) {
        this.subindx = subindx;
    }

    public float getSubtime() {
        return subtime;
    }

    public void setSubtime(float subtime) {
        this.subtime = subtime;
    }

    public float getSubnext() {
        return subnext;
    }

    public void setSubnext(float subnext) {
        this.subnext = subnext;
    }

    public float getSubnois() {
        return subnois;
    }

    public void setSubnois(float subnois) {
        this.subnois = subnois;
    }

    public int getSubnpts() {
        return subnpts;
    }

    public void setSubnpts(int subnpts) {
        this.subnpts = subnpts;
    }

    public int getSubscan() {
        return subscan;
    }

    public void setSubscan(int subscan) {
        this.subscan = subscan;
    }

    public float getSubwlevel() {
        return subwlevel;
    }

    public void setSubwlevel(float subwlevel) {
        this.subwlevel = subwlevel;
    }

    public String getSubresv() {
        return subresv;
    }

    public void setSubresv(String subresv) {
        this.subresv = subresv;
    }
}

class SubFileFlagBits {
    private boolean subchgd; // true if subfile changed
    private boolean subnopt; // true is peak table file should not be used
    private boolean submodf; // true if subfile modified by arithmetic

    public SubFileFlagBits() {
    }

    public SubFileFlagBits(byte[] bytes) {
        boolean[] flags = Utils.getFlagBits(bytes);
        this.subchgd = flags[0];
        this.subnopt = flags[3];
        this.submodf = flags[7];
    }

    public SubFileFlagBits(boolean subchgd, boolean subnopt, boolean submodf) {
        this.subchgd = subchgd;
        this.subnopt = subnopt;
        this.submodf = submodf;
    }

    public boolean isSubchgd() {
        return subchgd;
    }

    public void setSubchgd(boolean subchgd) {
        this.subchgd = subchgd;
    }

    public boolean isSubnopt() {
        return subnopt;
    }

    public void setSubnopt(boolean subnopt) {
        this.subnopt = subnopt;
    }

    public boolean isSubmodf() {
        return submodf;
    }

    public void setSubmodf(boolean submodf) {
        this.submodf = submodf;
    }
}