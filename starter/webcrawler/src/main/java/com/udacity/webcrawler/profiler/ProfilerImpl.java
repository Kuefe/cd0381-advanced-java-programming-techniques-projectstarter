package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

    private final Clock clock;
    private final ProfilingState state = new ProfilingState();
    private final ZonedDateTime startTime;

    @Inject
    ProfilerImpl(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        this.startTime = ZonedDateTime.now(clock);
    }

    @Override
    public <T> T wrap(Class<T> klass, T delegate) {
        Objects.requireNonNull(klass);

         if (checkAnnotationProfiled(klass)) {
            Object proxyInstance = Proxy.newProxyInstance(
                    ProfilerImpl.class.getClassLoader(),
                    new Class[]{klass},
                    new ProfilingMethodInterceptor(clock, state, delegate));

            return (T) proxyInstance;
        }
        throw new IllegalArgumentException("expected exception");
    }

    private Boolean checkAnnotationProfiled(Class<?> klass) {
        Method[] methods = klass.getDeclaredMethods();
        if (methods.length == 0) return false;
        for (Method method : methods) {
            if (method.getAnnotation(Profiled.class) != null) return true;
        }
        return false;
    }

    @Override
    public void writeData(Path path) throws IOException{
        try (Writer writer = Files.newBufferedWriter(path, CREATE, APPEND)) {
            writeData(writer);
        }
    }

    @Override
    public void writeData(Writer writer) throws IOException {
        writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
        writer.write(System.lineSeparator());
        state.write(writer);
        writer.write(System.lineSeparator());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfilerImpl profiler = (ProfilerImpl) o;
        return Objects.equals(clock, profiler.clock) && Objects.equals(state, profiler.state) && Objects.equals(startTime, profiler.startTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clock, state, startTime);
    }
}
