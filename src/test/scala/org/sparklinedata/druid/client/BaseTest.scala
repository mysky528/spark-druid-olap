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

package org.sparklinedata.druid.client

import java.util.TimeZone

import org.apache.spark.Logging
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row
import org.apache.spark.sql.hive.test.sparklinedata.TestHive
import org.apache.spark.sql.hive.test.sparklinedata.TestHive._
import org.apache.spark.sql.sources.druid.DruidPlanner
import org.scalatest.{BeforeAndAfterAll, fixture}
import org.sparklinedata.druid.Utils
import org.sparklinedata.spark.dateTime.Functions._

abstract class BaseTest extends fixture.FunSuite with
  fixture.TestDataFixture with BeforeAndAfterAll with Logging {

  val colMapping =
    """{
      | "l_quantity" : "sum_l_quantity",
      | "ps_availqty" : "sum_ps_availqty",
      | "cn_name" : "c_nation",
      | "cr_name" : "c_region",
      |  "sn_name" : "s_nation",
      | "sr_name" : "s_region"
      |}
    """.stripMargin.replace('\n', ' ')

  val functionalDependencies =
    """[
      |  {"col1" : "c_name", "col2" : "c_address", "type" : "1-1"},
      |  {"col1" : "c_phone", "col2" : "c_address", "type" : "1-1"},
      |  {"col1" : "c_name", "col2" : "c_mktsegment", "type" : "n-1"},
      |  {"col1" : "c_name", "col2" : "c_comment", "type" : "1-1"},
      |  {"col1" : "c_name", "col2" : "c_nation", "type" : "n-1"},
      |  {"col1" : "c_nation", "col2" : "c_region", "type" : "n-1"}
      |]
    """.stripMargin.replace('\n', ' ')

  val flatStarSchema =
    """
      |{
      |  "factTable" : "orderLineItemPartSupplier",
      |  "relations" : []
      | }
    """.stripMargin.replace('\n', ' ')

  val starSchema =
  """
    |{
    |  "factTable" : "lineitem",
    |  "relations" : [ {
    |    "leftTable" : "lineitem",
    |    "rightTable" : "orders",
    |    "relationType" : "n-1",
    |    "joinCondition" : [ {
    |      "leftAttribute" : "l_orderkey",
    |      "rightAttribute" : "o_orderkey"
    |    } ]
    |  }, {
    |    "leftTable" : "lineitem",
    |    "rightTable" : "partsupp",
    |    "relationType" : "n-1",
    |    "joinCondition" : [ {
    |      "leftAttribute" : "l_partkey",
    |      "rightAttribute" : "ps_partkey"
    |    }, {
    |      "leftAttribute" : "l_suppkey",
    |      "rightAttribute" : "ps_suppkey"
    |    } ]
    |  }, {
    |    "leftTable" : "partsupp",
    |    "rightTable" : "part",
    |    "relationType" : "n-1",
    |    "joinCondition" : [ {
    |      "leftAttribute" : "ps_partkey",
    |      "rightAttribute" : "p_partkey"
    |    } ]
    |  }, {
    |    "leftTable" : "partsupp",
    |    "rightTable" : "supplier",
    |    "relationType" : "n-1",
    |    "joinCondition" : [ {
    |      "leftAttribute" : "ps_suppkey",
    |      "rightAttribute" : "s_suppkey"
    |    } ]
    |  }, {
    |    "leftTable" : "orders",
    |    "rightTable" : "customer",
    |    "relationType" : "n-1",
    |    "joinCondition" : [ {
    |      "leftAttribute" : "o_custkey",
    |      "rightAttribute" : "c_custkey"
    |    } ]
    |  }, {
    |    "leftTable" : "customer",
    |    "rightTable" : "custnation",
    |    "relationType" : "n-1",
    |    "joinCondition" : [ {
    |      "leftAttribute" : "c_nationkey",
    |      "rightAttribute" : "cn_nationkey"
    |    } ]
    |  }, {
    |    "leftTable" : "custnation",
    |    "rightTable" : "custregion",
    |    "relationType" : "n-1",
    |    "joinCondition" : [ {
    |      "leftAttribute" : "cn_regionkey",
    |      "rightAttribute" : "cr_regionkey"
    |    } ]
    |  }, {
    |    "leftTable" : "supplier",
    |    "rightTable" : "suppnation",
    |    "relationType" : "n-1",
    |    "joinCondition" : [ {
    |      "leftAttribute" : "s_nationkey",
    |      "rightAttribute" : "sn_nationkey"
    |    } ]
    |  }, {
    |    "leftTable" : "suppnation",
    |    "rightTable" : "suppregion",
    |    "relationType" : "n-1",
    |    "joinCondition" : [ {
    |      "leftAttribute" : "sn_regionkey",
    |      "rightAttribute" : "sr_regionkey"
    |    } ]
    |  } ]
    |}
  """.stripMargin.replace('\n', ' ')

  override def beforeAll() = {

    System.setProperty("user.timezone", "UTC")
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    TestHive.sparkContext.setLogLevel("INFO")

    register(TestHive)
    // DruidPlanner(TestHive)

    val cT = s"""CREATE TABLE if not exists orderLineItemPartSupplierBase(o_orderkey integer,
             o_custkey integer,
      o_orderstatus string, o_totalprice double, o_orderdate string, o_orderpriority string,
      o_clerk string,
      o_shippriority integer, o_comment string, l_partkey integer, l_suppkey integer,
      l_linenumber integer,
      l_quantity double, l_extendedprice double, l_discount double, l_tax double,
      l_returnflag string,
      l_linestatus string, l_shipdate string, l_commitdate string, l_receiptdate string,
      l_shipinstruct string,
      l_shipmode string, l_comment string, order_year string, ps_partkey integer,
      ps_suppkey integer,
      ps_availqty integer, ps_supplycost double, ps_comment string, s_name string, s_address string,
      s_phone string, s_acctbal double, s_comment string, s_nation string,
      s_region string, p_name string,
      p_mfgr string, p_brand string, p_type string, p_size integer, p_container string,
      p_retailprice double,
      p_comment string, c_name string , c_address string , c_phone string , c_acctbal double ,
      c_mktsegment string , c_comment string , c_nation string , c_region string)
      USING com.databricks.spark.csv
      OPTIONS (path "src/test/resources/tpch/datascale1/orderLineItemPartSupplierCustomer.small",
      header "false", delimiter "|")""".stripMargin

    println(cT)
    sql(cT)

    TestHive.table("orderLineItemPartSupplierBase").cache()

    TestHive.setConf(DruidPlanner.SPARKLINEDATA_CACHE_TABLES_TOCHECK.key,
      "orderLineItemPartSupplierBase")

    // sql("select * from orderLineItemPartSupplierBase limit 10").show(10)

    val cTOlap = s"""CREATE TABLE if not exists orderLineItemPartSupplier
      USING org.sparklinedata.druid
      OPTIONS (sourceDataframe "orderLineItemPartSupplierBase",
      timeDimensionColumn "l_shipdate",
      druidDatasource "tpch",
      druidHost "localhost",
      zkQualifyDiscoveryNames "true",
      columnMapping '$colMapping',
      functionalDependencies '$functionalDependencies',
      starSchema '$flatStarSchema')""".stripMargin

    println(cTOlap)
    sql(cTOlap)
  }

  def result(df : DataFrame) : Array[Row] = {
    df.collect()
  }

  def assertResultEquals(
                     df1 : DataFrame,
                    df2 : DataFrame
                   ) : Unit = {
    assert(result(df1) ==  result(df2))
  }

  def test(nm : String, sql : String,
           numDruidQueries : Int = 1,
           showPlan : Boolean = false,
           showResults : Boolean = false,
           debugTransforms : Boolean = false) : Unit = {
    test(nm) {td =>
      try {
        if (debugTransforms) turnOnTransformDebugging
        val df = sqlAndLog(nm, sql)
        assertDruidQueries(td.name, df, numDruidQueries)
        if (showPlan ) logPlan(nm, df)
        logDruidQueries(td.name, df)
        if (showResults ) { df.show() }
      } finally {
        turnOffTransformDebugging
      }
    }
  }


  def cTest(nm : String,
            dsql: String,
            bsql : String,
            debugTransforms : Boolean = false) : Unit = {
    test(nm) {td =>
      try {
        println("modifiefff")
        if (debugTransforms) turnOnTransformDebugging
        val df1 = sqlAndLog(nm, dsql)
        val df2 = sqlAndLog(nm, bsql)
        assert(isTwoDataFrameEqual(df1, df2))
      } finally {
        turnOffTransformDebugging

      }
    }
  }

  def sqlAndLog(nm : String, sqlStr : String) : DataFrame = {
    logInfo(s"\n$nm SQL:\n" + sqlStr)
    sql(sqlStr)
  }

  def logPlan(nm : String, df : DataFrame) : Unit = {
    logInfo(s"\n$nm Plan:")
    logInfo(s"\nLogical Plan:\n" + df.queryExecution.optimizedPlan.toString)
    logInfo(s"\nPhysical Plan:\n" + df.queryExecution.sparkPlan.toString)
  }

  def logDruidQueries(nm : String, df : DataFrame) : Unit = {
    logInfo(s"\n$nm Druid Queries:")
    DruidPlanner.getDruidQuerySpecs(df.queryExecution.sparkPlan).foreach {dq =>
      logInfo(s"${Utils.queryToString(dq)}")
    }
  }

  def assertDruidQueries(nm : String, df : DataFrame, numQueries : Int) : Unit = {
    assert(DruidPlanner.getDruidQuerySpecs(df.queryExecution.sparkPlan).length == numQueries)
  }

  def turnOnTransformDebugging : Unit = {
    TestHive.setConf(DruidPlanner.DEBUG_TRANSFORMATIONS.key, "true")
  }

  def turnOffTransformDebugging : Unit = {
    TestHive.setConf(DruidPlanner.DEBUG_TRANSFORMATIONS.key, "false")
  }


  def isTwoDataFrameEqual(df1 : DataFrame,
                       df2 : DataFrame,
                       Sorted : Boolean = false,
                       chooseRounding : Boolean = true): Boolean = {
    val df1_count = df1.count()
    val df2_count = df2.count()
    if(df1_count != df2_count){
      println(df1_count + "\t" + df2_count)
      println("The row count is not equal")
      return false
    }

    val df1_column = df1.columns
    val df2_column = df2.columns
    val column1_length = df1_column.length
    val column2_length = df2_column.length
    if (column1_length != column2_length){
      println(column1_length + "\t" + column2_length)
      println("The column length is not equal")
      return false
    }

    var df1_list = df1.collectAsList()
    var df2_list = df2.collectAsList()
    if (!Sorted) {
      var df11 = df1.sort(df1_column(0))
      var df22 = df2.sort(df2_column(0))
      for (i <- 1 to column1_length - 1) {
        df11 = df11.sort(df1_column(i))
        df22 = df22.sort(df2_column(i))
      }
      df1_list = df11.collectAsList()
      df2_list = df22.collectAsList()
    }
    if(!chooseRounding) {
      for (i <- 0 to df1_count.toInt - 1){
        for (j <- 0 to column1_length - 1){
          if(df1_list.get(i).get(j) !=
            df2_list.get(i).get(j)){
            println(df1_list.get(i).get(j) + "\t" + df2_list.get(i).get(j))
            println("The row content is not equal")
            return false
          }
        }
      }
    }else{
      for (i <- 0 to df1_count.toInt - 1){
        for (j <- 0 to column1_length - 1){
          if (df1_list.get(i).get(j) == null ||
            df2_list.get(i).get(j) == null){
            if (df1_list.get(i).get(j) !=
              df2_list.get(i).get(j)){
              println(df1_list.get(i).get(j) + "\t" + df2_list.get(i).get(j))
              println("The row content is not equal null")
              return false
            }
          }else{
            var raw1 = (df1_list.get(i).get(j)).toString
            var raw2 = (df2_list.get(i).get(j)).toString
            if (raw1.endsWith("f") || raw1.endsWith("F")){
              raw1 = raw1.substring(0, raw1.length - 1)
            }
            if (raw2.endsWith("f") || raw2.endsWith("F")){
              raw2 = raw2.substring(0, raw2.length - 1)
            }
            try {
              val res1 = BigDecimal(raw1)
                .setScale(2, BigDecimal.RoundingMode.HALF_UP)
                .toDouble
              val res2 = BigDecimal(raw2)
                .setScale(2, BigDecimal.RoundingMode.HALF_UP)
                .toDouble
              if(res1 != res2){
                println(res1 + "\t" + res2)
                println("The rouding row content is not equal null")
                return false
              }
            }catch{
              case ex: Exception => {
                if(raw1 != raw2){
                  println(raw1 + "\t" + raw2)
                  println("The rouding exce row content is not equal null")
                  return false
                }
              }
            }
          }
        }
      }
    }
    println("The two dataframe is equal " + df1_count)
    return true
  }

}

