package com.github.fakemongo.impl.aggregation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fakemongo.impl.ExpressionParser;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

/**
 * @author Kollivakkam Raghavan
 * @created 4/22/2016
 */
public class Lookup extends PipelineKeyword {

  private static final Logger LOG = LoggerFactory.getLogger(Lookup.class);

  public static final Lookup INSTANCE = new Lookup();
  public static final String ID = "_id";

  @Override
  public DBCollection apply(DB originalDB, DBCollection parentColl, DBObject object) {
    DBObject lookup = ExpressionParser.toDbObject(object.get(getKeyword()));
    List<DBObject> parentItems = performLookup(originalDB, parentColl, lookup);
    return dropAndInsert(parentColl, parentItems);
  }

  @Override
  public String getKeyword() {
    return "$lookup";
  }

  private List<DBObject> performLookup(DB originalDB, DBCollection parentColl, DBObject lookup) {
    String from = (String) lookup.get("from");
    String localField = (String) lookup.get("localField");
    String foreignField = (String) lookup.get("foreignField");
    String as = (String) lookup.get("as");
    LOG.debug("Value {} will be returned from {} in parent collection {}.  Local field {} will be joined with {}",
              as, from, parentColl.getName(), localField, foreignField);

    DBCursor parentItems = parentColl.find();
    // can't use lambdas
    Iterator<DBObject> iterator = parentItems.iterator();
    Map<Object, List<DBObject>> parentMap = new HashMap<Object, List<DBObject>>();
    List<DBObject> parentsWithLocalField = new ArrayList<DBObject>();
    List<DBObject> parentsWithMissingLocalField = new ArrayList<DBObject>();
    // go through all parent items - put a list of DBObjects for the children
    while (iterator.hasNext()) {
      DBObject parentItem = iterator.next();
      Object localFieldValue = parentItem.get(localField);
      if (localFieldValue == null || "".equals(localField.toString().trim())) {
        parentItem.put(as, new ArrayList<DBObject>());
        parentsWithMissingLocalField.add(parentItem);
      }
      else {
        List<DBObject> childItems = (List<DBObject>) parentItem.get(as);
        if (childItems == null) {
          childItems = new ArrayList<DBObject>();
          parentItem.put(as, childItems);
        }
        if(!parentMap.containsKey(localFieldValue)) {
            parentMap.put(localFieldValue, new ArrayList<DBObject>());
        }
        parentMap.get(localFieldValue).add(parentItem);
        parentsWithLocalField.add(parentItem);
      }
    }
    // now loop through the children item and add them to the parent
    DBCollection childColl = originalDB.getCollection(from);

    DBCursor childItems = childColl.find();
    Iterator<DBObject> childIterator = childItems.iterator();
    while (childIterator.hasNext()) {
      DBObject childItem = childIterator.next();
      Object parentOid = childItem.get(foreignField);
      if (parentOid == null) {
        LOG.warn("Ignoring null parent id");
      }
      else {
        if(parentMap.containsKey(parentOid)) {
          for(DBObject parent: parentMap.get(parentOid)) {
            LOG.debug("Adding child with id {} to parent wth id {}", childItem.get(ID), parentOid);
            List<DBObject> childObjects = (List<DBObject>) parent.get(as);
            Assert.assertNotNull("Unexpected null value", childObjects);
            childObjects.add(childItem);
          }
        } else {
          LOG.warn("Ignoring missing parent with id {}", parentOid);
        }
      }
    }
    List<DBObject> retval = new ArrayList<DBObject>(parentsWithLocalField.size() + parentsWithMissingLocalField.size());
    retval.addAll(parentsWithLocalField);
    retval.addAll(parentsWithMissingLocalField);
    return retval;
  }

}
