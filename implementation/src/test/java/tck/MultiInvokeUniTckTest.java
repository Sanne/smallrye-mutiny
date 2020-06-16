package tck;

import java.util.stream.LongStream;

import org.reactivestreams.Publisher;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

public class MultiInvokeUniTckTest extends AbstractPublisherTck<Long> {

    @Override
    public Publisher<Long> createPublisher(long elements) {
        return Multi.createFrom().items(LongStream.rangeClosed(1, elements).boxed())
                .onItem().invokeUni(x -> Uni.createFrom().nullItem());
    }

    @Override
    public Publisher<Long> createFailedPublisher() {
        return Multi.createFrom().<Long> failure(new RuntimeException("failed"))
                .onItem().invokeUni(x -> Uni.createFrom().nullItem());
    }
}
