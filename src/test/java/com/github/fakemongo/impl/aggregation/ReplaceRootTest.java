package com.github.fakemongo.impl.aggregation;

import com.github.fakemongo.junit.FongoRule;
import com.mongodb.AggregationOutput;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by rkolliva
 * 1/28/17.
 */
public class ReplaceRootTest {

  private static final String REPLACE_ROOT_NEW_DOC = "[{ \"_id\" : 1, \"first_name\" : \"Gary\", " +
                                                     "\"last_name\" : \"Sheffield\", \"city\" : \"New York\" },\n" +
                                                     "{ \"_id\" : 2, \"first_name\" : \"Nancy\", " +
                                                     "\"last_name\" : \"Walker\", \"city\" : \"Anaheim\" },\n" +
                                                     "{ \"_id\" : 3, \"first_name\" : \"Peter\", " +
                                                     "\"last_name\" : \"Sumner\", \"city\" : \"Toledo\" }]";

  private static final String REPLACE_ROOT_EMBEDDED_DOC = "[{\n" +
                                                          "   \"_id\" : 1,\n" +
                                                          "   \"fruit\" : [ \"apples\", \"oranges\" ],\n" +
                                                          "   \"in_stock\" : { \"oranges\" : 20, \"apples\" : 60 },\n" +
                                                          "   \"on_order\" : { \"oranges\" : 35, \"apples\" : 75 }\n" +
                                                          "},\n" +
                                                          "{\n" +
                                                          "   \"_id\" : 2,\n" +
                                                          "   \"vegetables\" : [ \"beets\", \"yams\" ],\n" +
                                                          "   \"in_stock\" : { \"beets\" : 130, \"yams\" : 200 },\n" +
                                                          "   \"on_order\" : { \"beets\" : 90, \"yams\" : 145 }\n" +
                                                          "}]";


  @Rule
  public FongoRule fongoRule = new FongoRule(false);

  @Test
  public void mustReplaceRootWithEmbeddedDocument() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, REPLACE_ROOT_EMBEDDED_DOC);
    DBObject bucket = fongoRule.parseDBObject("{$replaceRoot: { newRoot: \"$in_stock\" }}");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    pipeline.add(bucket);
    AggregationOutput output = collection.aggregate(pipeline);
    Iterable<DBObject> result = output.results();
    assertNotNull(result);
    Iterator<DBObject> iterator = result.iterator();
    assertTrue(((List)result).size() == 2);
    while(iterator.hasNext()) {
      DBObject object = iterator.next();
      if(object.containsField("oranges")) {
        assertTrue(((Integer)object.get("oranges")) == 20);
      }
      if(object.containsField("yams")) {
        assertTrue(((Integer)object.get("yams")) == 200);
      }
      if(object.containsField("beets")) {
        assertTrue(((Integer)object.get("beets")) == 130);
      }
      if(object.containsField("apples")) {
        assertTrue(((Integer)object.get("apples")) == 60);
      }

    }
  }

  @Test
  public void mustReplaceRootWithNewDocument() throws Exception {
    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, REPLACE_ROOT_NEW_DOC);
    DBObject bucket = fongoRule.parseDBObject("{\n" +
                                              "      $replaceRoot: {\n" +
                                              "         newRoot: {\n" +
                                              "            full_name: {\n" +
                                              "               $concat : [ \"$first_name\", \" \", \"$last_name\" ]\n" +
                                              "            }\n" +
                                              "         }\n" +
                                              "      }}");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    pipeline.add(bucket);
    AggregationOutput output = collection.aggregate(pipeline);
    Iterable<DBObject> result = output.results();
    assertNotNull(result);
    Iterator<DBObject> iterator = result.iterator();
    assertTrue(((List)result).size() == 3);
    List<String> expectedResults = Arrays.asList("Gary Sheffield", "Nancy Walker", "Peter Sumner");
    while(iterator.hasNext()) {
      DBObject object = iterator.next();
      assertTrue(object.containsField("full_name"));
      Object value = object.get("full_name");
      assertNotNull(value);
      assertTrue(String.class.isAssignableFrom(value.getClass()));
      String valueStr = (String)value;
      assertTrue(expectedResults.indexOf(valueStr) != -1);
    }
  }

}