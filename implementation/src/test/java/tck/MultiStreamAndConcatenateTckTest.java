package tck;

import static org.testng.Assert.assertEquals;
import static tck.Await.await;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.LongStream;

import org.reactivestreams.Publisher;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import io.smallrye.mutiny.Multi;

public class MultiStreamAndConcatenateTckTest extends AbstractPublisherTck<Long> {

    private ScheduledExecutorService executor;

    @BeforeTest
    public void init() {
        executor = Executors.newScheduledThreadPool(4);
    }

    @AfterTest
    public void shutdown() {
        executor.shutdown();
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    @Test
    public void flatMapStageShouldMapElements() {
        assertEquals(await(Multi.createFrom().items(1, 2, 3)
                .emitOn(executor)
                .onItem().applyMultiAndConcatenate(n -> Multi.createFrom().items(n, n, n))
                .collectItems().asList()
                .subscribeAsCompletionStage()), Arrays.asList(1, 1, 1, 2, 2, 2, 3, 3, 3));
    }

    @Test
    public void flatMapStageShouldAllowEmptySubStreams() {
        assertEquals(await(Multi.createFrom().items(Multi.createFrom().empty(), Multi.createFrom().items(1, 2))
                .onItem().applyMultiAndConcatenate(Function.identity())
                .collectItems().asList()
                .subscribeAsCompletionStage()), Arrays.asList(1, 2));
    }

    @Test(expectedExceptions = QuietRuntimeException.class, expectedExceptionsMessageRegExp = "failed")
    public void flatMapStageShouldHandleExceptions() {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = infiniteStream()
                .on().termination((f, c) -> {
                    if (c) {
                        cancelled.complete(null);
                    }
                })
                .onItem().applyMultiAndConcatenate(foo -> {
                    throw new QuietRuntimeException("failed");
                })
                .collectItems().asList()
                .subscribeAsCompletionStage();
        await(cancelled);
        await(result);
    }

    @Test(expectedExceptions = QuietRuntimeException.class, expectedExceptionsMessageRegExp = "failed")
    public void flatMapStageShouldPropagateUpstreamExceptions() {
        await(Multi.createFrom().failure(new QuietRuntimeException("failed"))
                .onItem().applyMultiAndConcatenate(x -> Multi.createFrom().item(x))
                .collectItems().asList()
                .subscribeAsCompletionStage());
    }

    @Test(expectedExceptions = QuietRuntimeException.class, expectedExceptionsMessageRegExp = "failed")
    public void flatMapStageShouldPropagateSubstreamExceptions() {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = infiniteStream()
                .on().termination(() -> cancelled.complete(null))
                .onItem().applyMultiAndConcatenate(f -> Multi.createFrom().failure(new QuietRuntimeException("failed")))
                .collectItems().asList()
                .subscribeAsCompletionStage();
        await(cancelled);
        await(result);
    }

    @Test
    public void flatMapStageShouldPropagateCancelToSubstreams() {
        CompletableFuture<Void> outerCancelled = new CompletableFuture<>();
        CompletableFuture<Void> innerCancelled = new CompletableFuture<>();
        await(infiniteStream()
                .on().termination(() -> outerCancelled.complete(null))
                .onItem().applyMultiAndConcatenate(i -> infiniteStream().on().termination(() -> innerCancelled.complete(null)))
                .transform().byTakingFirstItems(5)
                .collectItems().asList()
                .subscribeAsCompletionStage());

        await(outerCancelled);
        await(innerCancelled);
    }

    @Override
    public Publisher<Long> createPublisher(long elements) {
        return Multi.createFrom().items(LongStream.rangeClosed(1, elements).boxed())
                .onItem().applyMultiAndConcatenate(x -> Multi.createFrom().item(x));
    }

    @Override
    public Publisher<Long> createFailedPublisher() {
        return Multi.createFrom().<Long> failure(new RuntimeException("failed"))
                .onItem().applyMultiAndConcatenate(x -> Multi.createFrom().item(x));
    }
}
