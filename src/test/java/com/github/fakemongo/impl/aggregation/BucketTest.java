package com.github.fakemongo.impl.aggregation;

import com.github.fakemongo.junit.FongoRule;
import com.mongodb.AggregationOutput;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoCommandException;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 * Created by rkolliva
 * 1/22/17.
 */
public class BucketTest {

  private static final String BUCKET_TEST_COLLECTION =
      "[{ \"_id\" : 1, \"title\" : \"The Pillars of Society\", \"artist\" : \"Grosz\", \"year\" : 1926,\n" +
      "    \"price\" : 199.99 },\n" +
      "{ \"_id\" : 2, \"title\" : \"Melancholy III\", \"artist\" : \"Munch\", \"year\" : 1902,\n" +
      "    \"price\" : 280.00 },\n" +
      "{ \"_id\" : 3, \"title\" : \"Dancer\", \"artist\" : \"Miro\", \"year\" : 1925,\n" +
      "    \"price\" : 76.04 },\n" +
      "{ \"_id\" : 4, \"title\" : \"The Great Wave off Kanagawa\", \"artist\" : \"Hokusai\",\n" +
      "    \"price\" : 167.30 },\n" +
      "{ \"_id\" : 5, \"title\" : \"The Persistence of Memory\", \"artist\" : \"Dali\", \"year\" : 1931,\n" +
      "    \"price\" : 483.00 },\n" +
      "{ \"_id\" : 6, \"title\" : \"Composition VII\", \"artist\" : \"Kandinsky\", \"year\" : 1913,\n" +
      "    \"price\" : 385.00 },\n" +
      "{ \"_id\" : 7, \"title\" : \"The Scream\", \"artist\" : \"Munch\", \"year\" : 1893},\n" +
      "{ \"_id\" : 8, \"title\" : \"Blue Flower\", \"artist\" : \"O'Keefe\", \"year\" : 1918,\n" +
      "    \"price\" : 118.42 }]";

  private static final String BUCKET_TEST_COLLECTION_NO_DEFAULT =
      "[{ \"_id\" : 1, \"title\" : \"The Pillars of Society\", \"artist\" : \"Grosz\", \"year\" : 1926,\n" +
      "    \"price\" : 199.99 },\n" +
      "{ \"_id\" : 2, \"title\" : \"Melancholy III\", \"artist\" : \"Munch\", \"year\" : 1902,\n" +
      "    \"price\" : 280.00 },\n" +
      "{ \"_id\" : 3, \"title\" : \"Dancer\", \"artist\" : \"Miro\", \"year\" : 1925,\n" +
      "    \"price\" : 76.04 },\n" +
      "{ \"_id\" : 4, \"title\" : \"The Great Wave off Kanagawa\", \"artist\" : \"Hokusai\",\n" +
      "    \"price\" : 167.30 },\n" +
      "{ \"_id\" : 5, \"title\" : \"The Persistence of Memory\", \"artist\" : \"Dali\", \"year\" : 1931,\n" +
      "    \"price\" : 483.00 },\n" +
      "{ \"_id\" : 6, \"title\" : \"Composition VII\", \"artist\" : \"Kandinsky\", \"year\" : 1913,\n" +
      "    \"price\" : 385.00 },\n" +
      "{ \"_id\" : 7, \"title\" : \"The Scream\", \"artist\" : \"Munch\", \"year\" : 1893, \"price\" : 533.01},\n" +
      "{ \"_id\" : 8, \"title\" : \"Blue Flower\", \"artist\" : \"O'Keefe\", \"year\" : 1918,\n" +
      "    \"price\" : 118.42 }]";
  private static final Logger LOG = LoggerFactory.getLogger(LookupTest.class);
  @Rule
  public FongoRule fongoRule = new FongoRule(false);

  @Test
  public void mustBucketizeCollectionWithDefaultAndOutput() throws Exception {

    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, BUCKET_TEST_COLLECTION);
    DBObject bucket = fongoRule.parseDBObject("{\n" +
                                              "    $bucket: {\n" +
                                              "      groupBy: \"$price\",\n" +
                                              "      boundaries: [ 0, 200, 400 ],\n" +
                                              "      default: \"Other\",\n" +
                                              "      output: {\n" +
                                              "        \"count\": { $sum: 1 },\n" +
                                              "        \"titles\" : { $push: \"$title\" }\n" +
                                              "      }\n" +
                                              "    }\n" +
                                              "  }");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    pipeline.add(bucket);
    AggregationOutput output = collection.aggregate(pipeline);
    Iterable<DBObject> result = output.results();
    assertNotNull(result);
  }

  @Test
  public void mustBucketizeCollectionWithNoDefaultAndOutput() throws Exception {

    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, BUCKET_TEST_COLLECTION_NO_DEFAULT);
    DBObject bucket = fongoRule.parseDBObject("{\n" +
                                              "    $bucket: {\n" +
                                              "      groupBy: \"$price\",\n" +
                                              "      boundaries: [ 0, 200, 400, 600 ],\n" +
                                              "      output: {\n" +
                                              "        \"count\": { $sum: 1 },\n" +
                                              "        \"titles\" : { $push: \"$title\" }\n" +
                                              "      }\n" +
                                              "    }\n" +
                                              "  }");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    pipeline.add(bucket);
    AggregationOutput output = collection.aggregate(pipeline);
    Iterable<DBObject> result = output.results();
    assertNotNull(result);
  }

  @Test
  public void mustReturnCountWhenOutputIsNotSpecified() throws Exception {

    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, BUCKET_TEST_COLLECTION_NO_DEFAULT);
    DBObject bucket = fongoRule.parseDBObject("{\n" +
                                              "    $bucket: {\n" +
                                              "      groupBy: \"$price\",\n" +
                                              "      boundaries: [ 0, 200, 400, 600 ],\n" +
                                              "    }\n" +
                                              "  }");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    pipeline.add(bucket);
    AggregationOutput output = collection.aggregate(pipeline);
    Iterable<DBObject> result = output.results();
    assertNotNull(result);
  }

  @Test(expected = MongoCommandException.class)
  public void mustThrowExceptionIfBoundariesDontFitData()  {

    DBCollection collection = fongoRule.newCollection();
    fongoRule.insertJSON(collection, BUCKET_TEST_COLLECTION_NO_DEFAULT);
    DBObject bucket = fongoRule.parseDBObject("{\n" +
                                              "    $bucket: {\n" +
                                              "      groupBy: \"$price\",\n" +
                                              "      boundaries: [ 0, 200, 400 ],\n" +
                                              "      output: {\n" +
                                              "        \"count\": { $sum: 1 },\n" +
                                              "        \"titles\" : { $push: \"$title\" }\n" +
                                              "      }\n" +
                                              "    }\n" +
                                              "  }");
    List<DBObject> pipeline = new ArrayList<DBObject>();
    pipeline.add(bucket);
    AggregationOutput output = collection.aggregate(pipeline);
    Iterable<DBObject> result = output.results();
    assertNotNull(result);
  }
}