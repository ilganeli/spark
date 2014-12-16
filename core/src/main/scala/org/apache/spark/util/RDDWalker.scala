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

package org.apache.spark.util

import com.google.common.collect.{Queues, Sets}

import scala.collection.mutable.ArrayBuffer
import scala.language.existentials

import org.apache.spark.rdd.RDD

/**
 * This class permits traversing the RDD's dependency graph. This is 
 * accomplished by walking the object graph linking these RDDs. This is useful for debugging 
 * internal RDD references. See SPARK-3694.
 */
object RDDWalker {
  
  // Keep track of both the RDD and its depth in the traversal graph.
  val walkQueue = Queues.newArrayDeque[(RDD[_], Int)]
  var visited = Sets.newIdentityHashSet[RDD[_]]
  
  /**
   * Traverse the dependencies of the RDD and store them within an Array along with their depths.
   * Return this data structure and subsequently process it. 
   * 
   * @param rddToWalk - The RDD to traverse along with its dependencies
   * @return Array[(RDD[_], depth : Int] - An array of results generated by the traversal function
   */
  def walk(rddToWalk : RDD[_]): Array[(RDD[_], Int)] = {
    val results = new ArrayBuffer[(RDD[_], Int)]
    // Implement as a queue to perform a BFS  
    walkQueue.addFirst(rddToWalk,0)

    while (!walkQueue.isEmpty) {
      // Pop from the queue 
      val (rddToProcess : RDD[_], depth:Int) = walkQueue.pollFirst()
      if (!visited.contains(rddToProcess)) {
        visited.add(rddToProcess)
        rddToProcess.dependencies.foreach(s => walkQueue.addFirst(s.rdd, depth + 1))
        results.append((rddToProcess, depth))
      }
    }
    
    results.toArray
  }
}
