import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.apache.spark.broadcast.Broadcast
import scala.math.{pow, sqrt, abs}
import java.io.File
import java.io.{PrintWriter, FileWriter}
import java.io.FileOutputStream
import scala.collection.mutable.ArrayBuffer
import java.nio.file.{Paths, Path, Files, StandardOpenOption}


object InterestingPoints extends Serializable {
    def main(args: Array[String]): Unit = {

      type PointPlusIndex = (Array[Double], Long)
      // example arguments
      // <TaskNumber> <filename> <kForTask3> <coresNumber>
      // "args": ["Task1", "normal_500_2.csv", "3", "\"*\""]
      // Task1 ./normal_2500_2.csv 3 *
      // because we run spark locally it is by default using 1 executor but coresNumber threads

      ////////////////////////////////////////////////////////////////////////// Definitions ////////////////////////////////////////////////////////////
      def calculateDominanceScores(
          notHavingDominanceScore: ArrayBuffer[(PointPlusIndex, Long)], 
          dataPoints: RDD[PointPlusIndex],
          currentOrigins: Array[Double]
        ): Unit = {
          // notHavingDominanceScore
          //   .filter(_._2 == -1L)
          //   .foreach { case (point, _) =>
          //     val dominanceScore = dataPoints
          //       .filter(otherPoint => isDominated(point, otherPoint))
          //       .count()

          //     // Update the tuple in notHavingDominanceScore with the calculated dominance score
          //     val index = notHavingDominanceScore.indexOf((point, -1L))
          //     if (index != -1) {
          //       notHavingDominanceScore(index) = (point, dominanceScore)
          //     }
          //   }
          // Calculate the dominance scores first without updating the original ArrayBuffer
          val updates = notHavingDominanceScore
            .filter(_._2 == -1L)
            .map { case (point, _) =>
              val dominanceScore = dataPoints
                .filter { case (coords, _) =>
                  coords.zip(currentOrigins).forall { case (coord, origin) => coord >= origin }
                }
                .filter(otherPoint => isDominated(otherPoint, point))
                .count()

              (point, dominanceScore)
            }

          // Update the ArrayBuffer with the new scores
          updates.foreach { case (point, dominanceScore) =>
            val index = notHavingDominanceScore.indexWhere {
              case (existingPoint, _) => existingPoint == point
            }
            if (index != -1) {
              notHavingDominanceScore(index) = (point, dominanceScore)
            }
          }
        }

      // using of Iterator --> lazy evaluation, single pass
      def skylineKnnSFS(points: Iterator[PointPlusIndex]): Iterator[PointPlusIndex] = {

        val sortedPoints = points.toList
          .map(calculateOriginL1Distance)
          .sortBy(_._1)
          .map(_._2)
        
        def filterDominated(points: List[PointPlusIndex], 
                            accum: List[PointPlusIndex] = Nil): List[PointPlusIndex] = points match {

          case Nil => accum.reverse
          case headPoint :: tailPoints => {
            val nonDominated = tailPoints.filterNot(isDominated(_,headPoint))
            filterDominated(nonDominated, headPoint +: accum)
          }
        }
        
        filterDominated(sortedPoints).iterator
      }


      def kDominatingSkyline(dataPoints: RDD[PointPlusIndex], topK: Int): ArrayBuffer[(PointPlusIndex, Long)] = {
        dataPoints.cache()

        val dimensions: Int = dataPoints.take(1)(0)._1.length
        
        // All Local Skyline Approach (ALS)
        val partialSkylinePoints = dataPoints.mapPartitions(skylineKnnSFS)

        val skylineDominanceScores = skylineKnnSFS(partialSkylinePoints.collect().iterator)
          .to(ArrayBuffer)
          .map(x => (x,-1L))
        
        // Initialize currentOrigins for calculateDominanceScores
        val currentOrigins: Array[Double] = Array.fill(dimensions)(0.0)

        // Calculate dominance scores
        calculateDominanceScores(skylineDominanceScores, dataPoints, currentOrigins)
        skylineDominanceScores.sortBy(_._2).reverse.take(topK)
      }


      def kDominatingSTD(sc: SparkContext, dataPoints: RDD[PointPlusIndex], topK: Int) : ArrayBuffer[(PointPlusIndex, Long)] = {
        val dimensions: Int = dataPoints.take(1)(0)._1.length
        
        dataPoints.cache()

        // All Local Skyline Approach (ALS)
        val partialSkylinePoints = dataPoints.mapPartitions(skylineKnnSFS)

        // -1 means that the dominance score for the particular point has not been calculated yet
        // Transform Array[PointPlusIndex] --> (Array[PointPlusIndex], -1)
        val potentialTopKDominatorsDominanceScores = skylineKnnSFS(partialSkylinePoints.collect().iterator)
          .toArray
          .map(x => (x,-1L))

        // initialize the potential Top K buffer to append new potential top K points
        var potentialTopKBuffer: ArrayBuffer[(PointPlusIndex, Long)] = ArrayBuffer(potentialTopKDominatorsDominanceScores: _*)

        
        // Initializations
        // Loop to accumulate top-K dominators
        // To add point --> topKDominators += (PointPlusIndex, Long)
        val topKDominators: ArrayBuffer[(PointPlusIndex, Long)] = ArrayBuffer[(PointPlusIndex, Long)]()
        var currentOrigins: Array[Double] = Array.fill(dimensions)(0.0)
        

        while (topKDominators.length < topK) {
          calculateDominanceScores(potentialTopKBuffer, dataPoints, currentOrigins)
          potentialTopKBuffer = potentialTopKBuffer.sortBy(_._2).reverse
          
          // Insert the top point to results
          val firstElement = potentialTopKBuffer.remove(0)
          topKDominators += firstElement
          
          // Update currentOrigins for the next iteration
          currentOrigins = firstElement._1._1

          // Calculate the points that firstElement dominates (Find the Exclusive Dominance Region)
          val dominatedArea = dataPoints.filter(p => isDominated(p, firstElement._1))

          // Calculate skyline for new Exclusive Dominance Region
          val partialEDRSkyline = dominatedArea.mapPartitions(skylineKnnSFS)
          val universalEDRSkyline = skylineKnnSFS(partialEDRSkyline.collect().iterator)
            .toArray
            .map(x => (x,-1L))
          
          // Append new skylines as potential top K points
          // check if any element of universalEDRSkyline is already in potentialTopKBuffer
          val filteredUniversalEDRSkyline = universalEDRSkyline.filter { element =>
            !potentialTopKBuffer.exists { bufferElement =>
              bufferElement._1._2 == element._1._2
            }
          }
          // apend those elements that are not already present in potentialTopKBuffer
          potentialTopKBuffer ++= filteredUniversalEDRSkyline

        }
        println(1)
        topKDominators
      }


      // L2 norm
      def calculateOriginL2Distance(point: PointPlusIndex) : (Double, PointPlusIndex) = {
        val distance = sqrt(point._1.map(coord => pow(coord, 2)).sum)
        (distance, point)
      }


      // L1 norm
      def calculateOriginL1Distance(point: PointPlusIndex) : (Double, PointPlusIndex) = {
        val distance = point._1.map(coord => abs(coord)).sum
        (distance, point)
      }


      def isDominated(point: PointPlusIndex, otherPoint: PointPlusIndex): Boolean = {
        val allCoordinatesLessOrEqual = point._1.zip(otherPoint._1).forall { case (a, b) => a >= b }
        val atLeastOneCoordinateStrictlyLess = point._1.zip(otherPoint._1).exists { case (a, b) => a > b }

        allCoordinatesLessOrEqual && atLeastOneCoordinateStrictlyLess
      }
      //////////////////////////////////// START //////////////////////////////////////////
      // input arguments
      val inputArgs = args.toList

      val conf = new SparkConf()
        .setAppName("Big-Data-2023")
        .setMaster(s"local[${inputArgs(3)}]")
        .set("spark.driver.extraJavaOptions", "--add-opens java.base/sun.nio.ch=ALL-UNNAMED")
        .set("spark.executor.extraJavaOptions", "--add-opens java.base/sun.nio.ch=ALL-UNNAMED")
        .set("spark.cores.max", "4")
        .set("spark.executor.memory", "6g")
        .set("spark.driver.memory", "4g")
      
      // spark context
      val sc = new SparkContext(conf)

      // load dataset using desiredPartitions
      val desiredPartitions = 4
      val dataset = sc.textFile(inputArgs(1), desiredPartitions) // iterable of strings=lines
      // val dataset = sc.textFile(inputArgs(1))
      // val numberOfPartitions = dataset.getNumPartitions
      // println(numberOfPartitions)

      // parse topK
      val topK = inputArgs(2).toInt

      // save results
      // val pathWithoutExtension = inputArgs(1).split("\\.").dropRight(1).mkString(".")
      // val resultsFilename = pathWithoutExtension + "_results.txt"

      // execution times
      // val execTimesFilename = inputArgs(1).split("\\.")(inputArgs(1).split("\\.").length - 2) + "_exec_times.txt"
      val execTimesFilename = inputArgs(4)
      val execTimesFile = new File(execTimesFilename)
      if (!execTimesFile.exists()) {
        val writer = new PrintWriter(execTimesFile)
        writer.close() // Create the file and close it immediately
        println(s"File '$execTimesFilename' has been created.")
      } else {
        println(s"File '$execTimesFilename' already exists.")
      }

      // Obtain information from filename
      val path: Path = Paths.get(inputArgs(1))
      val fileName: Path = path.getFileName()
      val fileInfo = fileName.toString.stripSuffix(".csv").split("_")

      val distribution = fileInfo(0)
      val size = fileInfo(1)
      val dimensions = fileInfo(2)

      // Obtain current cores
      val executorsNum = inputArgs(3)

      // Obtain Task
      val taskID = inputArgs(0)

      // val writer = new PrintWriter(new File(resultsFilename))

      // read rdd and conduct some general modifications
      // iterable of strings --> iterable of Arrays with strings --> iterable of Arrays with Doubles
      // for debugging purpose you can print first 5 elements using dataPoints.take(5).foreach(point => println(point.mkString(", ")))
      val dataPoints = dataset
      .map(_.split(","))
      .map(_.map(_.toDouble))
      .zipWithIndex()

      // Start calculating time
      val timeStart = System.nanoTime

      if (inputArgs(0) == "Task1") {
        println("I am in Task 1 --> Calculating Skyline")
        // All Local Skyline Approach (ALS)
        val partialSkylinePoints = dataPoints.mapPartitions(skylineKnnSFS)
        // val universalSkylinePoints = skylineKnnSFS(partialSkylinePoints.collect().iterator).toList
        // .map(x => s"${x._2}   ${x._1.mkString(", ")}")
        val universalSkylinePoints = skylineKnnSFS(partialSkylinePoints.collect().iterator)
        universalSkylinePoints.take(5).foreach(println)
        println(universalSkylinePoints.length)
      }
      else if (inputArgs(0) == "Task2") {
        println(s"I am in Task 2 --> Returning ${inputArgs(2)} points with the highest dominance score")

        // Skyline Based Top k-dominating Algorithm (STD)
        val topKDominating = kDominatingSTD(sc, dataPoints, topK)
        .take(5)
        .foreach {case ((coordinates, index), dominanceScore) => 
                  println(s"Index: $index, Coordinates: ${coordinates.mkString(", ")}, Dominance Score: $dominanceScore")
                  }

        println(1)

      }
      else {
        println(s"I am in Task 3 --> Returning ${inputArgs(2)} points from the skyline with the highest dominance score")
        val topKDominatingSkyline = kDominatingSkyline(dataPoints, topK)
          .take(5)
          .foreach {case ((coordinates, index), dominanceScore) => 
                    println(s"Index: $index, Coordinates: ${coordinates.mkString(", ")}, Dominance Score: $dominanceScore")
                    }
        println(1)
      }

      // save execution times
      val timeNeeded = (System.nanoTime() - timeStart) / 1e9d

      println(s"$timeNeeded seconds elapsed.")

      val rowToAppend = s"$taskID,$distribution,$size,$dimensions,$executorsNum,$timeNeeded\n"

      // Append the new row to the file
      val fileWriter = new FileWriter(execTimesFilename, true)  // Open in append mode
      val printWriter = new PrintWriter(fileWriter)

      printWriter.append(rowToAppend)
      printWriter.close()


      print(1)
      ///////////////////////////////////////////////////////////////////////////////////////////
      // val dataset = sc.textFile("normal_2500_2.csv")
      // val test_rdd = dataset.map(_.split(","))
      // // rdd of tuples
      // val dataPoints = dataset.map {
      //   s => 
      //     val parts = s.split(",")
      //     (parts(0).toDouble, parts(1).toDouble)
      // }

      // dataPoints.take(5).foreach(println)


      // println("Hello World")
    }
}