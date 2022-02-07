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
                subLinks.map(url -> new CrawlInternalTask(clock,
                                url,
                                deadline,
                                popularWordCount,
                                maxDepth - 1,
                                ignoredUrls,
                                counts,
                                visitedUrls))
                        .collect(Collectors.toList());
        invokeAll(subtasks);

        if (counts.isEmpty()) {
            return new CrawlResult.Builder()
                    .setWordCounts(counts)
                    .setUrlsVisited(visitedUrls.size())
                    .build();
        }

        return new CrawlResult.Builder()
                .setWordCounts(WordCounts.sort(counts, popularWordCount))
                .setUrlsVisited(visitedUrls.size())
                .build();
    }

}
