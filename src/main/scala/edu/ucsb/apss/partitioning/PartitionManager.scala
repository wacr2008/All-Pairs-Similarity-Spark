package edu.ucsb.apss.partitioning

import java.io.FileOutputStream

import edu.ucsb.apss.util.PartitionUtil.VectorWithNorms
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkEnv, TaskContext, SerializableWritable, SparkContext}
import org.apache.spark.broadcast.Broadcast

/**
  * Created by dimberman on 4/14/16.
  */
class PartitionManager extends Serializable {

    def assignFixed(startingIndex: Int, neededVecs: List[(Int, Int)], sums: Array[Int]): List[(Int, Int)] = {
        val numberOfNeeded = neededVecs.length
        numberOfNeeded % 2 match {
            case 1 =>
                val proposedRange = List.range(startingIndex + 1, (startingIndex + 1) + (numberOfNeeded - 1) / 2) :+ startingIndex
                val modded = proposedRange.map(a => a % numberOfNeeded).toSet
                val pairs = neededVecs.zipWithIndex.filter(a => modded.contains(a._2)).map(_._1)
                pairs
            case 0 =>
                if (startingIndex < numberOfNeeded / 2) {
                    val e = (List.range(startingIndex + 1, (startingIndex + 1) + numberOfNeeded / 2).map(_ % numberOfNeeded) :+ startingIndex).toSet
                    val pairs = neededVecs.zipWithIndex.filter(a => e.contains(a._2)).map(_._1)
                    pairs
                }
                else {
                    val x = (startingIndex + 1) + numberOfNeeded / 2 - 1
                    val e = (List.range(startingIndex + 1, x).map(_ % numberOfNeeded) :+ startingIndex).toSet
                    val pairs = neededVecs.zipWithIndex.filter(a => e.contains(a._2)).map(_._1)
                    pairs
                }
        }

    }

    def readPartition(key: (Int, Int), id: String, broadcastedConf: Broadcast[SerializableWritable[Configuration]], taskContext: TaskContext): Iterator[VectorWithNorms] = {
        val partitionFile = s"/tmp/$id/" + PartitionHasher.partitionHash(key)
        readFile(new Path(partitionFile), broadcastedConf, taskContext)
        //         List[VectorWithNorms]().toIterator
    }

    def readFile[T](path: Path, broadcastedConf: Broadcast[SerializableWritable[Configuration]], context: TaskContext) = {
        val env = SparkEnv.get
        val fs = path.getFileSystem(broadcastedConf.value.value)
        val bufferSize = env.conf.getInt("spark.buffer.size", 65536)
        val fileInputStream = fs.open(path, bufferSize)
        val serializer = env.serializer.newInstance()

        val deserializeStream = serializer.deserializeStream(fileInputStream)
        //        context.addTaskCompletionListener(context => deserializeStream.close())
        deserializeStream.asIterator.asInstanceOf[Iterator[T]]
    }

    def getSums(i: Int): Array[Int] = {
        val ret = new Array[Int](i + 1)
        ret(0) = 1
        for (j <- 1 to i) {
            ret(j) = ret(j - 1) + j + 1
        }
        ret
    }


    def writePartitionsToFile(r: RDD[((Int, Int), VectorWithNorms)]) = {
        val x = r.groupByKey()
        val id = r.context.applicationId
        val BVConf = r.context.broadcast(new SerializableWritable(r.context.hadoopConfiguration))
        x.foreach { case (k, v) =>
            writeFile(k, v, id,BVConf)
        1
        }
        val y = x.collect

    }

    def writePartitionListsToFile(r: RDD[((Int, Int), List[List[VectorWithNorms]])]) = {
        val x = r.groupByKey()
        val id = r.context.applicationId
        val BVConf = r.context.broadcast(new SerializableWritable(r.context.hadoopConfiguration))
        x.foreach { case (k, v) =>
            writeFile(k, v, id,BVConf)
            1
        }
        val y = x.collect

    }



    def assignByBucket(bucket: Int, tiedLeader:Int, numBuckets:Int): List[(Int, Int)] = {
        numBuckets % 2 match {
            case 1 =>
                val proposedBuckets = List.range(bucket + 1, (bucket + 1) + (numBuckets - 1) / 2) :+ bucket
                val modded = proposedBuckets.map(a => a % numBuckets)
                modded.flatMap(b =>{
                    val candidates = List.range(0,b+1).map(x => (b,x))
                    val answer =   candidates.filter(isCandidate((bucket,tiedLeader),_))
                    answer
                }

                )
            case 0 =>
                if (bucket < numBuckets / 2) {
                    val e = List.range(bucket + 1, (bucket + 1) + numBuckets / 2).map(_ % numBuckets) :+ bucket
                    e.flatMap(b =>{
                        val answer = List.range(0,b).map(x => (b,x)).filter(isCandidate((bucket,tiedLeader),_))
                        answer
                    })
                }
                else {
                    val x = (bucket + 1) + numBuckets / 2 - 1
                    val e = List.range(bucket + 1, x).map(_ % numBuckets) :+ bucket
                    e.flatMap(b =>{
                        val answer = List.range(0,b).map(x => (b,x)).filter(isCandidate((bucket,tiedLeader),_))
                        answer
                    })
                }
        }

    }

    def writeFile[T](key: (Int, Int), f: Iterable[T], id:String, BVConf:Broadcast[SerializableWritable[Configuration]]) = {
        val partitionFile = s"/tmp/$id/" + PartitionHasher.partitionHash(key)
        val path = new Path(partitionFile)
        val fs = path.getFileSystem(BVConf.value.value)

        val env = SparkEnv.get
        val bufferSize = env.conf.getInt("spark.buffer.size", 65536)
        if(fs.exists(path)){
            val output = fs.append(path, bufferSize)
//            println(s"writing vector to file $id: " + f)
            val serialized = env.serializer.newInstance().serializeStream(output)
            serialized.writeAll(f.toIterator)
            output.close()
        }
        else{
            val output = fs.create(path, false, bufferSize)
//            println(s"writing vector to file $id: " + f)
            val serialized = env.serializer.newInstance().serializeStream(output)
            serialized.writeAll(f.toIterator)
            output.close()
        }

    }


    def isCandidate(a: (Int, Int), b: (Int, Int)): Boolean = {
        if (a._1 == a._2 || b._1 == b._2) true
        else if ((a._2 >= b._1 && a._1 >= b._1) || (b._2 >= a._1 && b._1 >= a._1)) false
        else true
    }


}