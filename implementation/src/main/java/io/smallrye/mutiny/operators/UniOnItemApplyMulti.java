package io.smallrye.mutiny.operators;

import static io.smallrye.mutiny.helpers.ParameterValidation.MAPPER_RETURNED_NULL;
import static io.smallrye.mutiny.helpers.ParameterValidation.nonNull;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.EmptyUniSubscription;
import io.smallrye.mutiny.helpers.Subscriptions;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.smallrye.mutiny.subscription.UniSubscriber;
import io.smallrye.mutiny.subscription.UniSubscription;

public class UniOnItemApplyMulti<I, O> extends AbstractMulti<O> {

    private final Function<? super I, ? extends Publisher<? extends O>> mapper;
    private final Uni<I> upstream;

    public UniOnItemApplyMulti(Uni<I> upstream, Function<? super I, ? extends Publisher<? extends O>> mapper) {
        this.upstream = nonNull(upstream, "upstream");
        this.mapper = nonNull(mapper, "mapper");
    }

    @Override
    public void subscribe(MultiSubscriber<? super O> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("The subscriber must not be `null`");
        }
        upstream.subscribe().withSubscriber(new FlatMapPublisherSubscriber<>(subscriber, mapper));
    }

    @SuppressWarnings({ "SubscriberImplementation", "ReactiveStreamsSubscriberImplementation" })
    static final class FlatMapPublisherSubscriber<I, O> implements Subscriber<O>, UniSubscriber<I>, Subscription {

        private final AtomicReference<Subscription> secondUpstream;
        private final AtomicReference<UniSubscription> firstUpstream;
        private final Subscriber<? super O> downstream;
        private final Function<? super I, ? extends Publisher<? extends O>> mapper;
        private final AtomicLong requested = new AtomicLong();

        FlatMapPublisherSubscriber(Subscriber<? super O> downstream,
                Function<? super I, ? extends Publisher<? extends O>> mapper) {
            this.downstream = downstream;
            this.mapper = mapper;
            this.firstUpstream = new AtomicReference<>();
            this.secondUpstream = new AtomicReference<>();
        }

        @Override
        public void onNext(O item) {
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable failure) {
            downstream.onError(failure);
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
        }

        @Override
        public void request(long n) {
            Subscriptions.requestIfNotNullOrAccumulate(secondUpstream, requested, n);
        }

        @Override
        public void cancel() {
            UniSubscription subscription = firstUpstream.getAndSet(EmptyUniSubscription.CANCELLED);
            if (subscription != null && subscription != EmptyUniSubscription.CANCELLED) {
                subscription.cancel();
            }
            Subscriptions.cancel(secondUpstream);
        }

        /**
         * Called when we get the subscription from the upstream UNI
         *
         * @param subscription the subscription allowing to cancel the computation.
         */
        @Override
        public void onSubscribe(UniSubscription subscription) {
            if (firstUpstream.compareAndSet(null, subscription)) {
                downstream.onSubscribe(this);
            }
        }

        /**
         * Called after we produced the {@link Publisher} and subscribe on it.
         *
         * @param subscription the subscription from the produced {@link Publisher}
         */
        @Override
        public void onSubscribe(Subscription subscription) {
            if (secondUpstream.compareAndSet(null, subscription)) {
                long r = requested.getAndSet(0L);
                if (r != 0L) {
                    subscription.request(r);
                }
            }
        }

        @Override
        public void onItem(I item) {
            Publisher<? extends O> publisher;

            try {
                publisher = mapper.apply(item);
                if (publisher == null) {
                    throw new NullPointerException(MAPPER_RETURNED_NULL);
                }
            } catch (Throwable ex) {
                downstream.onError(ex);
                return;
            }

            publisher.subscribe(this);
        }

        @Override
        public void onFailure(Throwable failure) {
            downstream.onError(failure);
        }
    }
}
