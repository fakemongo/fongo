package com.github.fakemongo.impl.aggregation;

import com.github.fakemongo.Fongo;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.FongoDB;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * User: william Date: 24/07/13
 */
public abstract class PipelineKeyword {

  protected static final FongoDB fongo = new Fongo("aggregation_pipeline").getDB("pipeline");


  /**
   * Apply the keyword on the collection
   *
   * @param originalDB original DB from collection.
   * @param coll       collection to be processed (will be destroyed).
   * @param object     parameters for keyword.
   * @return a new collection in result.
   */
  public abstract DBCollection apply(DB originalDB, DBCollection coll, DBObject object);

  /**
   * Return the keyword in the pipeline (like $sort, $group...).
   */
  public abstract String getKeyword();

  /**
   * Drop collection and create new one with objects.
   *
   * @param coll
   * @param objects
   * @return the new collection.
   */
  protected DBCollection dropAndInsert(DBCollection coll, List<DBObject> objects) {
    coll.drop();
    return createAndInsert(objects);
  }

  static void errorResult(DBCollection coll, int code, String err) {
    ((FongoDB) coll.getDB()).notOkErrorResult(code, err).throwOnError();
  }

  protected DBCollection createAndInsert(List<DBObject> objects) {
    DBCollection coll = fongo.doGetCollection(UUID.randomUUID().toString(), true);
    coll.insert(objects);
    return coll;
  }

  public boolean canApply(DBObject object) {
    return object.containsField(getKeyword());
  }

  static <T> void validateNull(T param, String msg) {
    if(param == null) {
      fongo.errorResult(15955, msg).throwOnError();
    }
  }

  static void validateTrue(boolean expr, String msg) {
    if(!expr) {
      fongo.errorResult(15955, msg).throwOnError();
    }
  }
}
