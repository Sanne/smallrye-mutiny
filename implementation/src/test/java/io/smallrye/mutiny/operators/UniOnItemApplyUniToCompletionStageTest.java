package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.Test;

import io.smallrye.mutiny.Uni;

public class UniOnItemApplyUniToCompletionStageTest {

    @Test
    public void testFlatMapWithImmediateValue() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        Uni.createFrom().item(1).onItem().produceCompletionStage(v -> CompletableFuture.completedFuture(2)).subscribe()
                .withSubscriber(test);
        test.assertCompletedSuccessfully().assertItem(2).assertNoFailure();
    }

    @Test
    public void testWithImmediateCancellation() {
        UniAssertSubscriber<Integer> test = new UniAssertSubscriber<>(true);
        AtomicBoolean called = new AtomicBoolean();
        Uni.createFrom().item(1).onItem().produceCompletionStage(v -> {
            called.set(true);
            return CompletableFuture.completedFuture(2);
        }).subscribe().withSubscriber(test);
        test.assertNotCompleted();
        assertThat(called).isFalse();
    }

    @Test
    public void testWithACompletionStageResolvedAsynchronously() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        Uni<Integer> uni = Uni.createFrom().item(1).onItem()
                .produceCompletionStage(v -> CompletableFuture.supplyAsync(() -> 42));
        uni.subscribe().withSubscriber(test);
        test.await().assertCompletedSuccessfully().assertItem(42).assertNoFailure();
    }

    @Test
    public void testWithACompletionStageResolvedAsynchronouslyWithAFailure() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        Uni<Integer> uni = Uni.createFrom().item(1).onItem().produceCompletionStage(
                v -> CompletableFuture.supplyAsync(() -> {
                    throw new IllegalStateException("boom");
                }));
        uni.subscribe().withSubscriber(test);
        test.await().assertCompletedWithFailure().assertFailure(CompletionException.class, "boom");
    }

    @Test
    public void testThatMapperIsNotCalledOnUpstreamFailure() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        Uni.createFrom().failure(new Exception("boom")).onItem().produceCompletionStage(v -> {
            called.set(true);
            return CompletableFuture.completedFuture(2);
        }).subscribe().withSubscriber(test);
        test.await().assertCompletedWithFailure().assertFailure(Exception.class, "boom");
        assertThat(called).isFalse();
    }

    @Test
    public void testWithAMapperThrowingAnException() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        Uni.createFrom().item(1)
                .onItem().<Integer> produceCompletionStage(v -> {
                    called.set(true);
                    throw new IllegalStateException("boom");
                })
                .subscribe().withSubscriber(test);
        test.await().assertCompletedWithFailure().assertFailure(IllegalStateException.class, "boom");
        assertThat(called).isTrue();
    }

    @Test
    public void testWithAMapperReturningNull() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        Uni.createFrom().item(1)
                .onItem().<Integer> produceCompletionStage(v -> {
                    called.set(true);
                    return null;
                }).subscribe().withSubscriber(test);
        test.await().assertCompletedWithFailure().assertFailure(NullPointerException.class, "");
        assertThat(called).isTrue();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatTheMapperCannotBeNull() {
        Uni.createFrom().item(1).onItem().produceCompletionStage(null);
    }

    @Test
    public void testWithCancellationBeforeEmission() {
        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        AtomicBoolean cancelled = new AtomicBoolean();
        @SuppressWarnings("unchecked")
        CompletableFuture<Integer> future = new CompletableFuture() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled.set(true);
                return true;
            }
        };

        Uni<Integer> uni = Uni.createFrom().item(1).onItem().produceCompletionStage(v -> future);
        uni.subscribe().withSubscriber(test);
        test.cancel();
        test.assertNotCompleted();
        assertThat(cancelled).isTrue();
    }
}
