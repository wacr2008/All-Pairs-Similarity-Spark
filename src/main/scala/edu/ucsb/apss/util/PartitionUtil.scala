package edu.ucsb.apss.util

import org.apache.spark.mllib.linalg.SparseVector

/**
  * Created by dimberman on 4/10/16.
  */
object PartitionUtil extends Serializable {
    def l1Norm(v: SparseVector) = {
        v.values.map(math.abs).sum
    }


    def lInfNorm(v: SparseVector) = {
        v.values.map(math.abs).max
    }

    def normalizer(v: SparseVector) = {
        val a = v.values.map(a => a * a).sum
        math.sqrt(a)
    }

    def dotProduct(v1: SparseVector, v2: SparseVector): Double = {
        val v1Map = v1.indices.zip(v1.values).toMap
        var sum = 0.0
        var num = 0
        for (i <- v2.indices.indices) {
            if (v1Map.contains(v2.indices(i))) {
                sum += v1Map(v2.indices(i)) * v2.values(i)
            }
        }


        val anorm = math.sqrt(v1.values.map(x => x * x).sum)
        val bnorm = math.sqrt(v2.values.map(x => x * x).sum)

        val answer = sum / (anorm * bnorm)
        //        val answer =  sum/(math.sqrt(anorm)* math.sqrt(bnorm))


//        println(s"ideal similarity: $answer")
        //        sum /(normalizer(v1)*normalizer(v2))
        answer
    }

    def normalizeVector(vec: SparseVector): SparseVector = {
        val norm = normalizer(vec)
        for (i <- vec.values.indices) vec.values(i) = vec.values(i) / norm
        new SparseVector(vec.size, vec.indices, vec.values)

    }




    def extractUsefulInfo(v: VectorWithIndex): VectorWithNorms = {
        val vec = v.vec
        new VectorWithNorms(lInfNorm(vec), l1Norm(vec), normalizer(vec), vec, v.index)
    }

    def truncateAt(n: Double, p: Int): Double = {
        val s = math pow(10, p)
        (math floor n * s) / s
    }


}

case class VectorWithIndex(vec: SparseVector, index: Long)
