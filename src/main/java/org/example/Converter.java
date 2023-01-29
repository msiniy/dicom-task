package org.example;

import org.dcm4che3.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;


public class Converter implements Callable<Converter> {

    private static final Logger log = LoggerFactory.getLogger(Converter.class);
    private final Path dicomFilePath;
    private final Path outputBaseDirPath;

    private Path outputDir;

    public Converter(Path dicomFile, Path outputDir) {
        this.dicomFilePath = dicomFile;
        this.outputBaseDirPath = outputDir;
    }

    public Path getDicomFilePath() {
        return dicomFilePath;
    }

    @Override
    public Converter call() throws IOException {
        Path xmlDirPath = Files.createTempDirectory(outputBaseDirPath,
                this.dicomFilePath.getFileName().toString());
        outputDir = Files.createDirectories(xmlDirPath);
        log.debug("Started converting {} into {}", dicomFilePath, outputDir);
        DicomInputStream dis = new DicomInputStream(dicomFilePath.toFile());
        dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
        dis.setDicomInputHandler(new XMLWriter(xmlDirPath));
        dis.readDataset();
        dis.close();
        return this;
    }

    public Path getOutputDir() {
        return outputDir;
    }
}
