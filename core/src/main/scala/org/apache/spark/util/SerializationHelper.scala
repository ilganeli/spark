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

import java.io.NotSerializableException

import org.apache.spark.rdd.RDD
import org.apache.spark.serializer.{SerializerInstance, Serializer}

import scala.util.control.NonFatal

/**
 * This class is designed to encapsulate some utilities to facilitate debugging serialization 
 * problems
 */
object SerializationHelper {
    var Failed= "Failed to serialize"
    var FailedDeps = "Failed to serialize dependencies"
    var Serialized = "Serialized"
  
  /**
   * Helper function to help seperate a unserialiable parent rdd from unserializable dependencies 
   * @param closureSerializer - An instance of a serializer (single-threaded) that will be used
   * @param rdd - Rdd to attempt to serialize
   * @return - An output string qualifying success or failure.
   */
  def handleFailure(closureSerializer : SerializerInstance, 
                                         rdd: RDD[_]): String ={
    if(rdd.dependencies.length > 0){
      try{
        rdd.dependencies.foreach(dep => closureSerializer.serialize(dep : AnyRef))

        // By default, return a failure since we still failed to serialize the parent RDD
        // Now, however, we know that the dependencies are serializable
        Failed + ": " + rdd.toString
      }
      catch {
        // If instead, however, the dependencies ALSO fail to serialize then the subsequent stage
        // of evaluation will help identify which of the dependencies has failed 
        case e: NotSerializableException =>
          FailedDeps + ": " + rdd.toString

        case NonFatal(e) =>
          FailedDeps + ": " + rdd.toString
      }

    }
    else{
      Failed + ": " + rdd.toString
    }

  }

}

