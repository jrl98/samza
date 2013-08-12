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

package org.apache.samza.metrics.reporter

import org.junit.Assert._
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import scala.collection.JavaConversions._
import org.apache.samza.task.TaskContext
import javax.management.remote.JMXConnectorFactory
import org.apache.samza.metrics.MetricsRegistryMap
import javax.management.remote.JMXConnectorServerFactory
import javax.management.remote.JMXConnectorServer
import java.rmi.registry.LocateRegistry
import javax.management.remote.JMXServiceURL
import org.apache.samza.config.MapConfig
import java.lang.management.ManagementFactory
import org.apache.samza.Partition
import javax.management.ObjectName
import org.apache.samza.metrics.JvmMetrics

object TestJmxReporter {
  val port = 4500
  val url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:%d/jmxapitestrmi" format port)
  var cs: JMXConnectorServer = null

  @BeforeClass
  def beforeSetupServers {
    LocateRegistry.createRegistry(4500)
    val mbs = ManagementFactory.getPlatformMBeanServer()
    cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs)
    cs.start
  }

  @AfterClass
  def afterCleanLogDirs {
    if (cs != null) {
      cs.stop
    }
  }
}

class TestJmxReporter {
  import TestJmxReporter.url

  @Test
  def testJmxReporter {
    val registry = new MetricsRegistryMap
    val jvm = new JvmMetrics("test", registry)
    val reporter = new JmxReporterFactory().getMetricsReporter("", "", new MapConfig(Map[String, String]()))

    reporter.register("test", registry)
    reporter.start
    jvm.run

    val mbserver = JMXConnectorFactory.connect(url).getMBeanServerConnection
    val stateViaJMX = mbserver.getAttribute(new ObjectName("test:type=test,name=MemNonHeapUsedM"), "Value").asInstanceOf[Float]

    assertTrue(stateViaJMX > 0)

    reporter.stop
  }
}
