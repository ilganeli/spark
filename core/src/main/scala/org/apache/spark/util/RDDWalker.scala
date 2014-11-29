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

// Created by Ilya Ganelin

package org.apache.spark.util

import java.util
import org.apache.spark.rdd.RDD
import scala.language.existentials

/**
 * This class allows execution of a function on an RDD and all of its dependencies. This is 
 * accomplished by walking the object graph linking these RDDs. This is useful for debugging 
 * internal RDD references.
 */
object RDDWalker {
  
  // Keep track of both the RDD and its depth in the traversal graph.
  val walkQueue = new util.ArrayDeque[(RDD[_], Int)]
  var visited = new util.HashSet[RDD[_]]
  

  /**
   *
   * Execute the passed function on the underlying RDD
   * @param rddToWalk - The RDD to traverse along with its dependencies
   * @param func - The function to execute on each node. Returns a string 
   * @return Array[String] - An array of results generated by the traversal function
   * TODO Can there be cycles in RDD dependencies?
   */
  def walk(rddToWalk : RDD[_], func : (RDD[_])=>String): util.ArrayList[String] ={
    
    val results = new util.ArrayList[String]
    // Implement as a queue to perform a BFS  
    walkQueue.addFirst(rddToWalk,0)

    while(!walkQueue.isEmpty){
      // Pop from the queue 
      val (rddToProcess : RDD[_], depth:Int) = walkQueue.pollFirst()
      if(!visited.contains(rddToProcess)){
        visited.add(rddToProcess)
        rddToProcess.dependencies.foreach(s => walkQueue.addFirst(s.rdd, depth + 1))
        results.add("Depth " + depth + ": " + func(rddToProcess))
      }
    }
    
    results
  }
  
  
}
