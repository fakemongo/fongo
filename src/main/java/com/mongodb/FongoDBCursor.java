package com.mongodb;

import com.github.fakemongo.impl.Util;
import com.mongodb.client.model.FindOptions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * fongo override of com.mongodb.DBCursor
 * you shouldn't need to use this class directly
 */
public class FongoDBCursor extends DBCursor {
  private final static Logger LOG = LoggerFactory.getLogger(FongoDBCursor.class);

  private final FongoDBCollection dbCollection;
  private final DBObject query;
  private final DBObject projection;
  private final FindOptions findOptions;
  private final DBObject modifiers;
  private DBObject sort;
  private int numSeen;
  private boolean closed;

  private DBObject currentObject;
  private List<DBObject> objects = null;
  private Iterator<DBObject> iterator;

  public FongoDBCursor(FongoDBCollection fongoDBCollection, DBObject query, DBObject projection) {
    this(fongoDBCollection, query, projection, new FindOptions(), new BasicDBObject(), null);
  }

  private FongoDBCursor(FongoDBCollection collection, DBObject query, DBObject projection, FindOptions findOptions, DBObject modifiers, DBObject sort) {
    super(collection, query, projection, collection.getReadPreference());
    this.dbCollection = collection;
    this.query = query;
    this.projection = projection;
    this.findOptions = findOptions;
    this.modifiers = modifiers;
    this.sort = sort;
  }

  private void fetch() {
    if (this.objects == null) {
      objects = new ArrayList<DBObject>();

      final DBObject q;
      if (this.query != null && this.query.containsField("$query")) {
        q = Util.clone(query);
      } else {
        q = new BasicDBObject("$query", Util.clone(this.query));
      }
      if (sort != null) {
        q.put("$orderby", sort);
      }
      q.putAll(modifiers);
      objects.addAll(dbCollection.__find(q, projection, this.findOptions.getSkip(), this.findOptions.getBatchSize(),
          this.getLimit(), this.getOptions(), getReadPreference(), null));
      iterator = objects.iterator();
    }
  }


  private DBObject currentObject(final DBObject newCurrentObject) {
    if (newCurrentObject != null) {
      currentObject = newCurrentObject;
      numSeen++;

      if (projection != null && !(projection.keySet().isEmpty())) {
        currentObject.markAsPartialObject();
      }
    }
    return newCurrentObject;
  }

  @Override
  public synchronized List<DBObject> toArray(int max) {
    fetch();
    return objects;
  }

  @Override
  public boolean hasNext() {
    if (closed) {
      throw new IllegalStateException("Cursor has been closed");
    }
    fetch();
    return iterator.hasNext();
  }

  @Override
  public DBObject next() {
    if (closed) {
      throw new IllegalStateException("Cursor has been closed");
    }
    fetch();
    this.numSeen++;
    return currentObject(iterator.next());
  }

  @Override
  public DBObject tryNext() {
    if (closed) {
      throw new IllegalStateException("Cursor has been closed");
    }
    return next();
  }

  @Override
  public DBObject curr() {
    return currentObject;
  }

  @Override
  public int length() {
    fetch();
    return this.objects.size();
  }

  /**
   * Creates a copy of an existing database cursor. The new cursor is an iterator, even if the original was an array.
   *
   * @return the new cursor
   */
  @Override
  public DBCursor copy() {
    return new FongoDBCursor(this.dbCollection, this.query, this.projection, new FindOptions(this.findOptions), Util.clone(this.modifiers), Util.clone(this.sort));
  }

  @Override
  public int getLimit() {
    return findOptions.getLimit();
  }

  /**
   * Gets the batch size.
   *
   * @return the batch size
   */
  @Override
  public int getBatchSize() {
    return findOptions.getBatchSize();
  }

  @Override
  public DBCursor comment(String comment) {
    return super.comment(comment);
  }

  @Override
  public DBCursor maxScan(int max) {
    modifiers.put("$maxScan", max);
    return super.maxScan(max);
  }

  @Override
  public DBCursor max(DBObject max) {
    modifiers.put("$max", max);
    return super.max(max);
  }

  @Override
  public DBCursor min(DBObject min) {
    modifiers.put("$min", min);
    return super.min(min);
  }

  @Override
  public DBCursor returnKey() {
    modifiers.put("$returnKey", true);
    return super.returnKey();
  }

  @Override
  public DBCursor maxTime(long maxTime, TimeUnit timeUnit) {
    findOptions.maxTime(maxTime, timeUnit);
    return super.maxTime(maxTime, timeUnit);
  }

  @Override
  public DBCursor snapshot() {
    modifiers.put("$snapshot", true);
    return super.snapshot();
  }

  @Override
  public DBCursor sort(DBObject orderBy) {
    this.sort = orderBy;
    return super.sort(orderBy);
  }

  @Override
  public DBCursor limit(int limit) {
    findOptions.limit(limit);

    return super.limit(limit);
  }

  @Override
  public DBCursor batchSize(int numberOfElements) {
    findOptions.batchSize(numberOfElements);
    return super.batchSize(numberOfElements);
  }

  /**
   * Discards a given number of elements at the beginning of the cursor.
   *
   * @param numberOfElements the number of elements to skip
   * @return a cursor pointing to the new first element of the results
   * @throws IllegalStateException if the cursor has started to be iterated through
   */
  @Override
  public DBCursor skip(int numberOfElements) {
    findOptions.skip(numberOfElements);
    return super.skip(numberOfElements);
  }

  @Override
  public long getCursorId() {
    return super.getCursorId();
  }

  /**
   * Returns the number of objects through which the cursor has iterated.
   *
   * @return the number of objects seen
   */
  @Override
  public int numSeen() {
    return numSeen;
  }

  @Override
  public void close() {
    this.closed = true;
    super.close();
  }

  /**
   * Declare that this query can run on a secondary server.
   *
   * @return a copy of the same cursor (for chaining)
   * @see ReadPreference#secondaryPreferred()
   * @deprecated Replaced with {@link ReadPreference#secondaryPreferred()}
   */
  @Override
  public DBCursor slaveOk() {
    return super.slaveOk();
  }

  @Override
  public int count() {
    return super.count();
  }

  @Override
  public DBObject one() {
    return super.one();
  }

  @Override
  public int itcount() {
    return super.itcount();
  }

  /**
   * Counts the number of objects matching the query this does take limit/skip into consideration
   *
   * @return the number of objects
   * @throws MongoException if the operation failed
   * @see #count()
   */
  @Override
  public int size() {
    return super.size();
  }


}