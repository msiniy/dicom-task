# dicom-task

A basic solution for one dicom task: 
 - creates xml representation of dicom file(s)
 - extracts binaries from dicome file

It follows the [Native Dicom Model](https://dicom.nema.org/medical/dicom/current/output/html/part19.html#sect_A.1), but strict adherence to the standard was not checked.

## Prerequisites
 - JDK 11
 - Maven 3
 

## How to build and run
 - clone the repo
 - run `mvn clean package` 
 - launch the result JAR: `java -jar ./target/dicom-task-1.0-SNAPSHOT.jar -o ./output_dir -c 2 ./testfiles/*.dcm`
 
 Some explanation of command line parameters:
  - option `-o` specifies the base output directory. This directory will contain all the produced artefacts.
  - option `-c 2` defines the concurrency level, i.e. an amount of threads to execute the task. This value can be reduced in the runtime, as there is no sense to create more threads than the amount of files.
  - last argument `./testfiles/*.dcm`  specifies the list of files to convert
  
 For each file there is a subdirectory created in "base output directory". Each subdirectory name includes the name of original file. 
 
 
 ## Limitations
  - almost no test coverage
  - only JPEG and PDF files are properly named 
