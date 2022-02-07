package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
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
    private final Map<String, Integer> counts;
    private final Set<String> visitedUrls;

    public CrawlInternalTask(Clock clock, String url,
                             Instant deadline,
                             int popularWordCount,
                             int maxDepth,
                             List<Pattern> ignoredUrls,
                             Map<String, Integer> counts,
                             Set<String> visitedUrls) {
        this.clock = clock;
        this.url = url;
        this.deadline = deadline;
        this.popularWordCount = popularWordCount;
        this.maxDepth = maxDepth;
        this.ignoredUrls = ignoredUrls;
        this.counts = counts;
        this.visitedUrls = visitedUrls;
    }

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
        if (visitedUrls.contains(url)) {
            return new CrawlResult.Builder()
                    .setWordCounts(WordCounts.sort(counts, popularWordCount))
                    .setUrlsVisited(visitedUrls.size())
                    .build();
        }

        visitedUrls.add(url);
        PageParser.Result result = ParallelWebCrawler.addToResult(url, counts, visitedUrls);

        for (Map.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
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

        return new CrawlResult.Builder()
                .setWordCounts(WordCounts.sort(counts, popularWordCount))
                .setUrlsVisited(visitedUrls.size())
                .build();
    }

    public Clock getClock() {
        return clock;
    }

    public String getUrl() {
        return url;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public int getPopularWordCount() {
        return popularWordCount;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public List<Pattern> getIgnoredUrls() {
        return ignoredUrls;
    }

    public Map<String, Integer> getCounts() {
        return counts;
    }

    public Set<String> getVisitedUrls() {
        return visitedUrls;
    }

    public static final class Builder {
        private Clock clock;
        private String url;
        private Instant deadline;
        private int popularWordCount;
        private int maxDepth;
        private List<Pattern> ignoredUrls;
        private Map<String, Integer> counts;
        private Set<String> visitedUrls;

        public Builder setClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setDeadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder setPopularWordCount(int popularWordCount) {
            this.popularWordCount = popularWordCount;
            return this;
        }

        public Builder setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder setIgnoredUrls(List<Pattern> ignoredUrls) {
            this.ignoredUrls = ignoredUrls;
            return this;
        }

        public Builder setCounts(Map<String, Integer> counts) {
            this.counts = counts;
            return this;
        }

        public Builder setVisitedUrls(Set<String> visitedUrls) {
            this.visitedUrls = visitedUrls;
            return this;
        }

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
