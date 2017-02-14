# spc-reader-java

[![Build Status](https://travis-ci.org/wingardium/spc-reader-java.svg?branch=master)](https://travis-ci.org/wingardium/spc-reader-java)
[![DOI](https://zenodo.org/badge/81833901.svg)](https://zenodo.org/badge/latestdoi/81833901)

This java library provides functions that read and convert SPC files to TSV files.

## Usage

```
gradle clean jar fatJar
```
This will build and create two jar files in the `build/libs` directory: `spc-reader-java-1.0.0-ALPHA.jar` and `spc-reader-java-dist-1.0.0-ALPHA.jar`.

`spc-reader-java-1.0.0-ALPHA.jar` only contains the spc-reader-java classes, thus it requires external libraries to work properly.

`spc-reader-java-dist-1.0.0-ALPHA.jar` is the standalone library, which includes all the dependency libraries.

### Command Line Options
`spc-reader-java-dist-1.0.0-ALPHA.jar` is also runnable in the command line. Below are available options:

```
-h  --help      list all available command line options
-i  --input     input file path
-o  --output    [optional] output file path, if not set then output will be the same directory as input
-d  --decimals  [optional] number of decimals in converted tsv file, default value: 2
-p  --peak      [optional] enable peak group mode, format: true/false, default value: false
-s  --pstart    [required if -p is set to true] peak group range start value, inclusive
-e  --pend      [required if -p is set to true] peak group range end value, inclusive
```

### Dependency libraries
`spc-reader-java-1.0.0-ALPHA.jar` requires the following libraries:
- commons-lang3, version: 3.5
- commons-math3, version: 3.6.1
- commons-io, version: 2.5
- commons-cli, version: 1.3.1

### Peak Group Mode
The original data is exported if peak group mode is disabled.

The peak group mode sums values within a certain range of the peak point. Normally the peak point should be an integer number, and the range start/end value should be integer multiples of the scanning resolution.

The range start/end value must be greater than 0 and less than 1.

The sum of range start and end values must be less than 1 to avoid overlapped peak group range.

For example, if the scanning resolution is 0.05, and the peak range start/end values are set to 0.4/0.35, then for peak point 7, it sums values from 6.60 to 7.35, which equals to 8 data points before peak point, 7 data points after peak point, plus peak point itself.

## Citation
Please cite this tool as below or use your desired citation styles:
```
Zhao, Bo. (2017). SPC Reader Java [Software]. Zenodo. http://doi.org/10.5281/zenodo.291467
```

## TODO
- Add header information into exported tsv file comment section
- Add range start/end values validation before process

## SPC File Format

The SPC file format is used for storing spectroscopic data. It was invented by a company called Galactic Industries, which was obtained later by Thermo LabSystems.

The SPC file is a binary format file, which contains all data and corresponding configuration information from the instrument that produces the file. As of the binary format, it is not readable in standard text editors.

More information can be obtained from the original SDK available at : [https://github.com/wingardium/spc-sdk](https://github.com/wingardium/spc-sdk)
