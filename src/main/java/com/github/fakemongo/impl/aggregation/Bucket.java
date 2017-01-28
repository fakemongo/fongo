package com.github.fakemongo.impl.aggregation;

import com.github.fakemongo.impl.ExpressionParser;
import com.mongodb.*;
import com.mongodb.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by rkolliva
 * 1/21/17.
 */


public class Bucket extends PipelineKeyword {

  public static final Bucket INSTANCE = new Bucket();
  private static final Logger LOGGER = LoggerFactory.getLogger(Bucket.class);
  private static final String ID = "_id";

  @Override
  public DBCollection apply(DB originalDB, DBCollection parentColl, DBObject aggQuery) {
    LOGGER.trace(">>>> applying $bucket pipeline operation");
    DBObject lookup = ExpressionParser.toDbObject(aggQuery.get(getKeyword()));
    Collection<DBObject> parentItems = bucketize(parentColl, lookup);
    List<DBObject> dbObjects = new ArrayList<DBObject>();
    for (DBObject object : parentItems) {
      // don't include those values that don't have any entries other than _id
      Object obj = object.removeField(ID);
      if(object.keySet().size() > 0) {
        // add the _id field back and include in returned results.
        object.put(ID, obj);
        dbObjects.add(object);
      }
    }
    LOGGER.trace("<<<< applying $bucket pipeline operation");
    return dropAndInsert(parentColl, dbObjects);
  }

  @SuppressWarnings("unchecked")
  private Collection<DBObject> bucketize(DBCollection parentColl, DBObject lookup) {
    LOGGER.trace(">>>> Bucketizing collection ");
    Object groupByObj = lookup.get("groupBy");
    validateNull(groupByObj, "groupBy param must not be null");
    Object boundariesObj = lookup.get("boundaries");
    validateNull(boundariesObj, "boundaries param must not be null");
    validateTrue(List.class.isAssignableFrom(boundariesObj.getClass()), "boundaries must be of list type");
    List boundariesList = (List) boundariesObj;
    for (Object b : boundariesList) {
      validateTrue(Number.class.isAssignableFrom(b.getClass()), "Boundary entry " + b.toString() + " is not numeric");
    }
    List<Number> boundaries = (List<Number>) boundariesList;
    Map<String, DBObject> histograms = new HashMap<String, DBObject>();
    for (Number number : boundaries) {
      BasicDBObject basicDBObject = new BasicDBObject();
      String _idValue = String.valueOf(number);
      basicDBObject.put(ID, _idValue);
      histograms.put(_idValue, basicDBObject);
    }
    validateTrue(boundariesList.size() > 1, "boundaries must have at least two elements");
    Object defaultGroupObj = lookup.get("default");
    String defaultGroup = null;
    if (defaultGroupObj != null) {
      validateTrue(String.class.isAssignableFrom(defaultGroupObj.getClass()), "default must be a string");
      defaultGroup = (String) defaultGroupObj;
      BasicDBObject b = new BasicDBObject();
      b.put(ID, defaultGroup);
      histograms.put(defaultGroup, b);
    }
    DBObject output = ExpressionParser.toDbObject(lookup.get("output"));
    Collection<DBObject> groupedColl = bucketize(histograms, parentColl, groupByObj, boundaries,
                                                 defaultGroup, output);

    LOGGER.trace("<<<< Bucketizing collection ");
    return groupedColl;
  }

  private Collection<DBObject> bucketize(Map<String, DBObject> histograms, DBCollection parentColl,
                                         Object groupByObj, List<Number> boundaries, String defaultGroup, DBObject output) {
    DBCursor cursor = parentColl.find();
    while (cursor.hasNext()) {
      DBObject object = cursor.next();
      if (String.class.isAssignableFrom(groupByObj.getClass())) {
        updateBucket(histograms, object, (String) groupByObj, boundaries, defaultGroup, output);
      }
    }
    return histograms.values();
  }

  private void updateBucket(Map<String, DBObject> histograms, DBObject inputObj,
                            String groupByObj, List<Number> boundariesList, String defaultGroup, DBObject output) {
    // only supporting single field grouping at this time - $bucket supports arbitrary expr.
    validateTrue(groupByObj.charAt(0) == '$', "only single field grouping is supported currently and " +
                                              "must start with $");
    String field = groupByObj.substring(1);
    Object groupedField = inputObj.get(field);
    Number number = null;
    boolean bucketMatchFound = false;
    int index = 1;
    Number lb = boundariesList.get(0);
    if (groupedField != null) {
      validateTrue(Number.class.isAssignableFrom(groupedField.getClass()), "groupBy field value must be numeric");
      number = (Number) inputObj.get(field);
    }
    else {
      // don't try to match if groupedField is missing in the input document.
      // it'll go into the default bucket.
      bucketMatchFound = true;
      // set it to one larger than number of boundaries defined - this should go into the default bucket.
      index = boundariesList.size() + 1;
    }
    while (!bucketMatchFound && index < boundariesList.size()) {
      Number ub = boundariesList.get(index);
      if (number.doubleValue() >= lb.doubleValue() && number.doubleValue() < ub.doubleValue()) {
        bucketMatchFound = true;
      }
      else {
        lb = ub;
        index++;
      }
    }
    DBObject matchedHistogramBucket = null;
    if (bucketMatchFound) {
      // found a match - the id of this bucket is the value of the boundaryList[index-1]
      if (index < boundariesList.size()) {
        Number boundary = boundariesList.get(index - 1);
        matchedHistogramBucket = histograms.get(String.valueOf(boundary));
      }
    }
    if (matchedHistogramBucket == null) {
      if (defaultGroup == null) {
        fongo.errorResult(15955, "Must specify defaultGroup for unmatched buckets").throwOnError();
      }
      matchedHistogramBucket = histograms.get(defaultGroup);
    }
    if (output == null) {
      Integer value = (Integer) matchedHistogramBucket.get("count");
      if(value == null) {
        value = 0;
        matchedHistogramBucket.put("count", value);
      }
      matchedHistogramBucket.put("count", ++value);
    }
    else {
      // accumulate values
      Set<String> keys = output.keySet();
      for (String key : keys) {
        DBObject accumulatorExpr = (DBObject) output.get(key);
        for (BucketAccumulator accumulator : BucketAccumulator.values()) {
          if (accumulator.canApply(accumulatorExpr)) {
            accumulator.apply(inputObj, key, matchedHistogramBucket, accumulatorExpr);
          }
        }
      }
    }
  }

  @ThreadSafe
  enum BucketAccumulator {
    SUM("$sum") {
      @Override
      void apply(DBObject input, String outputKey, DBObject matchedHistogramBucket, DBObject accumulatorExpr) {
        Integer val = (Integer) matchedHistogramBucket.get(outputKey);
        if (val == null) {
          val = 0;
        }
        Integer increment = (Integer) accumulatorExpr.get(getKeyword());
        matchedHistogramBucket.put(outputKey, val + increment);
      }
    },
    PUSH("$push") {
      @SuppressWarnings("unchecked")
      @Override
      void apply(DBObject input, String outputKey, DBObject matchedHistogramBucket, DBObject accumulatorExpr) {
        List<Object> val = (List<Object>) matchedHistogramBucket.get(outputKey);
        if (val == null) {
          val = new ArrayList<Object>();
          matchedHistogramBucket.put(outputKey, val);
        }
        String pushedValue = (String) accumulatorExpr.get(getKeyword());
        validateTrue(pushedValue.charAt(0) == '$', "Field value to push must start with $");
        val.add(input.get(pushedValue.substring(1)));
      }
    };

    private final String keyword;

    BucketAccumulator(String keyword) {
      this.keyword = keyword;
    }

    abstract void apply(DBObject input, String outputKey, DBObject matchedHistogramBucket, DBObject accumulatorExpr);

    public boolean canApply(DBObject parameter) {
      return parameter.containsField(keyword);
    }

    public String getKeyword() {
      return keyword;
    }
  }

  @Override
  public String getKeyword() {
    return "$bucket";
  }
}
