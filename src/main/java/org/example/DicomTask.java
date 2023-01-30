package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@CommandLine.Command(name = "DicomTask", mixinStandardHelpOptions = true)
public class DicomTask implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(DicomTask.class);
    private static final int MAX_THREADS_NUM = 10;
    @CommandLine.Option(names = {"-c"}, defaultValue = "1")
    int numOfThreads;
    @CommandLine.Parameters
    List<Path> files;

    @CommandLine.Option(names = {"-o", "--output-dir"}, defaultValue = "output")
    Path outputDir;

    @Override
    public Integer call() {
        List<Callable<Converter>> tasks = files.stream()
                .filter(Files::exists)
                .map(p -> new Converter(p, this.outputDir)).collect(Collectors.toList());
        if (tasks.size() < 1) {
            return 0;
        }
        numOfThreads = Math.min(Math.max(numOfThreads, 1), MAX_THREADS_NUM);
        numOfThreads = Math.min(numOfThreads, tasks.size());
        log.debug("numOfThreads={}", numOfThreads);
        try {
            Files.createDirectories(this.outputDir);
        } catch (IOException e) {
            log.error("Can't create output dir {}", e);
            return 1;
        }
        ExecutorService executor = Executors.newFixedThreadPool(numOfThreads);
        List<Future<Converter>> results;
        try {
            results = executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            log.error("Error occurred while executing tasks: {}", e);
            return 1;
        }

        for (Future<Converter> r : results) {
            try {
                var resolved = r.get();
                log.info("Successfully converted {} into {}", resolved.getDicomFilePath(), resolved.getOutputDir());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new DicomTask()).execute(args));
    }

}