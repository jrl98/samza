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

import java.util.Map
import java.util.HashMap
import scala.reflect.BeanProperty

object MetricsSnapshot {
  def fromMap(map: Map[String, Map[String, Object]]) = {
    val header = MetricsHeader.fromMap(map.get("header"))
    val metrics = Metrics.fromMap(map.get("metrics").asInstanceOf[Map[String, Map[String, Object]]])
    new MetricsSnapshot(header, metrics)
  }
}

class MetricsSnapshot(@BeanProperty val header: MetricsHeader, @BeanProperty val metrics: Metrics) {
  def getAsMap(): Map[String, Object] = {
    val map = new HashMap[String, Object]

    map.put("header", header.getAsMap)
    map.put("metrics", metrics.getAsMap)

    map
  }
}
