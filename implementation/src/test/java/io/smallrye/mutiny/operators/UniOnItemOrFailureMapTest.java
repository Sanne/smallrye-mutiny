package io.smallrye.mutiny.operators;

import io.smallrye.mutiny.CompositeException;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class UniOnItemOrFailureMapTest {

    private final Uni<Integer> one = Uni.createFrom().item(1);
    private final Uni<Void> none = Uni.createFrom().nullItem();
    private final Uni<Integer> failed = Uni.createFrom().failure(new IOException("boom"));

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatMapperMustNotBeNull() {
        Uni.createFrom().item(1).onItemOrFailure().transform(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatSourceMustNotBeNull() {
        new UniOnItemOrFailureMap<>(null, (x, f) -> x);
    }

    @Test
    public void testMappingOnItem() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();

        AtomicInteger count = new AtomicInteger();
        one.onItemOrFailure().transform((i, f) -> {
            assertThat(f).isNull();
            count.incrementAndGet();
            return i + 1;
        }).subscribe().withSubscriber(ts);

        ts.assertCompletedSuccessfully()
                .assertItem(2);

        assertThat(count).hasValue(1);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testMappingOnItemDeprecated() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();

        AtomicInteger count = new AtomicInteger();
        one.onItemOrFailure().apply((i, f) -> {
            assertThat(f).isNull();
            count.incrementAndGet();
            return i + 1;
        }).subscribe().withSubscriber(ts);

        ts.assertCompletedSuccessfully()
                .assertItem(2);

        assertThat(count).hasValue(1);
    }

    @Test
    public void testMappingOnNullItem() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();

        AtomicInteger count = new AtomicInteger();
        none.onItemOrFailure().transform((i, f) -> {
            assertThat(f).isNull();
            count.incrementAndGet();
            return 2;
        }).subscribe().withSubscriber(ts);

        ts.assertCompletedSuccessfully()
                .assertItem(2);

        assertThat(count).hasValue(1);
    }

    @Test
    public void testMappingOnFailure() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();

        AtomicInteger count = new AtomicInteger();
        failed.onItemOrFailure().transform((i, f) -> {
            assertThat(i).isNull();
            assertThat(f).isNotNull().isInstanceOf(IOException.class).hasMessageContaining("boom");
            count.incrementAndGet();
            return 2;
        }).subscribe().withSubscriber(ts);

        ts.assertCompletedSuccessfully()
                .assertItem(2);

        assertThat(count).hasValue(1);
    }

    @Test
    public void testMappingOnItemThrowingException() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();

        AtomicInteger count = new AtomicInteger();
        one.onItemOrFailure().<Integer>transform((i, f) -> {
            assertThat(f).isNull();
            count.incrementAndGet();
            throw new IllegalStateException("kaboom");
        }).subscribe().withSubscriber(ts);

        ts.assertFailure(IllegalStateException.class, "kaboom");
        assertThat(count).hasValue(1);
    }

    @Test
    public void testMappingOnFailureThrowingException() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();

        AtomicInteger count = new AtomicInteger();
        failed.onItemOrFailure().<Integer>transform((i, f) -> {
            assertThat(i).isNull();
            assertThat(f).isNotNull().isInstanceOf(IOException.class).hasMessageContaining("boom");
            count.incrementAndGet();
            throw new IllegalStateException("kaboom");
        }).subscribe().withSubscriber(ts);

        ts.assertFailure(CompositeException.class, "kaboom");
        ts.assertFailure(CompositeException.class, "boom");

        assertThat(count).hasValue(1);
    }

    @Test
    public void testWithTwoSubscribers() {
        UniAssertSubscriber<Integer> ts1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> ts2 = UniAssertSubscriber.create();

        AtomicInteger count = new AtomicInteger();
        Uni<Integer> uni = one.onItemOrFailure().transform((v, f) -> v + count.incrementAndGet());
        uni.subscribe().withSubscriber(ts1);
        uni.subscribe().withSubscriber(ts2);

        ts1.assertCompletedSuccessfully()
                .assertItem(2);
        ts2.assertCompletedSuccessfully()
                .assertItem(3);
    }

    @Test
    public void testThatMapperCanReturnNull() {
        UniAssertSubscriber<Void> ts = UniAssertSubscriber.create();

        one.onItemOrFailure().<Void>transform((v, f) -> null).subscribe().withSubscriber(ts);

        ts.assertCompletedSuccessfully().assertItem(null);
    }

    @Test
    public void testThatMapperIsCalledOnTheRightExecutorOnItem() {
        UniAssertSubscriber<Integer> ts = new UniAssertSubscriber<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<String> threadName = new AtomicReference<>();
            one
                    .emitOn(executor)
                    .onItemOrFailure().transform((i, f) -> {
                threadName.set(Thread.currentThread().getName());
                return i + 1;
            })
                    .subscribe().withSubscriber(ts);

            ts.await().assertCompletedSuccessfully().assertItem(2);
            assertThat(threadName).isNotNull().doesNotHaveValue("main");
            assertThat(ts.getOnItemThreadName()).isEqualTo(threadName.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testThatMapperIsCalledOnTheRightExecutorOnFailure() {
        UniAssertSubscriber<Integer> ts = new UniAssertSubscriber<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<String> threadName = new AtomicReference<>();
            failed
                    .emitOn(executor)
                    .onItemOrFailure().transform((i, f) -> {
                threadName.set(Thread.currentThread().getName());
                assertThat(i).isNull();
                assertThat(f).isNotNull();
                return 1;
            })
                    .subscribe().withSubscriber(ts);

            ts.await().assertCompletedSuccessfully().assertItem(1);
            assertThat(threadName).isNotNull().doesNotHaveValue("main");
            assertThat(ts.getOnItemThreadName()).isEqualTo(threadName.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testMapperNotCalledAfterCancellationOnItem() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        Uni.createFrom().<Integer>emitter(emitter::set)
                .onItemOrFailure().transform((i, f) -> {
            count.incrementAndGet();
            return i + 1;
        }).subscribe().withSubscriber(ts);

        ts.assertSubscribed();
        ts.cancel();
        assertThat(count).hasValue(0);
        emitter.get().complete(1);
        assertThat(count).hasValue(0);
    }

    @Test
    public void testMapperNotCalledAfterImmediateCancellationOnItem() {
        UniAssertSubscriber<Integer> ts = new UniAssertSubscriber<>(true);
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        Uni.createFrom().<Integer>emitter(emitter::set)
                .onItemOrFailure().transform((i, f) -> {
            count.incrementAndGet();
            return i + 1;
        }).subscribe().withSubscriber(ts);

        ts.assertSubscribed();
        assertThat(count).hasValue(0);
        emitter.get().complete(1);
        assertThat(count).hasValue(0);
    }

    @Test
    public void testMapperNotCalledAfterCancellationOnFailure() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        Uni.createFrom().<Integer>emitter(emitter::set)
                .onItemOrFailure().transform((i, f) -> {
            count.incrementAndGet();
            return i + 1;
        }).subscribe().withSubscriber(ts);

        ts.assertSubscribed();
        ts.cancel();
        assertThat(count).hasValue(0);
        emitter.get().fail(new IOException("boom"));
        assertThat(count).hasValue(0);
    }

    @Test
    public void testMapperNotCalledAfterImmediateCancellationOnFailure() {
        UniAssertSubscriber<Integer> ts = new UniAssertSubscriber<>(true);
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        Uni.createFrom().<Integer>emitter(emitter::set)
                .onItemOrFailure().transform((i, f) -> {
            count.incrementAndGet();
            return i + 1;
        }).subscribe().withSubscriber(ts);

        ts.assertSubscribed();
        assertThat(count).hasValue(0);
        emitter.get().fail(new IOException("boom"));
        assertThat(count).hasValue(0);
    }
}
