import LSB.ParseLSB;
import LittleEndian.Utils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;

/**
 * Created by bozhao on 08/02/2017.
 */
public class Reader {
    private static String pathInput;
    private static String pathOutput;
    private static int numberOfDecimals;

    private static boolean enablePeakGroup;
    private static double peakRangeStart;
    private static double peakRangeEnd;

    private static byte[] bytes;

    public static void main(String[] args) throws Exception {

        final CommandLineParser parser = new DefaultParser();
        final Options options = new Options();
        options.addOption("i", "input", true, "specify input file path");
        options.addOption("o", "output", true, "[optional] specify output file path, output to same folder as input if not set");
        options.addOption("d", "decimals", true, "[optional] specify number of decimals, default 2");
        options.addOption("p", "peak", true, "[optional] enable peak group, default false");
        options.addOption("s", "pstart", true, "[required if -p is set to true] peak group range start value");
        options.addOption("e", "pend", true, "[required if -p is set to true] peak group range end value");
        options.addOption("h", "help", false, "list all command line options");

        CommandLine commandLine = parser.parse(options, args);

        if (commandLine.hasOption("h")) {
            System.out.println("Options:");
            System.out.println("-h --help\tlist all command line options");
            System.out.println("-i --input\t[required] specify input file path");
            System.out.println("-o --output\t[optional] specify output file path, output to same folder as input if not set");
            System.out.println("-d --decimals\t[optional] specify number of decimals, default 2");
            System.out.println("-p --peak\t[optional] enable peak group, default false");
            System.out.println("-s --start\t[required if -p is set to true] peak group range start value");
            System.out.println("-e --end\t[required if -p is set to true] peak group range end value");
            System.exit(0);
        }


        if (commandLine.hasOption("i")) {
            pathInput = commandLine.getOptionValue("i");
            System.out.println("Input from: " + pathInput);
        } else {
            System.out.println("Input file path is required.");
            System.out.println("Use -i or --input argument to specify an input file.");
            System.exit(0);
        }
        if (commandLine.hasOption("o")) {
            System.out.println("Using user defined output path.");
            String output = commandLine.getOptionValue("o");
            File file = new File(output);
            if (file.isDirectory()) {
                if (SystemUtils.IS_OS_WINDOWS) {
                    if (output.endsWith("\\")) {
                        pathOutput = output + FilenameUtils.getBaseName(pathInput) + ".tsv";
                    } else {
                        pathOutput = output + "\\" + FilenameUtils.getBaseName(pathInput) + ".tsv";
                    }
                } else {
                    if (output.endsWith("/")) {
                        pathOutput = output + FilenameUtils.getBaseName(pathInput) + ".tsv";
                    } else {
                        pathOutput = output + "/" + FilenameUtils.getBaseName(pathInput) + ".tsv";
                    }
                }
            } else {
                pathOutput = output;
            }
            System.out.println("Output to: " + pathOutput);
        } else {
            System.out.println("No output path given. Output to the same folder as input file.");
            pathOutput = FilenameUtils.getFullPath(pathInput) + FilenameUtils.getBaseName(pathInput) + ".tsv";
            System.out.println("Output to: " + pathOutput);
        }
        if (commandLine.hasOption("d")) {
            numberOfDecimals = Integer.parseInt(commandLine.getOptionValue("d"));
            System.out.println("Using user defined number of decimals: " + numberOfDecimals);
        } else {
            numberOfDecimals = 2;
            System.out.println("Using default number of decimals: " + numberOfDecimals);
        }
        if (commandLine.hasOption("p")) {
            System.out.println(commandLine.getOptionValue("p"));
            enablePeakGroup = Boolean.parseBoolean(commandLine.getOptionValue("p"));
            System.out.println("Peak group mode: " + enablePeakGroup);
            if (commandLine.hasOption("s")) {
                peakRangeStart = Double.parseDouble(commandLine.getOptionValue("s"));
                System.out.println("Peak range start from and include: " + peakRangeStart);
            } else {
                System.out.println("Peak range start value is required if you have enabled the peak group mode.");
                System.out.println("Use -s or --pstart argument to specify a peak range start value.");
                System.exit(0);
            }
            if (commandLine.hasOption("e")) {
                peakRangeEnd = Double.parseDouble(commandLine.getOptionValue("e"));
                System.out.println("Peak range end at and include: " + peakRangeEnd);
            } else {
                System.out.println("Peak range end value is required if you have enabled the peak group mode.");
                System.out.println("Use -e or --pend argument to specify a peak range end value.");
                System.exit(0);
            }
        } else {
            enablePeakGroup = false;
            System.out.println("Peak group mode: " + enablePeakGroup);
        }

        //pathInput = "/Users/bozhao/IdeaProjects/spc.reader/data/test.SPC";
        bytes = Utils.getBytes(pathInput);
        switch (bytes[1]) {
            case 75:
                System.out.println("----------------");
                System.out.println("LSB file detected.");
                ParseLSB.toTSV(bytes, pathOutput, numberOfDecimals, enablePeakGroup, peakRangeStart, peakRangeEnd);
                break;
            case 76:
                System.out.println("----------------");
                System.out.println("MSB file detected.");
                System.out.println("Unfortunately, MSB file format support is still under development.");
                break;
            case 77:
                System.out.println("----------------");
                System.out.println("OLD file detected.");
                System.out.println("Unfortunately, OLD file format support is still under development.");
                break;
            default:
                System.out.println("----------------");
                System.out.println("Failed to recognise file format or the file format is not currently supported.");
                break;
        }

    }
}
