package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
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

    /**
     * Initialize ParallelWebCrawler
     * @param clock
     * @param parserFactory
     * @param timeout
     * @param popularWordCount
     * @param ignoredUrls
     * @param maxDepth
     * @param threadCount
     */
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
        ParallelWebCrawler.parserFactory = parserFactory;
        this.timeout = timeout;
        this.popularWordCount = popularWordCount;
        this.ignoredUrls = ignoredUrls;
        this.maxDepth = maxDepth;
        this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    }

    /**
     * crawl the startingUrls
     * @param startingUrls the starting points of the crawl.
     * @return CrawlResult
     */
    @Override
    public CrawlResult crawl(List<String> startingUrls) {
        Instant deadline = clock.instant().plus(timeout);
        ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
        ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();

        List<CrawlResult> resultList = new LinkedList<>();
        resultList.add(new CrawlResult.Builder()
                .setWordCounts(counts)
                .setUrlsVisited(0)
                .build());

        if (maxDepth < 1) {
            return new CrawlResult.Builder()
                    .setWordCounts(counts)
                    .setUrlsVisited(0)
                    .build();
        }

        for (String url : startingUrls) {
            if (isNotIgnoredUrls(url)) {
                resultList.add(pool.invoke(new CrawlInternalTask.Builder()
                        .setClock(clock)
                        .setUrl(url)
                        .setDeadline(deadline)
                        .setPopularWordCount(popularWordCount)
                        .setMaxDepth(maxDepth)
                        .setIgnoredUrls(ignoredUrls)
                        .setCounts(counts)
                        .setVisitedUrls(visitedUrls).build()));
            }
        }

        for (CrawlResult result : resultList) {
            counts.putAll(result.getWordCounts());
            visitedUrls.addAll(visitedUrls);
        }

        if (counts.size() >= 1) {
            return new CrawlResult.Builder()
                    .setWordCounts(WordCounts.sort(counts, popularWordCount))
                    .setUrlsVisited(visitedUrls.size())
                    .build();
        } else {
            return new CrawlResult.Builder()
                    .setWordCounts(counts)
                    .setUrlsVisited(visitedUrls.size())
                    .build();
        }
    }

    /**
     * Check if the url match to ignore pattern
     * @param url
     * @return Boolean
     */
    private Boolean isNotIgnoredUrls(String url) {
        for (Pattern pattern : ignoredUrls) {
            if (pattern.matcher(url).matches()) {
                return FALSE;
            }
        }
        return TRUE;
    }

    /**
     * add the url to the result
     * @param url
     * @param counts
     * @param visitedUrls
     * @return PageParser.Result
     */
    static PageParser.Result addToResult(String url, Map<String, Integer> counts, Set<String> visitedUrls) {
        visitedUrls.add(url);
        return parserFactory.get(url).parse();
    }

    /**
     * get max. parallelism based on the available processors
     * @return int
     */
    @Override
    public int getMaxParallelism() {
        return Runtime.getRuntime().availableProcessors();
    }
}
