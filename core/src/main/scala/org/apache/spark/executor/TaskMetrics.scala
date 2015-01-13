/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.executor

import scala.collection.mutable.ArrayBuffer

import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.storage.{BlockId, BlockStatus}

/**
 * :: DeveloperApi ::
 * Metrics tracked during the execution of a task.
 *
 * This class is used to house metrics both for in-progress and completed tasks. In executors,
 * both the task thread and the heartbeat thread write to the TaskMetrics. The heartbeat thread
 * reads it to send in-progress metrics, and the task thread reads it to send metrics along with
 * the completed task.
 *
 * So, when adding new fields, take into consideration that the whole object can be serialized for
 * shipping off at any time to consumers of the SparkListener interface.
 */
@DeveloperApi
class TaskMetrics extends Serializable {
  /**
   * Host's name the task runs on
   */
  var hostname: String = _

  /**
   * Time taken on the executor to deserialize this task
   */
  var executorDeserializeTime: Long = _

  /**
   * Time the executor spends actually running the task (including fetching shuffle data)
   */
  var executorRunTime: Long = _

  /**
   * The number of bytes this task transmitted back to the driver as the TaskResult
   */
  var resultSize: Long = _

  /**
   * Amount of time the JVM spent in garbage collection while executing this task
   */
  var jvmGCTime: Long = _

  /**
   * Amount of time spent serializing the task result
   */
  var resultSerializationTime: Long = _

  /**
   * The number of in-memory bytes spilled by this task
   */
  var memoryBytesSpilled: Long = _

  /**
   * The number of on-disk bytes spilled by this task
   */
  var diskBytesSpilled: Long = _

  /**
   * If this task reads from a HadoopRDD or from persisted data, metrics on how much data was read
   * are stored here.
   */
  var inputMetrics: Option[InputMetrics] = None

  /**
   * If this task writes data externally (e.g. to a distributed filesystem), metrics on how much
   * data was written are stored here.
   */
  var outputMetrics: Option[OutputMetrics] = None

  /**
   * If this task reads from shuffle output, metrics on getting shuffle data will be collected here.
   * This includes read metrics aggregated over all the task's shuffle dependencies.
   */
  private var _shuffleReadMetrics: Option[ShuffleReadMetrics] = None

  def shuffleReadMetrics = _shuffleReadMetrics

  /**
   * This should only be used when recreating TaskMetrics, not when updating read metrics in
   * executors.
   */
  private[spark] def setShuffleReadMetrics(shuffleReadMetrics: Option[ShuffleReadMetrics]) {
    _shuffleReadMetrics = shuffleReadMetrics
  }

  /**
   * ShuffleReadMetrics per dependency for collecting independently while task is in progress.
   */
  @transient private lazy val depsShuffleReadMetrics: ArrayBuffer[ShuffleReadMetrics] =
    new ArrayBuffer[ShuffleReadMetrics]()

  /**
   * If this task writes to shuffle output, metrics on the written shuffle data will be collected
   * here
   */
  var shuffleWriteMetrics: Option[ShuffleWriteMetrics] = None

  /**
   * Storage statuses of any blocks that have been updated as a result of this task.
   */
  var updatedBlocks: Option[Seq[(BlockId, BlockStatus)]] = None

  /**
   * A task may have multiple shuffle readers for multiple dependencies. To avoid synchronization
   * issues from readers in different threads, in-progress tasks use a ShuffleReadMetrics for each
   * dependency, and merge these metrics before reporting them to the driver. This method returns
   * a ShuffleReadMetrics for a dependency and registers it for merging later.
   */
  private [spark] def createShuffleReadMetricsForDependency(): ShuffleReadMetrics = synchronized {
    val readMetrics = new ShuffleReadMetrics()
    depsShuffleReadMetrics += readMetrics
    readMetrics
  }

  /**
   * Aggregates shuffle read metrics for all registered dependencies into shuffleReadMetrics.
   */
  private[spark] def updateShuffleReadMetrics() = synchronized {
    val merged = new ShuffleReadMetrics()
    for (depMetrics <- depsShuffleReadMetrics) {
      merged.fetchWaitTime += depMetrics.fetchWaitTime
      merged.localBlocksFetched += depMetrics.localBlocksFetched
      merged.remoteBlocksFetched += depMetrics.remoteBlocksFetched
      merged.remoteBytesRead += depMetrics.remoteBytesRead
    }
    _shuffleReadMetrics = Some(merged)
  }
}

private[spark] object TaskMetrics {
  def empty: TaskMetrics = new TaskMetrics
}

/**
 * :: DeveloperApi ::
 * Method by which input data was read.  Network means that the data was read over the network
 * from a remote block manager (which may have stored the data on-disk or in-memory).
 */
@DeveloperApi
object DataReadMethod extends Enumeration with Serializable {
  type DataReadMethod = Value
  val Memory, Disk, Hadoop, Network = Value
}

/**
 * :: DeveloperApi ::
 * Method by which output data was written.
 */
@DeveloperApi
object DataWriteMethod extends Enumeration with Serializable {
  type DataWriteMethod = Value
  val Hadoop = Value
}

/**
 * :: DeveloperApi ::
 * Metrics about reading input data.
 */
@DeveloperApi
case class InputMetrics(readMethod: DataReadMethod.Value) {
  /**
   * Total bytes read.
   */
  private var _bytesRead: Long = _
  def bytesRead = _bytesRead
  def incBytesRead(value: Long) = _bytesRead += value
  def decBytesRead(value: Long) = _bytesRead -= value
}

/**
 * :: DeveloperApi ::
 * Metrics about writing output data.
 */
@DeveloperApi
case class OutputMetrics(writeMethod: DataWriteMethod.Value) {
  /**
   * Total bytes written
   */
  private var _bytesWritten: Long = _
  def bytesWritten = _bytesWritten
  def incBytesWritten(value : Long) = _bytesWritten += value
  def decBytesWritten(value : Long) = _bytesWritten -= value
}

/**
 * :: DeveloperApi ::
 * Metrics pertaining to shuffle data read in a given task.
 */
@DeveloperApi
class ShuffleReadMetrics extends Serializable {
  /**
   * Number of remote blocks fetched in this shuffle by this task
   */
  private var _remoteBlocksFetched: Int = _
  def remoteBlocksFetched = _remoteBlocksFetched
  def incRemoteBlocksFetched(value: Int) = _remoteBlocksFetched += value
  def defRemoteBlocksFetched(value: Int) = _remoteBlocksFetched -= value
  
  /**
   * Number of local blocks fetched in this shuffle by this task
   */
  private var _localBlocksFetched: Int = _
  def localBlocksFetched = _localBlocksFetched
  def incLocalBlocksFetched(value: Int) = _localBlocksFetched += value
  def defLocalBlocksFetched(value: Int) = _localBlocksFetched -= value


  /**
   * Time the task spent waiting for remote shuffle blocks. This only includes the time
   * blocking on shuffle input data. For instance if block B is being fetched while the task is
   * still not finished processing block A, it is not considered to be blocking on block B.
   */
  private var _fetchWaitTime: Long = _
  def fetchWaitTime = _fetchWaitTime
  def incFetchWaitTime(value: Long) = _fetchWaitTime += value
  def decFetchWaitTime(value: Long) = _fetchWaitTime -= value
  
  /**
   * Total number of remote bytes read from the shuffle by this task
   */
  private var _remoteBytesRead: Long = _
  def remoteBytesRead = _remoteBytesRead
  def incRemoteBytesRead(value: Long) = _remoteBytesRead += value
  def decRemoteBytesRead(value: Long) = _remoteBytesRead -= value

  /**
   * Number of blocks fetched in this shuffle by this task (remote or local)
   */
  private var _totalBlocksFetched: Int = remoteBlocksFetched + localBlocksFetched
  def totalBlocksFetched = _totalBlocksFetched
  def incTotalBlocksFetched(value: Int) = _totalBlocksFetched += value
  def decTotalBlocksFetched(value: Int) = _totalBlocksFetched -= value
}

/**
 * :: DeveloperApi ::
 * Metrics pertaining to shuffle data written in a given task.
 */
@DeveloperApi
class ShuffleWriteMetrics extends Serializable {
  /**
   * Number of bytes written for the shuffle by this task
   */
  @volatile private var _shuffleBytesWritten: Long = _
  def incShuffleBytesWritten(value: Long) = _shuffleBytesWritten += value
  def decShuffleBytesWritten(value: Long) = _shuffleBytesWritten -= value
  def shuffleBytesWritten = _shuffleBytesWritten
  /**
   * Time the task spent blocking on writes to disk or buffer cache, in nanoseconds
   */
  @volatile private var _shuffleWriteTime: Long = _
  def incShuffleWriteTime(value: Long) = _shuffleWriteTime += value
  def decShuffleWriteTime(value: Long) = _shuffleWriteTime -= value
  def shuffleWriteTime= _shuffleWriteTime

}
