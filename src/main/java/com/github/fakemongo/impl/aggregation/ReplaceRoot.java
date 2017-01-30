package com.github.fakemongo.impl.aggregation;

import com.github.fakemongo.impl.ExpressionParser;
import com.mongodb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by rkolliva
 * 1/28/17.
 */


public class ReplaceRoot extends PipelineKeyword {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceRoot.class);

  public static ReplaceRoot INSTANCE = new ReplaceRoot();


  @Override
  public DBCollection apply(DB originalDB, DBCollection parentColl, DBObject replaceRootQuery) {
    LOGGER.trace(">>>> applying $replaceRoot pipeline operation");

    DBObject newRoot = ExpressionParser.toDbObject(replaceRootQuery.get(getKeyword()));
    LOGGER.trace("<<<< applying $replaceRoot pipeline operation");
    List<DBObject> dbObjects = replaceRootFromDocument(parentColl, newRoot);
    return dropAndInsert(parentColl, dbObjects);
  }

  private List<DBObject> replaceRootFromDocument(DBCollection parentColl, DBObject replaceRootExpr) {
    Object replaceRootExprValue = replaceRootExpr.get("newRoot");
    validateNull(replaceRootExprValue, "newRoot expression cannot be null");
    List<DBObject> retval = new ArrayList<DBObject>();
    if(replaceRootExprValue instanceof String) {
      String newRootValue = (String)replaceRootExprValue;
      int index = 0;
      validateTrue(newRootValue.charAt(0) == '$', "Field path expression for newRoot must start with $");
      newRootValue = newRootValue.substring(1);
      DBCursor cursor = parentColl.find();
      while(cursor.hasNext()) {
        DBObject object = cursor.next();
        Object embeddedDoc = object.get(newRootValue);
        validateNull(embeddedDoc, newRootValue + " is missing in collection at index " + index);
        validateTrue(DBObject.class.isAssignableFrom(embeddedDoc.getClass()),
                     "Embedded value must evaluate to document at " + index);
        index++;
        retval.add((DBObject) embeddedDoc);
      }
      return retval;
    }
    else {
      DBObject newRootExpr = ExpressionParser.toDbObject(replaceRootExprValue);
      String projectionExpr = "{$project :".concat(newRootExpr.toString()).concat("}");
      DBObject projection = ExpressionParser.toDbObject(BasicDBObject.parse(projectionExpr));
      AggregationOutput aggregationOutput = parentColl.aggregate(Arrays.asList(projection));
      Iterable<DBObject> results = aggregationOutput.results();
      validateNull(results, "Expression for new root resulted in a null object");
      for (DBObject result : results) {
        retval.add(result);
      }
    }

    return retval;
  }

  @Override
  public String getKeyword() {
    return "$replaceRoot";
  }
}
