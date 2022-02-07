package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
    private final Clock clock;
    private static PageParserFactory parserFactory;
    private final Duration timeout;
    private final int popularWordCount;
    private final List<Pattern> ignoredUrls;
    private final int maxDepth;
    private final ForkJoinPool pool;

    @Inject
    ParallelWebCrawler(
            Clock clock,
            PageParserFactory parserFactory,
            @Timeout Duration timeout,
            @PopularWordCount int popularWordCount,
            @IgnoredUrls List<Pattern> ignoredUrls,
            @MaxDepth int maxDepth,
            @TargetParallelism int threadCount) {
        this.clock = clock;
        this.parserFactory = parserFactory;
        this.timeout = timeout;
        this.popularWordCount = popularWordCount;
        this.ignoredUrls = ignoredUrls;
        this.maxDepth = maxDepth;
        this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    }

    @Override
    public CrawlResult crawl(List<String> startingUrls) {
        Instant deadline = clock.instant().plus(timeout);
        Map<String, Integer> counts = new HashMap<>();
        Set<String> visitedUrls = new HashSet<>();

        List<CrawlResult> resultList = new LinkedList<>();
        resultList.add(new CrawlResult.Builder()
                .setWordCounts(counts)
                .setUrlsVisited(visitedUrls.size())
                .build());

        for (String url : startingUrls) {
            Boolean isIgnoredUrls = FALSE;
            for (Pattern pattern : ignoredUrls) {
                if (pattern.matcher(url).matches()) {
                    isIgnoredUrls = TRUE;
                }
            }
            if (!url.isEmpty() && url != null && maxDepth > 0 && !isIgnoredUrls) {
                resultList.add(pool.invoke(new CrawlInternalTask(clock,
                        url,
                        deadline,
                        popularWordCount,
                        maxDepth,
                        ignoredUrls,
                        counts,
                        visitedUrls)));
            }
        }

        Map<String, Integer> wordCounts = new HashMap<String, Integer>();
        Set<String> visitUrls = new HashSet<>();

        for (CrawlResult result : resultList) {
            wordCounts.putAll(result.getWordCounts());
            for (String url: visitedUrls) {
                visitUrls.add(url);
            }
        }

        if (wordCounts.size() >= 1) {
            return new CrawlResult.Builder()
                    .setWordCounts(WordCounts.sort(wordCounts, popularWordCount))
                    .setUrlsVisited(visitUrls.size())
                    .build();
        } else {
            return new CrawlResult.Builder()
                    .setWordCounts(counts)
                    .setUrlsVisited(visitUrls.size())
                    .build();
        }


    }

    static PageParser.Result addToResult(String url, Map<String, Integer> counts, Set<String> visitedUrls) {
        visitedUrls.add(url);
        return parserFactory.get(url).parse();
    }

    @Override
    public int getMaxParallelism() {
        return Runtime.getRuntime().availableProcessors();
    }
}
