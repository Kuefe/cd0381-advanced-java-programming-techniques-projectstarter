package com.udacity.webcrawler.main;

import com.google.inject.Guice;
import com.udacity.webcrawler.WebCrawler;
import com.udacity.webcrawler.WebCrawlerModule;
import com.udacity.webcrawler.json.ConfigurationLoader;
import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.json.CrawlResultWriter;
import com.udacity.webcrawler.json.CrawlerConfiguration;
import com.udacity.webcrawler.profiler.Profiler;
import com.udacity.webcrawler.profiler.ProfilerModule;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.Objects;

public final class WebCrawlerMain {

    private final CrawlerConfiguration config;

    private WebCrawlerMain(CrawlerConfiguration config) {
        this.config = Objects.requireNonNull(config);
    }

    @Inject
    private WebCrawler crawler;

    @Inject
    private Profiler profiler;

    private void run() throws IOException {
        Guice.createInjector(new WebCrawlerModule(config), new ProfilerModule()).injectMembers(this);

        CrawlResult result = crawler.crawl(config.getStartPages());
        CrawlResultWriter resultWriter = new CrawlResultWriter(result);
        // Write the crawl results to a JSON file (or System.out if the file name is empty)
        String resultPath = config.getResultPath();
        if (resultPath.isEmpty()) {
            resultWriter.write(new OutputStreamWriter(System.out));
        } else {
            resultWriter.write(Path.of(resultPath));
        }

        String profilePath = config.getProfileOutputPath();

        if (profilePath.isEmpty()) {
            profiler.writeData(new OutputStreamWriter(System.out));

        } else {
            profiler.writeData(Path.of(profilePath));
        }

    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: WebCrawlerMain [starting-url]");
            return;
        }

        CrawlerConfiguration config = new ConfigurationLoader(Path.of(args[0])).load();
        try {
            new WebCrawlerMain(config).run();
        } catch (IOException ex) {
            throw new IOException("Can't write to 'profileOutputPath'");
        }
    }
}
