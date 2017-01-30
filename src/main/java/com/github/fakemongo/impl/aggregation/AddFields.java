package com.github.fakemongo.impl.aggregation;

import com.github.fakemongo.impl.ExpressionParser;
import com.mongodb.*;
import com.mongodb.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by rkolliva
 * 1/29/17.
 */
public class AddFields extends PipelineKeyword {

  private static final Logger LOGGER = LoggerFactory.getLogger(AddFields.class);

  public static AddFields INSTANCE = new AddFields();

  @Override
  public DBCollection apply(DB originalDB, DBCollection parentColl, DBObject addFieldsQuery) {
    LOGGER.trace(">>>> applying $addFields pipeline operation");

    DBObject fieldsToAddExpr = ExpressionParser.toDbObject(addFieldsQuery.get(getKeyword()));
    List<DBObject> dbObjects = addFieldsToDocument(parentColl, fieldsToAddExpr);
    LOGGER.trace("<<<< applying $addFields pipeline operation");
    return dropAndInsert(parentColl, dbObjects);

  }

  private List<DBObject> addFieldsToDocument(DBCollection parentColl, DBObject fieldsToAddExpr) {
    List<DBObject> dbObjects = new ArrayList<DBObject>();
    DBCursor cursor = parentColl.find();
    for (DBObject item : cursor) {
      DBObject newObject = new BasicDBObject();
      newObject.putAll(item);
      dbObjects.add(newObject);
      for (String fieldToAdd : fieldsToAddExpr.keySet()) {
        Object object = fieldsToAddExpr.get(fieldToAdd);
        validateNull(object, "Expression for field " + fieldToAdd + " cannot be null");
        for (AddFieldsExpr addFieldsExpr : AddFieldsExpr.values()) {
          if (addFieldsExpr.canApply(object)) {
            addFieldsExpr.apply(newObject, fieldToAdd, object);
          }
        }
      }
    }
    return dbObjects;
  }

  @Override
  public String getKeyword() {
    return "$addFields";
  }

  @ThreadSafe enum AddFieldsExpr {
    SUM("$sum") {
      @Override
      void apply(DBObject itemToAddFieldsTo, String newFieldName, Object sumExprObjParam) {
        validateTrue(DBObject.class.isAssignableFrom(sumExprObjParam.getClass()), "expr must be of DBObject type");
        DBObject sumExprObj = (DBObject) sumExprObjParam;
        Object sumExpr = sumExprObj.get(getKeyword());
        double result = 0;
        if (String.class.isAssignableFrom(sumExpr.getClass())) {
          String sumExprStr = (String) sumExpr;
          if(sumExprStr.charAt(0) == '$') {
            String fieldNameToSum = sumExprStr.substring(1);
            Object fieldToSum = itemToAddFieldsTo.get(fieldNameToSum);
            validateNull(fieldToSum, "field expr " + sumExprStr + " evaluated to null");
            validateTrue(Iterable.class.isAssignableFrom(fieldToSum.getClass()),
                         "field to sum must be an iterable type");
            Iterable iterable = (Iterable) fieldToSum;
            int index = 0;
            for (Object iterableItem : iterable) {
              validateTrue(Number.class.isAssignableFrom(iterableItem.getClass()), "Value at index [" + index++ +
                                                                                   "] must be numeric");
              Number number = (Number) iterableItem;
              result += number.doubleValue();
            }
          }
          else {
            fongo.errorResult(15955, "String expr for $sum must start with $").throwOnError();
          }
          itemToAddFieldsTo.put(newFieldName, result);
        }
        else if(Number.class.isAssignableFrom(sumExpr.getClass())) {
          // adding a literal.
          itemToAddFieldsTo.put(newFieldName, ((Number)sumExpr).doubleValue());
        }
        else {
          fongo.errorResult(15955, "$sum must either be a numeric field or a literal number").throwOnError();
        }
      }
    },

    ADD("$add") {
      @Override
      void apply(DBObject itemToAddFieldsTo, String newFieldName, Object addExprObjParam) {
        validateTrue(DBObject.class.isAssignableFrom(addExprObjParam.getClass()), "expr must be of DBObject type");
        DBObject addExprObj = (DBObject) addExprObjParam;
        Object addExpr = addExprObj.get(getKeyword());
        validateTrue(Iterable.class.isAssignableFrom(addExpr.getClass()), "add expression must be an array");
        Iterable iterable = (Iterable) addExpr;
        double result = 0;
        int index = 0;
        for (Object iterableItem : iterable) {
          if (String.class.isAssignableFrom(iterableItem.getClass())) {
            // the value is a field in this document - must start with $
            String iterableItemStr = (String) iterableItem;
            validateTrue(iterableItemStr.startsWith("$"),
                         "Field expression must start with $ at index [" + index + "]");
            String fieldValueToAdd = iterableItemStr.substring(1);
            Object value = itemToAddFieldsTo.get(fieldValueToAdd);
            validateTrue(Number.class.isAssignableFrom(value.getClass()), "Field value to add must be numeric  at " +
                                                                          "index [" + index + "]");
            result += ((Number) value).doubleValue();
          }
          else {
            validateTrue(Number.class.isAssignableFrom(iterableItem.getClass()),
                         "Field value to add must be numeric  " +
                         "at index [" + index + "]");
            result += ((Number) iterableItem).doubleValue();
          }
        }
        itemToAddFieldsTo.put(newFieldName, result);
      }
    },

    LITERAL("") {
      @Override
      void apply(DBObject itemToAddFieldsTo, String newFieldName, Object addExprObjParam) {
        if (addExprObjParam instanceof String) {
          String addExprObj = (String) addExprObjParam;
          if (addExprObj.startsWith("$")) {
            // this must be a value within the document.
            itemToAddFieldsTo.put(newFieldName, itemToAddFieldsTo.get(addExprObj.substring(1)));
          }
          else {
            itemToAddFieldsTo.put(newFieldName, addExprObjParam);
          }
        }
        else {
          itemToAddFieldsTo.put(newFieldName, addExprObjParam);
        }
      }

      @Override
      public boolean canApply(Object parameter) {
        return SUPPORTED_TYPES.contains(parameter.getClass());
      }

      private Set<Class> SUPPORTED_TYPES;

      @Override
      protected void init() {
        SUPPORTED_TYPES = new HashSet<Class>();
        SUPPORTED_TYPES.add(Boolean.class);
        SUPPORTED_TYPES.add(Character.class);
        SUPPORTED_TYPES.add(Byte.class);
        SUPPORTED_TYPES.add(Short.class);
        SUPPORTED_TYPES.add(String.class);
        SUPPORTED_TYPES.add(Integer.class);
        SUPPORTED_TYPES.add(Long.class);
        SUPPORTED_TYPES.add(Float.class);
        SUPPORTED_TYPES.add(Double.class);
        SUPPORTED_TYPES.add(Void.class);
      }
    };

    private final String keyword;

    AddFieldsExpr(String keyword) {
      this.keyword = keyword;
      init();
    }

    abstract void apply(DBObject itemToAddFieldsTo, String newFieldName, Object sumExpr);

    public boolean canApply(Object parameter) {
      return parameter instanceof DBObject && ((DBObject) parameter).containsField(keyword);
    }

    protected void init() {

    }

    public String getKeyword() {
      return keyword;
    }
  }
}
