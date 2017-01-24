package com.github.fakemongo.impl.aggregation;

import com.github.fakemongo.impl.ExpressionParser;
import com.github.fakemongo.impl.Util;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.FongoDBCollection;
import com.mongodb.MongoException;
import com.mongodb.annotations.ThreadSafe;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@see http://docs.mongodb.org/manual/reference/aggregation/group/}
 */
@ThreadSafe
public class Group extends PipelineKeyword {
  private static final Logger LOG = LoggerFactory.getLogger(Group.class);

  public static final Group INSTANCE = new Group();

  static class Mapping {
    private final DBObject key;

    private final DBCollection collection;

    private final DBObject result;

    public Mapping(DBObject key, DBCollection collection, DBObject result) {
      this.key = key;
      this.collection = collection;
      this.result = result;
    }

    @Override
    public String toString() {
      return "Mapping{" +
          "keyword=" + key +
          ", collection=" + collection +
          ", result=" + result +
          '}';
    }
  }

  private Group() {
  }

  @ThreadSafe
  enum GroupKeyword {
    MIN("$min") {
      @Override
      Object work(DBCollection coll, Object keywordParameter) {
        return minmax(coll, keywordParameter, 1);
      }
    },
    MAX("$max") {
      @Override
      Object work(DBCollection coll, Object keywordParameter) {
        return minmax(coll, keywordParameter, -1);
      }
    },
    FIRST("$first", true) {
      @Override
      Object work(DBCollection coll, Object keywordParameter) {
        return firstlast(coll, keywordParameter, true);
      }
    },
    LAST("$last", true) {
      @Override
      Object work(DBCollection coll, Object keywordParameter) {
        return firstlast(coll, keywordParameter, false);
      }
    },
    AVG("$avg") {
      @Override
      Object work(DBCollection coll, Object keywordParameter) {
        return avg(coll, keywordParameter);
      }
    },
    SUM("$sum") {
      @Override
      Object work(DBCollection coll, Object keywordParameter) {
        return sum(coll, keywordParameter);
      }
    },
    PUSH("$push") {
      @Override
      Object work(DBCollection coll, Object keywordParameter) {
        return pushAddToSet(coll, keywordParameter, false);
      }
    },
    ADD_TO_SET("$addToSet") {
      @Override
      Object work(DBCollection coll, Object keywordParameter) {
        return pushAddToSet(coll, keywordParameter, true);
      }
    };

    private final String keyword;

    private final boolean canReturnNull;

    GroupKeyword(String keyword) {
      this(keyword, false);
    }

    GroupKeyword(String keyword, boolean canReturnNull) {
      this.keyword = keyword;
      this.canReturnNull = canReturnNull;
    }

    abstract Object work(DBCollection coll, Object keywordParameter);

    public Object apply(DBCollection coll, DBObject parameter) {
      return work(coll, parameter.get(keyword));
    }

    public boolean canApply(DBObject parameter) {
      return parameter.containsField(keyword);
    }

    public boolean isCanReturnNull() {
      return canReturnNull;
    }

    public String getKeyword() {
      return keyword;
    }
  }

  @Override
  public DBCollection apply(DB originalDB, DBCollection coll, DBObject object) {
    DBObject group = ExpressionParser.toDbObject(object.get(getKeyword()));

    if (!group.containsField(FongoDBCollection.ID_FIELD_NAME)) {
      fongo.errorResult(15955, "a group specification must include an _id").throwOnError();
    }
    Object id = group.removeField(FongoDBCollection.ID_FIELD_NAME);
    LOG.debug("group() for _id : {}", id);
    // Try to group in the mapping.
    Map<DBObject, Mapping> mapping = createMapping(coll, id);

    //noinspection unchecked
    for (Map.Entry<String, Object> entry : ((Set<Map.Entry<String, Object>>) group.toMap().entrySet())) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (ExpressionParser.isDbObject(value)) {
        DBObject objectValue = ExpressionParser.toDbObject(value);
        for (Map.Entry<DBObject, Mapping> entryMapping : mapping.entrySet()) {
          DBCollection workColl = entryMapping.getValue().collection;
          for (GroupKeyword keyword : GroupKeyword.values()) {
            if (keyword.canApply(objectValue)) {
              Object result = keyword.apply(workColl, objectValue);
              if (result != null || keyword.isCanReturnNull()) {
                LOG.debug("_id:{}, keyword:{}, result:{}", entryMapping.getKey(), key, result);
                entryMapping.getValue().result.put(key, result);
              } else {
                LOG.warn("result is null for entry {}", entry);
              }
              break;
            }
          }
        }
      }
    }

    coll = dropAndInsert(coll, new ArrayList<DBObject>());

    // Extract from mapping to do the result.
    for (Map.Entry<DBObject, Mapping> entry : mapping.entrySet()) {
      coll.insert(entry.getValue().result);
      entry.getValue().collection.drop();
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("group() : {} result : {}", object, coll.find().toArray());
    }
    return coll;
  }

  /**
   * Create mapping. Group result with a 'keyword'.
   *
   * @param coll collection to be mapped
   * @param id   id of the group
   * @return a (Criteria, Mapping) for the id.
   */
  private Map<DBObject, Mapping> createMapping(DBCollection coll, Object id) {
    Map<DBObject, Mapping> mapping = new HashMap<DBObject, Mapping>();
    List<DBObject> objects = coll.find().toArray();
    for (DBObject dbObject : objects) {
      DBObject criteria = criteriaForId(id, dbObject);
      if (!mapping.containsKey(criteria)) {
        // Return all object we can group
        List<DBObject> newCollection = coll.find(criteria).toArray();
        // Generate keyword
        DBObject key = keyForId(id, dbObject);
        // Save into mapping
        mapping.put(criteria, new Mapping(key, createAndInsert(newCollection), Util.clone(key)));
        LOG.trace("createMapping() new criteria : {}", criteria);
      }
    }
    return mapping;
  }

  /**
   * Get the keyword from the "_id".
   *
   * @param id
   * @param dbObject
   * @return
   */

  private DBObject keyForId(Object id, DBObject dbObject) {
    DBObject result = new BasicDBObject();
    if (ExpressionParser.isDbObject(id)) {
      //ex: { "state" : "$state" , "city" : "$city"}
      DBObject subKey = new BasicDBObject();
      //noinspection unchecked
      extractKeys(ExpressionParser.toDbObject(id), dbObject, subKey);
      result.put(FongoDBCollection.ID_FIELD_NAME, subKey);
    } else if (id != null) {
      String field = fieldName(id);
      result.put(FongoDBCollection.ID_FIELD_NAME, Util.extractField(dbObject, field));
    } else {
      result.put(FongoDBCollection.ID_FIELD_NAME, null);
    }
    LOG.debug("keyForId() id:{}, dbObject:{}, result:{}", id, dbObject, result);
    return result;
  }

  private void extractKeys(DBObject id, DBObject dbObject, DBObject subKey) {
    for (Map.Entry<String, Object> entry : (Set<Map.Entry<String, Object>>) id.toMap().entrySet()) {
      if (entry.getValue() instanceof DBObject) {
        DBObject keywordDBObject = (DBObject) entry.getValue();
        String keywordString = keywordDBObject.keySet().iterator().next();
        Keyword extracted = Keyword.keyword(keywordString);
        if (extracted != null) {
          subKey.put(entry.getKey(), extracted.apply(Util.extractField(dbObject, fieldName(keywordDBObject.get(keywordString)))));
        } else {
          LOG.error("cannot find keywork for {}", entry);
          throw new MongoException(15999, String.format("invalid operator '%s'", keywordString));
        }
      } else {
        subKey.put(entry.getKey(), Util.extractField(dbObject, fieldName(entry.getValue()))); // TODO : hierarchical, like "state" : {bar:"$foo"}
      }
    }
  }


  enum Keyword {
    // https://docs.mongodb.org/manual/reference/operator/aggregation-date/
    DAYOFYEAR("$dayOfYear", Calendar.DAY_OF_YEAR),
    DAYOFMONTH("$dayOfMonth", Calendar.DAY_OF_MONTH),
    DAYOFWEEK("$dayOfWeek", Calendar.DAY_OF_WEEK),
    YEAR("$year", Calendar.YEAR),
    MONTH("$month", Calendar.MONTH, 1),
    WEEK("$week", Calendar.WEEK_OF_YEAR, -1),
    HOUR("$hour", Calendar.HOUR_OF_DAY),
    MINUTE("$minute", Calendar.MINUTE),
    SECOND("$second", Calendar.SECOND),
    MILLISECOND("$millisecond", Calendar.MILLISECOND);
//    DATETOSTRING("$dateToString",);

    final String keyword;
    final int fromCalendar;
    final int modifier;

    Keyword(String keyword, int fromCalendar, int modifier) {
      this.keyword = keyword;
      this.fromCalendar = fromCalendar;
      this.modifier = modifier;
    }

    Keyword(String keyword, int fromCalendar) {
      this(keyword, fromCalendar, 0);
    }

    Object apply(Object value) {
      if (value == null) {
        return null;
      }
      Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ENGLISH);
      calendar.setTimeInMillis((((Date) value).getTime()));
      int extracted = calendar.get(fromCalendar) + modifier;
      return extracted;
    }

    static Keyword keyword(String word) {
      for (Keyword keyword : Keyword.values()) {
        if (keyword.keyword.equals(word)) {
          return keyword;
        }
      }
      return null;
    }
  }

  private DBObject criteriaForId(Object id, DBObject dbObject) {
    DBObject result = new BasicDBObject();
    if (ExpressionParser.isDbObject(id)) {
      //noinspection unchecked
      extractKeys(ExpressionParser.toDbObject(id), dbObject, result);
    } else if (id != null) {
      String field = fieldName(id);
      result.put(field, Util.extractField(dbObject, field));
    }
    LOG.debug("criteriaForId() id:{}, dbObject:{}, result:{}", id, dbObject, result);
    return result;
  }

  private static String fieldName(Object name) {
    String field = name.toString();
    if (name instanceof String) {
      if (name.toString().startsWith("$")) {
        field = name.toString().substring(1);
      }
    }
    return field;
  }

  /**
   * {@see http://docs.mongodb.org/manual/reference/aggregation/sum/#grp._S_sum}
   */
  private static Object sum(DBCollection coll, Object value) {
    Number result = null;
    if (value.toString().startsWith("$")) {
      String field = value.toString().substring(1);
      List<DBObject> objects = coll.find(null, new BasicDBObject(field, 1).append(FongoDBCollection.ID_FIELD_NAME, 0)).toArray();
      for (DBObject object : objects) {
        if (Util.containsField(object, field)) {
          if (result == null) {
            result = Util.extractField(object, field);
          } else {
            Number other = Util.extractField(object, field);
            result = addWithSameType(result, other);
          }
        }
      }
    } else {
      Number iValue = (Number) value;
      // TODO : handle null value ?
      if (iValue instanceof Float || iValue instanceof Double) {
        result = coll.count() * iValue.doubleValue();
      } else if (iValue instanceof Byte || iValue instanceof Short || iValue instanceof Integer) {
        result = intOrLong(coll.count() * iValue.longValue());
      } else if (iValue instanceof Long) {
        result = coll.count() * iValue.longValue();
      } else {
        LOG.warn("type of field not handled for sum:{}", result == null ? null : result.getClass());
      }
    }
    return result;
  }

  /**
   * return Integer if the parameter could be safely cast to an integer
   */
  private static Number intOrLong(long number) {
    if (number <= Integer.MAX_VALUE && number >= Integer.MIN_VALUE) {
      return (int) number;
    } else {
      return number;
    }
  }

  /**
   * {@see http://docs.mongodb.org/manual/reference/aggregation/avg/#grp._S_avg}
   *
   * @param coll  grouped collection to make the avg
   * @param value field to be averaged.
   * @return the average of the collection.
   */
  private static Double avg(DBCollection coll, Object value) {
    Number result = null;
    long count = 1;
    if (value.toString().startsWith("$")) {
      String field = value.toString().substring(1);
      List<DBObject> objects = coll.find(null, new BasicDBObject(field, 1).append(FongoDBCollection.ID_FIELD_NAME, 0)).toArray();
      for (DBObject object : objects) {
        LOG.debug("avg object {} ", object);

        if (Util.containsField(object, field)) {
          if (result == null) {
            result = Util.extractField(object, field);
          } else {
            count++;
            Number other = Util.extractField(object, field);
            result = addWithSameType(result, other);
          }
        }
      }
    } else {
      LOG.error("Sorry, doesn't know what to do...");
      return null;
    }
    // Always return double.
    return result == null ? null : (result.doubleValue() / (double) count);
  }

  /**
   * Return the first or the last of a collection.
   *
   * @param coll  collection who contains data.
   * @param value fieldname for searching.
   * @return
   */
  private static Object firstlast(DBCollection coll, Object value, boolean first) {
    LOG.debug("first({})/last({}) on {}", first, !first, value);
    Object result = null;
    if (value.toString().startsWith("$")) {
      String field = value.toString().substring(1);
      DBCursor cursor = coll.find();
      while (cursor.hasNext()) {
        result = Util.extractField(cursor.next(), field);
        if (first) {
          break;
        }
      }
    } else {
      LOG.error("Sorry, doesn't know what to do...");
    }

    LOG.debug("first({})/last({}) on {}, result : {}", first, !first, value, result);
    return result;
  }

  /**
   * Return the first or the last of a collection.
   *
   * @param coll
   * @param value fieldname for searching.
   * @return
   */
  private static BasicDBList pushAddToSet(DBCollection coll, Object value, boolean uniqueness) {
    LOG.debug("pushAddToSet() on {}", value);
    BasicDBList result = null;
    if (value.toString().startsWith("$")) {
      result = new BasicDBList();
      String field = value.toString().substring(1);
      DBCursor cursor = coll.find();
      while (cursor.hasNext()) {
        Object fieldValue = Util.extractField(cursor.next(), field);
        if (!uniqueness || !result.contains(fieldValue)) {
          result.add(fieldValue);
        }
      }
    } else {
      LOG.error("Sorry, doesn't know what to do...");
    }

    LOG.debug("pushAddToSet() on {}, result : {}", value, result);
    return result;
  }

  /**
   * Return the min or the max of a collection.
   *
   * @param coll
   * @param value
   * @param valueComparable 0 for equals, -1 for min, +1 for max
   * @return
   */
  private static Object minmax(DBCollection coll, Object value, int valueComparable) {
    if (value.toString().startsWith("$")) {
      String field = value.toString().substring(1);
      DBCursor cursor = coll.find();
      Comparable comparable = null;
      while (cursor.hasNext()) {
        DBObject object = cursor.next();
        if (Util.containsField(object, field)) {
          if (comparable == null) {
            comparable = Util.extractField(object, field);
          } else {
            Comparable other = Util.extractField(object, field);
            if (comparable.compareTo(other) == valueComparable) {
              comparable = other;
            }
          }
        }
      }
      return comparable;
    } else {
      LOG.error("Sorry, doesn't know what to do...");
    }
    return null;
  }

  /**
   * Add two number in the same type.
   *
   * @param result
   * @param other
   * @return
   */
  private static Number addWithSameType(Number result, Number other) {
    if (result instanceof Float) {
      result = Float.valueOf(result.floatValue() + other.floatValue());
    } else if (result instanceof Double) {
      result = Double.valueOf(result.doubleValue() + other.doubleValue());
    } else if (result instanceof Integer) {
      result = Integer.valueOf(result.intValue() + other.intValue());
    } else if (result instanceof Long) {
      result = Long.valueOf(result.longValue() + other.longValue());
    } else {
      LOG.warn("type of field not handled for sum : {}", result.getClass());
    }
    return result;
  }

// --Commented out by Inspection START (05/11/13 12:10):
//  private static Number returnSameType(Number type, Number other) {
//    if (type instanceof Float) {
//      return Float.valueOf(other.floatValue());
//    } else if (type instanceof Double) {
//      return Double.valueOf(other.doubleValue());
//    } else if (type instanceof Integer) {
//      return Integer.valueOf(other.intValue());
//    } else if (type instanceof Long) {
//      return Long.valueOf(other.longValue());
//    } else {
//      LOG.warn("type of field not handled for sum : {}", type.getClass());
//    }
//    return other;
//  }
// --Commented out by Inspection STOP (05/11/13 12:10)

  @Override
  public String getKeyword() {
    return "$group";
  }

}
