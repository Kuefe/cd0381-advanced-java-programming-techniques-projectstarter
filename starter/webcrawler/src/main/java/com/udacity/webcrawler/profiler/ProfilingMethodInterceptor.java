package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

    private final Clock clock;
    private final Object delegate;
    private final ProfilingState state;

    ProfilingMethodInterceptor(Clock clock, ProfilingState state, Object delegate) {
        this.clock = Objects.requireNonNull(clock);
        this.state = Objects.requireNonNull(state);
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = null;
        Instant start = null;

        if (method.getAnnotation(Profiled.class) != null) {
            start = clock.instant();
        }
        try {
            result = method.invoke(delegate, args);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ex);
        }
        finally {
            if (null != start) {
                state.record(delegate.getClass(),
                        method,
                        Duration.between(start, Instant.now(clock)));
            }
        }
        return result;
    }
}
