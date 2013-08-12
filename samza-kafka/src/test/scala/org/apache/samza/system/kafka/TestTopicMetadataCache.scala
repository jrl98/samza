/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.samza.system.kafka
import org.junit.Test
import kafka.api.TopicMetadata
import org.apache.samza.util.TopicMetadataStore
import org.I0Itec.zkclient.ZkClient
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import java.util.concurrent.CountDownLatch
import org.apache.samza.util.Clock

class TestTopicMetadataCache {

  object MockTime extends Clock {
    var currentValue = 0

    def currentTimeMillis: Long = currentValue
  }

  class MockTopicMetadataStore extends TopicMetadataStore {
    var mockCache = Map(
      "topic1" -> new TopicMetadata("topic1", List.empty, 0),
      "topic2" -> new TopicMetadata("topic2", List.empty, 0))
    var numberOfCalls: AtomicInteger = new AtomicInteger(0)

    def getTopicInfo(topics: Set[String]) = {
      var topicMetadata = Map[String, TopicMetadata]()
      topics.foreach(topic => topicMetadata += topic -> mockCache(topic))
      numberOfCalls.getAndIncrement
      topicMetadata
    }
    /*
    def onTopicMissingMock(topic: String, zkClient: ZkClient) {
      mockCache += topic -> new TopicMetadata(topic, List.empty, 0)
    }
    */

    def setErrorCode(topic: String, errorCode: Short) {
      mockCache += topic -> new TopicMetadata(topic, List.empty, errorCode)
    }
    def resetNumOfCalls = numberOfCalls = new AtomicInteger(0)
  }

  val mockStore = new MockTopicMetadataStore()
  val waitForThreadStart = new CountDownLatch(3)

  @Test
  def testBasicMetadataCacheFunctionality {
    // Retrieve a topic from the cache. Initially cache is empty and store is queried to get the data
    mockStore.setErrorCode("topic1", 3)
    var metadata = TopicMetadataCache.getTopicMetadata(Set("topic1"), "kafka", mockStore.getTopicInfo, 5, () => MockTime.currentTimeMillis)
    assert(metadata("topic1").topic.equals("topic1"))
    assert(metadata("topic1").errorCode == 3)
    assert(mockStore.numberOfCalls.get() == 1)

    // Retrieve the same topic from the cache which has an error code. Ensure the store is called to refresh the cache
    MockTime.currentValue = 5
    mockStore.setErrorCode("topic1", 0)
    metadata = TopicMetadataCache.getTopicMetadata(Set("topic1"), "kafka", mockStore.getTopicInfo, 5, () => MockTime.currentTimeMillis)
    assert(metadata("topic1").topic.equals("topic1"))
    assert(metadata("topic1").errorCode == 0)
    assert(mockStore.numberOfCalls.get() == 2)

    // Retrieve the same topic from the cache with refresh rate greater than the last update. Ensure the store is not
    // called
    metadata = TopicMetadataCache.getTopicMetadata(Set("topic1"), "kafka", mockStore.getTopicInfo, 5, () => MockTime.currentTimeMillis)
    assert(metadata("topic1").topic.equals("topic1"))
    assert(metadata("topic1").errorCode == 0)
    assert(mockStore.numberOfCalls.get() == 2)

    // Ensure that refresh happens when refresh rate is less than the last update. Ensure the store is called
    MockTime.currentValue = 11
    metadata = TopicMetadataCache.getTopicMetadata(Set("topic1"), "kafka", mockStore.getTopicInfo, 5, () => MockTime.currentTimeMillis)
    assert(metadata("topic1").topic.equals("topic1"))
    assert(metadata("topic1").errorCode == 0)
    assert(mockStore.numberOfCalls.get() == 3)
  }

  @Test
  def testMultiThreadedInteractionForTopicMetadataCache {
    mockStore.resetNumOfCalls
    MockTime.currentValue = 17
    val numAssertionSuccess = new AtomicBoolean(true)
    // Add topic to the cache from multiple threads and ensure the store is called only once
    val threads = new Array[Thread](3)
    for (i <- 0 until 3) {
      threads(i) = new Thread(new Runnable {
        def run {
          waitForThreadStart.countDown()
          waitForThreadStart.await()
          val metadata = TopicMetadataCache.getTopicMetadata(Set("topic1"), "kafka", mockStore.getTopicInfo, 5, () => MockTime.currentTimeMillis)
          numAssertionSuccess.compareAndSet(true, metadata("topic1").topic.equals("topic1"))
          numAssertionSuccess.compareAndSet(true, metadata("topic1").errorCode == 0)
        }
      })
      threads(i).start()
    }
    for (i <- 0 until 3) {
      threads(i).join
    }
    assert(numAssertionSuccess.get() == true)
    assert(mockStore.numberOfCalls.get() == 1)
  }
}
