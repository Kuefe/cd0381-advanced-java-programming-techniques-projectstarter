package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CrawlInternalTask extends RecursiveTask<CrawlResult> {
    private final Clock clock;
    private final String url;
    private final Instant deadline;
    private final int popularWordCount;
    private final int maxDepth;
    private final List<Pattern> ignoredUrls;
    private final ConcurrentMap<String, Integer> counts;
    private final ConcurrentSkipListSet<String> visitedUrls;
    private final ReentrantLock lock = new ReentrantLock();
    /**
     *
     * @param clock
     * @param url
     * @param deadline
     * @param popularWordCount
     * @param maxDepth
     * @param ignoredUrls
     * @param counts
     * @param visitedUrls
     */
    public CrawlInternalTask(Clock clock, String url,
                             Instant deadline,
                             int popularWordCount,
                             int maxDepth,
                             List<Pattern> ignoredUrls,
                             ConcurrentMap<String, Integer> counts,
                             ConcurrentSkipListSet<String> visitedUrls) {
        this.clock = clock;
        this.url = url;
        this.deadline = deadline;
        this.popularWordCount = popularWordCount;
        this.maxDepth = maxDepth;
        this.ignoredUrls = ignoredUrls;
        this.counts = counts;
        this.visitedUrls = visitedUrls;
    }

    /**
     * method crawl over the pages
     * @return CrawlResult
     */
    @Override
    protected CrawlResult compute() {

        if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
            return new CrawlResult.Builder()
                    .setWordCounts(WordCounts.sort(counts, popularWordCount))
                    .setUrlsVisited(visitedUrls.size())
                    .build();
        }
        for (Pattern pattern : ignoredUrls) {
            if (pattern.matcher(url).matches()) {
                return new CrawlResult.Builder()
                        .setWordCounts(WordCounts.sort(counts, popularWordCount))
                        .setUrlsVisited(visitedUrls.size())
                        .build();
            }
        }
        try {
            lock.lock();
            if (visitedUrls.contains(url)) {
                return new CrawlResult.Builder()
                        .setWordCounts(WordCounts.sort(counts, popularWordCount))
                        .setUrlsVisited(visitedUrls.size())
                        .build();
            }
            visitedUrls.add(url);
            PageParser.Result result = ParallelWebCrawler.addToResult(url, counts, visitedUrls);

            for (ConcurrentMap.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
                if (counts.containsKey(e.getKey())) {
                    counts.put(e.getKey(), e.getValue() + counts.get(e.getKey()));
                } else {
                    counts.put(e.getKey(), e.getValue());
                }
            }

            Stream<String> subLinks = result.getLinks().stream();

            List<CrawlInternalTask> subtasks =
                    subLinks.map(url -> new CrawlInternalTask.Builder()
                                    .setClock(clock)
                                    .setUrl(url)
                                    .setDeadline(deadline)
                                    .setPopularWordCount(popularWordCount)
                                    .setMaxDepth(maxDepth - 1)
                                    .setIgnoredUrls(ignoredUrls)
                                    .setCounts(counts)
                                    .setVisitedUrls(visitedUrls).build())
                            .collect(Collectors.toList());
            invokeAll(subtasks);

        } catch(Exception ex) {
            ex.getLocalizedMessage();
        }finally {
            lock.unlock();
        }
        return new CrawlResult.Builder()
                .setWordCounts(WordCounts.sort(counts, popularWordCount))
                .setUrlsVisited(visitedUrls.size())
                .build();
    }

    public static final class Builder {
        private Clock clock;
        private String url;
        private Instant deadline;
        private int popularWordCount;
        private int maxDepth;
        private List<Pattern> ignoredUrls;
        private ConcurrentMap<String, Integer> counts;
        private ConcurrentSkipListSet<String> visitedUrls;

        /**
         * Setter method for clock
         * @param clock
         * @return Builder
         */
        public Builder setClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Setter method for url
         * @param url
         * @return Builder
         */
        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        /**
         * Setter method for deadline
         * @param deadline
         * @return Builder
         */
        public Builder setDeadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        /**
         * Setter method for popularWordCount
         * @param popularWordCount
         * @return Builder
         */
        public Builder setPopularWordCount(int popularWordCount) {
            this.popularWordCount = popularWordCount;
            return this;
        }

        /**
         * Setter method for maxDepth
         * @param maxDepth
         * @return Builder
         */
        public Builder setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * Setter method for ignoredUrls
         * @param ignoredUrls
         * @return Builder
         */
        public Builder setIgnoredUrls(List<Pattern> ignoredUrls) {
            this.ignoredUrls = ignoredUrls;
            return this;
        }

        /**
         * Setter method for counts
         * @param counts
         * @return Builder
         */
        public Builder setCounts(ConcurrentMap<String, Integer> counts) {
            this.counts = counts;
            return this;
        }

        /**
         * Setter method for visited Urls
         * @param visitedUrls
         * @return Builder
         */
        public Builder setVisitedUrls(ConcurrentSkipListSet<String> visitedUrls) {
            this.visitedUrls = visitedUrls;
            return this;
        }

        /**
         * build method for CrawlInternalTask
         * @return CrawlInternalTask
         */
        public CrawlInternalTask build() {
            return new CrawlInternalTask(clock,
                    url,
                    deadline,
                    popularWordCount,
                    maxDepth,
                    ignoredUrls,
                    counts,
                    visitedUrls);
        }
    }
}
