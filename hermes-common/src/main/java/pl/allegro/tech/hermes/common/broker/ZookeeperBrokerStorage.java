package pl.allegro.tech.hermes.common.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import kafka.common.TopicAndPartition;
import kafka.utils.ZkUtils;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.allegro.tech.hermes.common.di.CuratorType;
import pl.allegro.tech.hermes.common.exception.BrokerInfoNotAvailableException;
import pl.allegro.tech.hermes.common.exception.BrokerNotFoundForPartitionException;
import pl.allegro.tech.hermes.common.exception.PartitionsNotFoundForGivenTopicException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Lists.transform;

public class ZookeeperBrokerStorage implements BrokerStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperBrokerStorage.class);

    private static final String PARTITIONS = "/brokers/topics/%s/partitions";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper objectMapper;

    @Inject
    public ZookeeperBrokerStorage(@Named(CuratorType.KAFKA) CuratorFramework curatorFramework, ObjectMapper objectMapper) {
        this.curatorFramework = curatorFramework;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int readLeaderForPartition(TopicAndPartition topicAndPartition) {
        try {
            byte[] data = curatorFramework.getData().forPath(getTopicPartitionLeaderPath(topicAndPartition));
            Map<String, Object> values = objectMapper.readValue(data, Map.class);
            return (Integer) values.get("leader");
        } catch (Exception exception) {
            throw new BrokerNotFoundForPartitionException(topicAndPartition.topic(), topicAndPartition.partition(), exception);
        }
    }

    @Override
    public Multimap<Integer, TopicAndPartition> readLeadersForPartitions(Set<TopicAndPartition> topicAndPartitionSet) {
        Multimap<Integer, TopicAndPartition> leadersForPartitions = ArrayListMultimap.create();
        for (TopicAndPartition topicAndPartition : topicAndPartitionSet) {
            try {
                Integer leaderId = leaderIdForPartition(topicAndPartition);
                leadersForPartitions.put(leaderId, topicAndPartition);
            } catch (BrokerNotFoundForPartitionException ex) {
                LOGGER.warn("Broker not found", ex);
            }
        }
        return leadersForPartitions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BrokerDetails readBrokerDetails(Integer brokerId) {
        try {
            byte[] data = curatorFramework.getData().forPath(getBrokerDetailsPath(brokerId));
            Map<String, Object> values = objectMapper.readValue(data, Map.class);
            return new BrokerDetails((String) values.get("host"), (Integer) values.get("port"));
        } catch (Exception exception) {
            throw new BrokerInfoNotAvailableException(brokerId, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Integer leaderIdForPartition(TopicAndPartition topicAndPartition) {
        try {
            byte[] data = curatorFramework.getData().forPath(getTopicPartitionLeaderPath(topicAndPartition));
            Map<String, Object> values = objectMapper.readValue(data, Map.class);
            return (Integer) values.get("leader");
        } catch (Exception exception) {
            throw new BrokerNotFoundForPartitionException(topicAndPartition.topic(), topicAndPartition.partition(), exception);
        }
    }

    @Override
    public List<Integer> readPartitionsIds(String topicName) {
        try {
            List<String> partitionsAsStrings = curatorFramework.getChildren().forPath(String.format(PARTITIONS, topicName));
            List<Integer> partitions = transform(partitionsAsStrings, Ints.stringConverter());

            return Ordering.natural().sortedCopy(partitions);
        } catch (Exception exception) {
            throw new PartitionsNotFoundForGivenTopicException(topicName, exception);
        }
    }

    @VisibleForTesting
    protected String getTopicPartitionLeaderPath(TopicAndPartition topicAndPartition) {
        return ZkUtils.getTopicPartitionLeaderAndIsrPath(topicAndPartition.topic(), topicAndPartition.partition());
    }

    @VisibleForTesting
    protected String getBrokerDetailsPath(int brokerId) {
        return ZkUtils.BrokerIdsPath() + "/" + brokerId;
    }
}
