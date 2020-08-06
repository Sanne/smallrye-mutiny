package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import io.smallrye.mutiny.subscription.UniEmitter;
import org.testng.annotations.Test;

import io.smallrye.mutiny.Uni;

public class UniOnItemTransformTest {

    private final Uni<Integer> one = Uni.createFrom().item(1);

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatMapperMustNotBeNull() {
        Uni.createFrom().item(1).map(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatSourceMustNotBeNull() {
        new UniOnItemTransform<>(null, Function.identity());
    }

    @Test
    public void testSimpleMapping() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();

        one.map(v -> v + 1).subscribe().withSubscriber(ts);

        ts.assertCompletedSuccessfully()
                .assertItem(2);
    }

    @Test
    public void testWithTwoSubscribers() {
        UniAssertSubscriber<Integer> ts1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> ts2 = UniAssertSubscriber.create();

        AtomicInteger count = new AtomicInteger();
        Uni<Integer> uni = one.map(v -> v + count.incrementAndGet());
        uni.subscribe().withSubscriber(ts1);
        uni.subscribe().withSubscriber(ts2);

        ts1.assertCompletedSuccessfully()
                .assertItem(2);
        ts2.assertCompletedSuccessfully()
                .assertItem(3);
    }

    @Test
    public void testWhenTheMapperThrowsAnException() {
        UniAssertSubscriber<Object> ts = UniAssertSubscriber.create();

        one.map(v -> {
            throw new RuntimeException("failure");
        }).subscribe().withSubscriber(ts);

        ts.assertFailure(RuntimeException.class, "failure");
    }

    @Test
    public void testWhenTheMapperThrowsAnError() {
        UniAssertSubscriber<Object> ts = UniAssertSubscriber.create();

        one.map(v -> {
            throw new AssertionError("OH NO!");
        }).subscribe().withSubscriber(ts);

        ts.assertFailure(AssertionError.class, "OH NO!");
    }

    @Test
    public void testThatMapperCanReturnNull() {
        UniAssertSubscriber<Object> ts = UniAssertSubscriber.create();

        one.map(v -> null).subscribe().withSubscriber(ts);

        ts.assertCompletedSuccessfully().assertItem(null);
    }

    @Test
    public void testThatMapperIsCalledWithNull() {
        UniAssertSubscriber<String> ts = UniAssertSubscriber.create();
        Uni.createFrom().item((String) null).map(x -> "foo").subscribe().withSubscriber(ts);
        ts.assertCompletedSuccessfully().assertItem("foo");
    }

    @Test
    public void testThatMapperIsCalledOnTheRightExecutor() {
        UniAssertSubscriber<Integer> ts = new UniAssertSubscriber<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<String> threadName = new AtomicReference<>();
            Uni.createFrom().item(1)
                    .emitOn(executor)
                    .map(i -> {
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
    public void testThatMapperIsNotCalledIfPreviousStageFailed() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        Uni.createFrom().<Integer> failure(new Exception("boom"))
                .map(x -> {
                    called.set(true);
                    return x + 1;
                }).subscribe().withSubscriber(ts);

        ts.assertFailure(Exception.class, "boom");
        assertThat(called).isFalse();
    }

    @Test
    public void verifyThatTheMapperIsNotCalledAfterCancellationWithEmitter() {
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean();
        UniAssertSubscriber<Integer> subscriber = Uni.createFrom()
                .emitter((Consumer<UniEmitter<? super Integer>>) emitter::set)
                .onItem().transform(i -> {
                    called.set(true);
                    return i + 1;
                })
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        assertThat(called).isFalse();
        subscriber.assertNotCompleted().assertNoFailure().assertSubscribed();
        subscriber.cancel();
        emitter.get().complete(1);
        assertThat(called).isFalse();
    }

    @Test
    public void verifyThatTheMapperIsNotCalledAfterImmediateCancellationWithEmitter() {
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean();
        UniAssertSubscriber<Integer> subscriber = Uni.createFrom()
                .emitter((Consumer<UniEmitter<? super Integer>>) emitter::set)
                .onItem().transform(i -> {
                    called.set(true);
                    return i + 1;
                })
                .subscribe().withSubscriber(new UniAssertSubscriber<>(true));

        assertThat(called).isFalse();
        subscriber.assertNotCompleted().assertNoFailure().assertSubscribed();
        emitter.get().complete(1);
        assertThat(called).isFalse();
    }
}
