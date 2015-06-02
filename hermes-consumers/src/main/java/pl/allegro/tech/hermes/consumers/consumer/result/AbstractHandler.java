package pl.allegro.tech.hermes.consumers.consumer.result;

import org.joda.time.Duration;
import pl.allegro.tech.hermes.api.Subscription;
import pl.allegro.tech.hermes.common.metric.HermesMetrics;
import pl.allegro.tech.hermes.consumers.consumer.offset.SubscriptionOffsetCommitQueues;
import pl.allegro.tech.hermes.consumers.consumer.receiver.Message;

public abstract class AbstractHandler {
    protected SubscriptionOffsetCommitQueues offsetHelper;
    protected HermesMetrics hermesMetrics;

    public AbstractHandler(SubscriptionOffsetCommitQueues offsetHelper, HermesMetrics hermesMetrics) {
        this.offsetHelper = offsetHelper;
        this.hermesMetrics = hermesMetrics;
    }

    protected void updateMetrics(String counterToUpdate, Message message, Subscription subscription) {
        hermesMetrics.counter(counterToUpdate, subscription.getTopicName(), subscription.getName()).inc();
        hermesMetrics.decrementInflightCounter(subscription);

        if (message.getPublishingTimestamp().isPresent()) {
            long timeLag = calculateTimeLagInSeconds(message.getPublishingTimestamp().get());
            hermesMetrics.histogramForOffsetTimeLag(subscription, message.getPartition()).update(timeLag);
        }
    }

    private long calculateTimeLagInSeconds(Long messageTimestamp) {
        return new Duration(messageTimestamp, System.currentTimeMillis()).getStandardSeconds();
    }

}
