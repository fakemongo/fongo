package com.github.fakemongo.impl.aggregation;

import com.github.fakemongo.junit.FongoRule;
import com.mongodb.AggregationOutput;
import com.mongodb.BasicDBList;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by rkolliva
 * 1/29/17.
 */
public class AddFieldsTest {

  private static final String ADD_FIELDS_MULTIPLE_TIMES = "[{\n" +
                                                          "  _id: 1,\n" +
                                                          "  student: \"Maya\",\n" +
                                                          "  homework: [ 10, 5, 10 ],\n" +
                                                          "  quiz: [ 10, 8 ],\n" +
                                                          "  extraCredit: 0\n" +
                                                          "},\n" +
                                                          "{\n" +
                                                          "  _id: 2,\n" +
                                                          "  student: \"Ryan\",\n" +
                                                          "  homework: [ 5, 6, 5 ],\n" +
                                                          "  quiz: [ 8, 8 ],\n" +
                                                          "  extraCredit: 8\n" +
                                                          "}]";

  private static final String ADD_FIELDS_EMBEDDED_DOC = "[{ _id: 1, type: \"car\", specs: { doors: 4, wheels: 4 } },\n" +
                                                        "{ _id: 2, type: \"motorcycle\", specs: { doors: 0, wheels: 2 } },\n" +
                                                        "{ _id: 3, type: \"jet ski\" }]";

  private static final String ADD_FIELDS_OVERWRITE = "[{ _id: 1, dogs: 10, cats: 15 }]";

  private static final String ADD_FIELDS_REPLACE = "[{ \"_id\" : 1, \"item\" : \"tangerine\", \"type\" : \"citrus\" },\n" +
                                                   "{ \"_id\" : 2, \"item\" : \"lemon\", \"type\" : \"citrus\" },\n" +
                                                   "{ \"_id\" : 3, \"item\" : \"grapefruit\", \"type\" : \"citrus\" }]";


  @Rule
  public FongoRule fongoRule = new FongoRule(false);

  @Test
  public void mustAddFieldsUsingTwoStages() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, ADD_FIELDS_MULTIPLE_TIMES);
    DBObject aggQuery = fongoRule.parseDBObject("[{\n" +
                                                "     $addFields: {\n" +
                                                "       totalHomework: { $sum: \"$homework\" } ,\n" +
                                                "       totalQuiz: { $sum: \"$quiz\" }\n" +
                                                "     }\n" +
                                                "   },\n" +
                                                "   {\n" +
                                                "     $addFields: { totalScore:\n" +
                                                "       { $add: [ \"$totalHomework\", \"$totalQuiz\", \"$extraCredit\" ] } }\n" +
                                                "   }]");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    for (Object o : ((BasicDBList) aggQuery)) {
      pipeline.add((DBObject) o);
    }
    AggregationOutput output = collection.aggregate(pipeline);
    assertNotNull(output);
    Iterable<DBObject> results = output.results();
    assertTrue(results instanceof List);
    List<DBObject> dbObjects = (List<DBObject>)results;
    assertTrue(dbObjects.size() == 2);
    for(DBObject result : results) {
      if(((Integer)result.get("_id")) == 1) {
        validateMultipleAddFieldsResult(result, 25.0, 18.0, 43.0);
      }
      else {
        validateMultipleAddFieldsResult(result, 16.0, 16.0, 40.0);
      }
    }
  }

  @Test
  public void mustAddFieldsUsingLiteralNumbers() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, ADD_FIELDS_MULTIPLE_TIMES);
    DBObject aggQuery = fongoRule.parseDBObject("[{\n" +
                                                "     $addFields: {\n" +
                                                "       totalHomework: { $sum: \"$homework\" } ,\n" +
                                                "       totalQuiz: { $sum: \"$quiz\" }\n" +
                                                "     }\n" +
                                                "   },\n" +
                                                "   {\n" +
                                                "     $addFields: { totalScore:\n" +
                                                "       { $add: [ \"$totalHomework\", \"$totalQuiz\", \"$extraCredit\", 5.0] } }\n" +
                                                "   }]");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    for (Object o : ((BasicDBList) aggQuery)) {
      pipeline.add((DBObject) o);
    }
    AggregationOutput output = collection.aggregate(pipeline);
    assertNotNull(output);
    Iterable<DBObject> results = output.results();
    assertTrue(results instanceof List);
    List<DBObject> dbObjects = (List<DBObject>)results;
    assertTrue(dbObjects.size() == 2);
    for(DBObject result : results) {
      if(((Integer)result.get("_id")) == 1) {
        validateMultipleAddFieldsResult(result, 25.0, 18.0, 48.0);
      }
      else {
        validateMultipleAddFieldsResult(result, 16.0, 16.0, 45.0);
      }
    }
  }

  @Test
  public void mustBeAbleToSumLiteralValues() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, ADD_FIELDS_MULTIPLE_TIMES);
    DBObject aggQuery = fongoRule.parseDBObject("[{\n" +
                                                "     $addFields: {\n" +
                                                "       totalHomework: { $sum: \"$homework\" } ,\n" +
                                                "       totalQuiz: { $sum: 20 }\n" +
                                                "     }\n" +
                                                "   },\n" +
                                                "   {\n" +
                                                "     $addFields: { totalScore:\n" +
                                                "       { $add: [ \"$totalHomework\", \"$totalQuiz\", \"$extraCredit\"] } }\n" +
                                                "   }]");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    for (Object o : ((BasicDBList) aggQuery)) {
      pipeline.add((DBObject) o);
    }
    AggregationOutput output = collection.aggregate(pipeline);
    assertNotNull(output);
    Iterable<DBObject> results = output.results();
    assertTrue(results instanceof List);
    List<DBObject> dbObjects = (List<DBObject>)results;
    assertTrue(dbObjects.size() == 2);
    for(DBObject result : results) {
      if(((Integer)result.get("_id")) == 1) {
        validateMultipleAddFieldsResult(result, 25.0, 20, 45.0);
      }
      else {
        validateMultipleAddFieldsResult(result, 16.0, 20.0, 44.0);
      }
    }
  }

  @Test(expected = Exception.class)
  public void mustThrowErrorIfSumDoesntStartWithDollar() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, ADD_FIELDS_MULTIPLE_TIMES);
    DBObject aggQuery = fongoRule.parseDBObject("[{\n" +
                                                "     $addFields: {\n" +
                                                "       totalHomework: { $sum: \"homework\" } ,\n" +
                                                "       totalQuiz: { $sum: 20 }\n" +
                                                "     }\n" +
                                                "   },\n" +
                                                "   {\n" +
                                                "     $addFields: { totalScore:\n" +
                                                "       { $add: [ \"$totalHomework\", \"$totalQuiz\", \"$extraCredit\"] } }\n" +
                                                "   }]");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    for (Object o : ((BasicDBList) aggQuery)) {
      pipeline.add((DBObject) o);
    }
    collection.aggregate(pipeline);
  }

  @Test(expected = Exception.class)
  public void mustThrowErrorIfSumIsAnArray() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, ADD_FIELDS_MULTIPLE_TIMES);
    DBObject aggQuery = fongoRule.parseDBObject("[{\n" +
                                                "     $addFields: {\n" +
                                                "       totalHomework: { $sum: \"$homework\" } ,\n" +
                                                "       totalQuiz: { $sum: [20, 30] }\n" +
                                                "     }\n" +
                                                "   },\n" +
                                                "   {\n" +
                                                "     $addFields: { totalScore:\n" +
                                                "       { $add: [ \"$totalHomework\", \"$totalQuiz\", \"$extraCredit\"] } }\n" +
                                                "   }]");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    for (Object o : ((BasicDBList) aggQuery)) {
      pipeline.add((DBObject) o);
    }
    collection.aggregate(pipeline);
  }

  @Test
  public void mustAddFieldsToAnEmbeddedDoc() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, ADD_FIELDS_EMBEDDED_DOC);
    DBObject aggQuery = fongoRule.parseDBObject("{\n" +
                                                "           $addFields: {\n" +
                                                "              \"specs.fuel_type\": \"unleaded\"\n" +
                                                "           }\n" +
                                                "        }");
    AggregationOutput output = collection.aggregate(Arrays.asList(aggQuery));
    assertNotNull(output);
    Iterable<DBObject> results = output.results();
    assertTrue(results instanceof List);
    List<DBObject> dbObjects = (List<DBObject>)results;
    assertTrue(dbObjects.size() == 3);
    for(DBObject result : results) {
      if(((Integer)result.get("_id")) == 1) {
        validateEmbeddedAddFieldsResult(result, 3);
      }
      else if(((Integer)result.get("_id")) == 2) {
        validateEmbeddedAddFieldsResult(result, 3);
      }
      else {
        validateEmbeddedAddFieldsResult(result, 1);
      }
    }
  }

  @Test
  public void mustOverwriteAnExistingField() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, ADD_FIELDS_OVERWRITE);
    DBObject aggQuery = fongoRule.parseDBObject("{\n" +
                                                "    $addFields: { \"cats\": 20 }\n" +
                                                "  }");
    AggregationOutput output = collection.aggregate(Arrays.asList(aggQuery));
    assertNotNull(output);
    Iterable<DBObject> results = output.results();
    assertTrue(results instanceof List);
    List<DBObject> dbObjects = (List<DBObject>)results;
    assertTrue(dbObjects.size() == 1);
    assertEquals((Integer)dbObjects.get(0).get("cats"), (Integer)20);
  }

  @Test
  public void mustReplaceAnExistingFieldWithAnother() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, ADD_FIELDS_REPLACE);
    DBObject aggQuery = fongoRule.parseDBObject("{\n" +
                                                "    $addFields: {\n" +
                                                "      _id : \"$item\",\n" +
                                                "      item: \"fruit\"\n" +
                                                "    }\n" +
                                                "  }");
    AggregationOutput output = collection.aggregate(Arrays.asList(aggQuery));
    assertNotNull(output);
    Iterable<DBObject> results = output.results();
    assertTrue(results instanceof List);
    List<DBObject> dbObjects = (List<DBObject>)results;
    assertTrue(dbObjects.size() == 3);
    for(DBObject result : dbObjects) {
      if(result.get("_id").equals("tangerine")) {
        validateReplaceFieldsResult(result, "tangerine", "fruit", "citrus");
      }
      else if(result.get("_id").equals("lemon")) {
        validateReplaceFieldsResult(result, "lemon", "fruit", "citrus");
      }
      else {
        validateReplaceFieldsResult(result, "grapefruit", "fruit", "citrus");
      }
    }
  }

  private void validateReplaceFieldsResult(DBObject result, String idValue, String item, String type) {
    assertEquals(result.get("_id"), idValue);
    assertEquals(result.get("item"), item);
    assertEquals(result.get("type"), type);
  }

  private void validateEmbeddedAddFieldsResult(DBObject result, int sizeOfSpecs) {
    assertNotNull(result.get("specs"));
    assertTrue(((DBObject)result.get("specs")).keySet().size() == sizeOfSpecs);
    assertEquals(((Map)result.get("specs")).get("fuel_type"), "unleaded");
  }

  private void validateMultipleAddFieldsResult(DBObject result, double hw, double quiz, double total) {
    assertTrue(((Number)result.get("totalHomework")).doubleValue() == hw);
    assertTrue(((Number)result.get("totalQuiz")).doubleValue() == quiz);
    assertTrue(((Number)result.get("totalScore")).doubleValue() == total);
  }

}