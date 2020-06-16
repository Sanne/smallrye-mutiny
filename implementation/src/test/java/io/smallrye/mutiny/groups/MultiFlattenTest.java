package io.smallrye.mutiny.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.Test;

import io.reactivex.Flowable;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.test.MultiAssertSubscriber;

public class MultiFlattenTest {

    @Test
    public void testDisjointWithMultis() {
        AtomicBoolean subscribed = new AtomicBoolean();
        MultiAssertSubscriber<String> subscriber = Multi.createFrom().items(
                Multi.createFrom().items("a", "b", "c"),
                Multi.createFrom().items("d", "e"),
                Multi.createFrom().empty(),
                Multi.createFrom().items("f", "g")
                        .on().subscribed(s -> subscribed.set(true)))
                .onItem().<String> disjoint()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(4));
        assertThat(subscribed).isFalse();
        subscriber.assertReceived("a", "b", "c", "d");
        subscriber.request(3);
        subscriber.assertCompletedSuccessfully();
        assertThat(subscriber.items()).contains("e", "f", "g");
        assertThat(subscribed).isTrue();
    }

    @Test
    public void testDisjointWithPublishers() {
        AtomicBoolean subscribed = new AtomicBoolean();
        MultiAssertSubscriber<String> subscriber = Multi.createFrom().items(
                Flowable.just("a", "b", "c"),
                Flowable.just("d", "e"),
                Flowable.empty(),
                Flowable.just("f", "g")
                        .doOnSubscribe(s -> subscribed.set(true)))
                .onItem().<String> disjoint()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(4));
        assertThat(subscribed).isFalse();
        subscriber.assertReceived("a", "b", "c", "d");
        subscriber.request(3);
        subscriber.assertCompletedSuccessfully();
        assertThat(subscriber.items()).contains("e", "f", "g");
        assertThat(subscribed).isTrue();
    }

    @Test
    public void testDisjointWithArrays() {
        MultiAssertSubscriber<String> subscriber = Multi.createFrom().items(
                new String[] { "a", "b", "c" },
                new String[] { "d", "e" },
                new String[] {},
                new String[] { "f", "g" })
                .onItem().<String> disjoint()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(4));
        subscriber.assertReceived("a", "b", "c", "d");
        subscriber.request(3);
        subscriber.assertCompletedSuccessfully();
        assertThat(subscriber.items()).contains("e", "f", "g");
    }

    @Test
    public void testDisjointWithIterables() {
        MultiAssertSubscriber<String> subscriber = Multi.createFrom().items(
                Arrays.asList("a", "b", "c"),
                Arrays.asList("d", "e"),
                Collections.emptySet(),
                Collections.singleton("f"),
                Collections.singleton("g"))
                .onItem().<String> disjoint()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(4));
        subscriber.assertReceived("a", "b", "c", "d");
        subscriber.request(3);
        subscriber.assertCompletedSuccessfully();
        assertThat(subscriber.items()).contains("e", "f", "g");
    }

    @Test
    public void testWithMultisWithAFailure() {
        AtomicBoolean subscribed = new AtomicBoolean();
        MultiAssertSubscriber<String> subscriber = Multi.createFrom().items(
                Multi.createFrom().items("a", "b", "c"),
                Multi.createFrom().items("d", "e"),
                Multi.createFrom().failure(new IOException("boom")),
                Multi.createFrom().items("f", "g")
                        .on().subscribed(s -> subscribed.set(true)))
                .onItem().<String> disjoint()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(4));
        assertThat(subscribed).isFalse();
        subscriber.assertReceived("a", "b", "c", "d");
        subscriber.request(3);
        subscriber.assertHasFailedWith(IOException.class, "boom");
        assertThat(subscribed).isFalse();
    }

    @Test
    public void testWithMultisWithOneEmittingAFailure() {
        AtomicBoolean subscribed = new AtomicBoolean();
        MultiAssertSubscriber<String> subscriber = Multi.createFrom().items(
                Multi.createFrom().items("a", "b", "c"),
                Multi.createFrom().items("d", "e"),
                Multi.createFrom().emitter(e -> {
                    e.emit("f");
                    e.fail(new IOException("boom"));
                }),
                Multi.createFrom().items("g")
                        .on().subscribed(s -> subscribed.set(true)))
                .onItem().<String> disjoint()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(4));
        assertThat(subscribed).isFalse();
        subscriber.assertReceived("a", "b", "c", "d");
        subscriber.request(3);
        subscriber.assertHasFailedWith(IOException.class, "boom");
        assertThat(subscribed).isFalse();
    }

    @Test
    public void testWithIterablesContainingNull() {
        MultiAssertSubscriber<String> subscriber = Multi.createFrom().items(
                Arrays.asList("a", "b", "c"),
                Arrays.asList("d", "e"),
                Collections.emptySet(),
                Collections.singleton(null),
                Collections.singleton("g"))
                .onItem().<String> disjoint()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(4));
        subscriber.assertReceived("a", "b", "c", "d");
        subscriber.request(3);
        subscriber.assertHasFailedWith(NullPointerException.class, "");
    }

    @Test
    public void testWithInvalidType() {
        Multi.createFrom().items("a", "b", "c")
                .onItem().disjoint()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(2))
                .assertHasFailedWith(IllegalArgumentException.class, "String");
    }

    @Test
    public void testFlatMapRequestsWithEmissionOnExecutor() {
        MultiAssertSubscriber<String> subscriber = Multi.createFrom().items("a", "b", "c", "d", "e", "f", "g", "h")
                .onItem()
                .applyUni(s -> Uni.createFrom().item(s.toUpperCase()).onItem().delayIt().by(Duration.ofMillis(10)))
                .concatenate()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(0));

        subscriber
                .assertSubscribed()
                .assertHasNotReceivedAnyItem()
                .request(1);

        await().until(() -> subscriber.items().contains("A"));

        subscriber.request(2);
        await().until(() -> subscriber.items().contains("B") && subscriber.items().contains("C"));

        subscriber.request(100);
        subscriber.await().assertCompletedSuccessfully();
    }
}
