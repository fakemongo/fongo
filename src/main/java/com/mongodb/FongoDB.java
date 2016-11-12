package com.mongodb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fakemongo.Fongo;
import com.github.fakemongo.impl.Aggregator;
import com.github.fakemongo.impl.ExpressionParser;
import com.github.fakemongo.impl.geo.GeoUtil;
import com.mongodb.util.JSON;
import com.vividsolutions.jts.geom.Coordinate;

/**
 * fongo override of com.mongodb.DB
 * you shouldn't need to use this class directly
 *
 * @author jon
 */
public class FongoDB extends DB {
  private static final Logger LOG = LoggerFactory.getLogger(FongoDB.class);
  private static final String SYSTEM_NAMESPACES = "system.namespaces";
  private static final String SYSTEM_INDEXES = "system.indexes";
  private static final String SYSTEM_USERS = "system.users";

  private final Map<String, FongoDBCollection> collMap = new ConcurrentHashMap<String, FongoDBCollection>();
  private final Set<String> namespaceDeclared = Collections.synchronizedSet(new LinkedHashSet<String>());
  final Fongo fongo;

  public FongoDB(Fongo fongo, String name) {
    super(fongo.getMongo(), name);
    this.fongo = fongo;
    doGetCollection(SYSTEM_USERS);
    doGetCollection(SYSTEM_INDEXES);
    doGetCollection(SYSTEM_NAMESPACES);
  }

  @Override
  public synchronized DBCollection createCollection(final String collectionName, final DBObject options) {
    // See getCreateCollectionOperation()
    if (options != null) {
      if (options.get("size") != null && !(options.get("size") instanceof Number)) {
        throw new IllegalArgumentException("'size' should be Number");
      }
      if (options.get("max") != null && !(options.get("max") instanceof Number)) {
        throw new IllegalArgumentException("'max' should be Number");
      }
      if (options.get("capped") != null && !(options.get("capped") instanceof Boolean)) {
        throw new IllegalArgumentException("'capped' should be Boolean");
      }
      if (options.get("autoIndexId") != null && !(options.get("capped") instanceof Boolean)) {
        throw new IllegalArgumentException("'capped' should be Boolean");
      }
      if (options.get("storageEngine") != null && !(options.get("storageEngine") instanceof DBObject)) {
        throw new IllegalArgumentException("storageEngine' should be DBObject");
      }
    }

    if (this.collMap.containsKey(collectionName)) {
      this.notOkErrorResult("collection already exists").throwOnError();
    }

    final DBCollection collection = getCollection(collectionName);
    this.addCollection((FongoDBCollection) collection);
    return collection;
  }

  @Override
  public FongoDBCollection getCollection(final String name) {
    return doGetCollection(name);
  }

  @Override
  protected synchronized FongoDBCollection doGetCollection(String name) {
    return doGetCollection(name, false);
  }

  /**
   * Only for aggregation.
   */
  public synchronized FongoDBCollection doGetCollection(String name, boolean idIsNotUniq) {
    FongoDBCollection coll = collMap.get(name);
    if (coll == null) {
      coll = new FongoDBCollection(this, name, idIsNotUniq);
      collMap.put(name, coll);
    }
    return coll;
  }

  private DBObject findAndModify(String collection, DBObject query, DBObject sort, boolean remove, DBObject update, boolean returnNew, DBObject fields, boolean upsert) {
    FongoDBCollection coll = doGetCollection(collection);

    return coll.findAndModify(query, fields, sort, remove, update, returnNew, upsert);
  }

  private List<DBObject> doAggregateCollection(String collection, List<DBObject> pipeline) {
    FongoDBCollection coll = doGetCollection(collection);
    Aggregator aggregator = new Aggregator(this, coll, pipeline);

    return aggregator.computeResult();
  }

  private MapReduceOutput doMapReduce(String collection, String map, String reduce, String finalize, Map<String, Object> scope, DBObject out, DBObject query, DBObject sort, Number limit) {
    FongoDBCollection coll = doGetCollection(collection);
    MapReduceCommand mapReduceCommand = new MapReduceCommand(coll, map, reduce, null, null, query);
    mapReduceCommand.setSort(sort);
    if (limit != null) {
      mapReduceCommand.setLimit(limit.intValue());
    }
    mapReduceCommand.setFinalize(finalize);
    mapReduceCommand.setOutputDB((String) out.get("db"));
    mapReduceCommand.setScope(scope);
    return coll.mapReduce(mapReduceCommand);
  }

  private List<DBObject> doGeoNearCollection(String collection, Coordinate near, DBObject query, Number limit, Number maxDistance, boolean spherical) {
    FongoDBCollection coll = doGetCollection(collection);
    return coll.geoNear(near, query, limit, maxDistance, spherical);
  }

  //see http://docs.mongodb.org/manual/tutorial/search-for-text/ for mongodb
  private DBObject doTextSearchInCollection(String collection, String search, Integer limit, DBObject project) {
    FongoDBCollection coll = doGetCollection(collection);
    return coll.text(search, limit, project);
  }

//  @Override
//  public Set<String> getCollectionNames() throws MongoException {
//    Set<String> names = new HashSet<String>();
//    for (FongoDBCollection fongoDBCollection : collMap.values()) {
//      int expectedCount = 0;
//      if (fongoDBCollection.getName().startsWith("system.indexes")) {
//        expectedCount = 1;
//      }
//
//      if (fongoDBCollection.count() > expectedCount) {
//        names.add(fongoDBCollection.getName());
//      }
//    }
//
//    return names;
//  }

  @Override
  public DB getSisterDB(String name) {
    return fongo.getDB(name);
  }

  @Override
  public WriteConcern getWriteConcern() {
    return fongo.getWriteConcern();
  }

  @Override
  public ReadConcern getReadConcern() {
    return fongo.getReadConcern();
  }

  @Override
  public ReadPreference getReadPreference() {
    return ReadPreference.primaryPreferred();
  }

  @Override
  public synchronized void dropDatabase() throws MongoException {
    this.fongo.dropDatabase(this.getName());
    for (FongoDBCollection c : new ArrayList<FongoDBCollection>(collMap.values())) {
      c.drop();
    }
  }

  // TODO WDEL
//  @Override
//  CommandResult doAuthenticate(MongoCredential credentials) {
//    this.mongoCredential = credentials;
//    return okResult();
//  }
//
//  @Override
//  MongoCredential getAuthenticationCredentials() {
//    return this.mongoCredential;
//  }

  /**
   * Executes a database command.
   *
   * @param cmd            dbobject representing the command to execute
   * @param readPreference ReadPreferences for this command (nodes selection is the biggest part of this)
   * @return result of command from the database
   * @throws MongoException
   * @dochub commands
   * @see <a href="http://mongodb.onconfluence.com/display/DOCS/List+of+Database+Commands">List of Commands</a>
   */
  @Override
  public CommandResult command(final DBObject cmd, final ReadPreference readPreference, final DBEncoder encoder) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Fongo got command " + cmd);
    }
    if (cmd.containsField("$eval")) {
      CommandResult commandResult = okResult();
      commandResult.append("retval", "null");
      return commandResult;
    } else if (cmd.containsField("getlasterror") || cmd.containsField("getLastError")) {
      return okResult();
    } else if (cmd.containsField("fsync")) {
      return okResult();
    } else if (cmd.containsField("drop")) {
      this.getCollection(cmd.get("drop").toString()).drop();
      return okResult();
    } else if (cmd.containsField("create")) {
      String collectionName = (String) cmd.get("create");
      doGetCollection(collectionName);
      return okResult();
    } else if (cmd.containsField("count")) {
      String collectionName = (String) cmd.get("count");
      Number limit = (Number) cmd.get("limit");
      Number skip = (Number) cmd.get("skip");
      long result = doGetCollection(collectionName).getCount(
          ExpressionParser.toDbObject(cmd.get("query")),
          null,
          limit == null ? 0L : limit.longValue(),
          skip == null ? 0L : skip.longValue());
      CommandResult okResult = okResult();
      okResult.append("n", (double) result);
      return okResult;
    } else if (cmd.containsField("deleteIndexes")) {
      String collectionName = (String) cmd.get("deleteIndexes");
      String indexName = (String) cmd.get("index");
      if ("*".equals(indexName)) {
        doGetCollection(collectionName)._dropIndexes();
      } else {
        doGetCollection(collectionName)._dropIndex(indexName);
      }
      return okResult();
    } else if (cmd.containsField("aggregate")) {
      @SuppressWarnings(
          "unchecked") List<DBObject> result = doAggregateCollection((String) cmd.get("aggregate"), (List<DBObject>) cmd.get("pipeline"));
      if (result == null) {
        return notOkErrorResult("can't aggregate");
      }
      CommandResult okResult = okResult();
      BasicDBList list = new BasicDBList();
      list.addAll(result);
      okResult.put("result", list);
      return okResult;
    } else if (cmd.containsField("findAndModify")) {
      return runFindAndModify(cmd, "findAndModify");
    } else if (cmd.containsField("findandmodify")) {
      return runFindAndModify(cmd, "findandmodify");
    } else if (cmd.containsField("ping")) {
      return okResult();
    } else if (cmd.containsField("validate")) {
      return okResult();
    } else if (cmd.containsField("buildInfo") || cmd.containsField("buildinfo")) {
      CommandResult okResult = okResult();
      List<Integer> versionList = fongo.getServerVersion().getVersionList();
      okResult.put("version", versionList.get(0) + "." + versionList.get(1) + "." + versionList.get(2));
      okResult.put("maxBsonObjectSize", 16777216);
      return okResult;
    } else if (cmd.containsField("forceerror")) {
      // http://docs.mongodb.org/manual/reference/command/forceerror/
      return notOkErrorResult(10038, null, "exception: forced error");
    } else if (cmd.containsField("mapreduce")) {
      return runMapReduce(cmd, "mapreduce");
    } else if (cmd.containsField("mapReduce")) {
      return runMapReduce(cmd, "mapReduce");
    } else if (cmd.containsField("geoNear")) {
      // http://docs.mongodb.org/manual/reference/command/geoNear/
      // TODO : handle "num" (override limit)
      try {
        List<DBObject> result = doGeoNearCollection((String) cmd.get("geoNear"),
            GeoUtil.coordinate(cmd.get("near")),
            ExpressionParser.toDbObject(cmd.get("query")),
            (Number) cmd.get("limit"),
            (Number) cmd.get("maxDistance"),
            Boolean.TRUE.equals(cmd.get("spherical")));
        if (result == null) {
          return notOkErrorResult("can't geoNear");
        }
        CommandResult okResult = okResult();
        BasicDBList list = new BasicDBList();
        list.addAll(result);
        okResult.put("results", list);
        return okResult;
      } catch (MongoException me) {
        return errorResult(me.getCode(), me.getMessage());
      }
    } else if (cmd.containsField("renameCollection")) {
      if (!this.getName().equals("admin")) {
        return notOkErrorResult(null, "renameCollection may only be run against the admin database.");
      }
      final String renameCollection = (String) cmd.get("renameCollection");
      final String to = (String) cmd.get("to");
      final Boolean dropTarget = (Boolean) cmd.get("dropTarget");
      this.renameCollection(renameCollection, to, dropTarget);
      return okResult();
    } else if (cmd.containsField("insert")) {
      return runInsert(cmd);
    } else if (cmd.containsField("delete")) {
      return runDelete(cmd);
    } else {
      String collectionName = ((Map.Entry<String, DBObject>) cmd.toMap().entrySet().iterator().next()).getKey();
      if (collectionExists(collectionName)) {
        DBObject newCmd = ExpressionParser.toDbObject(cmd.get(collectionName));
        if ((newCmd.containsField("text") && (ExpressionParser.toDbObject(newCmd.get("text"))).containsField("search"))) {
          DBObject resp = doTextSearchInCollection(collectionName,
              (String) (ExpressionParser.toDbObject(newCmd.get("text"))).get("search"),
              (Integer) (ExpressionParser.toDbObject(newCmd.get("text"))).get("limit"),
              ExpressionParser.toDbObject(((DBObject) newCmd.get("text")).get("project")));
          if (resp == null) {
            return notOkErrorResult("can't perform text search");
          }
          CommandResult okResult = okResult();
          okResult.put("results", resp.get("results"));
          okResult.put("stats", resp.get("stats"));
          return okResult;
        } else if ((newCmd.containsField("$text") && (ExpressionParser.toDbObject(newCmd.get("$text"))).containsField("$search"))) {
          DBObject resp = doTextSearchInCollection(collectionName,
              (String) (ExpressionParser.toDbObject(newCmd.get("$text"))).get("$search"),
              (Integer) (ExpressionParser.toDbObject(newCmd.get("text"))).get("limit"),
              ExpressionParser.toDbObject(((DBObject) newCmd.get("text")).get("project")));
          if (resp == null) {
            return notOkErrorResult("can't perform text search");
          }
          CommandResult okResult = okResult();
          okResult.put("results", resp.get("results"));
          okResult.put("stats", resp.get("stats"));
          return okResult;
        }
      }
    }
    String command = cmd.toString();
    if (!cmd.keySet().isEmpty()) {
      command = cmd.keySet().iterator().next();
    }
    return notOkErrorResult(null, "no such command: '" + command + "', bad cmd: '" + JSON.serialize(cmd) + "'");
  }

  public void renameCollection(String renameCollection, String to, Boolean dropTarget) {
    String dbRename = renameCollection.substring(0, renameCollection.indexOf('.'));
    String collectionRename = renameCollection.substring(renameCollection.indexOf('.') + 1);
    String dbTo = to.substring(0, to.indexOf('.'));
    String collectionTo = to.substring(to.indexOf('.') + 1);
    FongoDBCollection rename = (FongoDBCollection) this.fongo.getDB(dbRename).getCollection(collectionRename);
    FongoDBCollection fongoDBCollection = new FongoDBCollection((FongoDB) fongo.getDB(dbTo), collectionTo);
    fongoDBCollection.insert(rename.find().toArray());

    for (DBObject index : rename.getIndexInfo()) {
      if (!index.get("name").equals("_id_")) {
        System.out.println(index);
        Boolean unique = (Boolean) index.get("unique");
        fongoDBCollection.createIndex(ExpressionParser.toDbObject(index.get("key")), (String) index.get("name"), unique == null ? false : unique);
      }
    }

//    for (IndexAbstract index : rename.getIndexes()) {
//      fongoDBCollection.createIndex(index.getKeys(), new BasicDBObject("unique", index.isUnique()));
//    }

    rename.dropIndexes();
    rename.remove(new BasicDBObject());
  }

  /**
   * Returns a set containing the names of all collections in this database.
   *
   * @return the names of collections in this database
   * @throws com.mongodb.MongoException
   */
  @Override
  public Set<String> getCollectionNames() {
    List<String> collectionNames = new ArrayList<String>();
    Iterator<DBObject> collections = getCollection(SYSTEM_NAMESPACES).find(new BasicDBObject());
    while (collections.hasNext()) {
      String collectionName = collections.next().get("name").toString();
      if (!collectionName.contains("$")) {
        collectionNames.add(collectionName.substring(getName().length() + 1));
      }
    }

    if (!mustContainsSystemIndexes()) {
      collectionNames.remove("system.indexes");
    }

    Collections.sort(collectionNames);
    return new LinkedHashSet<String>(collectionNames);
  }

  private boolean mustContainsSystemIndexes() {
    return this.fongo.getServerVersion().compareTo(Fongo.V3_2_SERVER_VERSION) < 0;
  }

  public CommandResult okResult() {
    final BsonDocument result = new BsonDocument("ok", new BsonDouble(1.0));
    return new CommandResult(result, fongo.getServerAddress());
  }

  private BsonDocument bsonResultNotOk(int code, String err) {
    final BsonDocument result = new BsonDocument("ok", new BsonDouble(0.0));
    if (err != null) {
      result.put("err", new BsonString(err));
    }
    result.put("code", new BsonInt32(code));
    return result;
  }

  public CommandResult notOkErrorResult(String err) {
    return notOkErrorResult(err, null);
  }

  public CommandResult notOkErrorResult(String err, String errmsg) {
    final BsonDocument result = new BsonDocument("ok", new BsonDouble(0.0));
    if (err != null) {
      result.put("err", new BsonString(err));
    }
    if (errmsg != null) {
      result.put("errmsg", new BsonString(errmsg));
    }
    return new CommandResult(result, fongo.getServerAddress());
  }

  public CommandResult notOkErrorResult(int code, String err) {
    final BsonDocument result = bsonResultNotOk(code, err);
    return new CommandResult(result, fongo.getServerAddress());
  }

  public WriteConcernException writeConcernException(int code, String err) {
    final BsonDocument result = bsonResultNotOk(code, err);
    return new WriteConcernException(result, fongo.getServerAddress(), WriteConcernResult.unacknowledged());
  }

  public WriteConcernException duplicateKeyException(int code, String err) {
    final BsonDocument result = bsonResultNotOk(code, err);
    return new DuplicateKeyException(result, fongo.getServerAddress(), WriteConcernResult.unacknowledged());
  }

  public MongoCommandException mongoCommandException(int code, String err) {
    final BsonDocument result = bsonResultNotOk(code, err);
    return new MongoCommandException(result, fongo.getServerAddress());
  }

  public CommandResult notOkErrorResult(int code, String err, String errmsg) {
    CommandResult result = notOkErrorResult(err, errmsg);
    result.put("code", code);
    return result;
  }

  public CommandResult errorResult(int code, String err) {
    final BsonDocument result = new BsonDocument();
    if (err != null) {
      result.put("err", new BsonString(err));
    }
    result.put("code", new BsonInt32(code));
    result.put("ok", BsonBoolean.FALSE);
    return new CommandResult(result, fongo.getServerAddress());
  }

  @Override
  public String toString() {
    return "FongoDB." + this.getName();
  }

  synchronized void removeCollection(FongoDBCollection collection) {
    this.collMap.remove(collection.getName());
    this.getCollection(SYSTEM_NAMESPACES).remove(new BasicDBObject("name", collection.getFullName()));
    this.namespaceDeclared.remove(collection.getFullName());
  }

  void addCollection(FongoDBCollection collection) {
    this.collMap.put(collection.getName(), collection);
    if (!collection.getName().startsWith("system.")) {
      if (!this.namespaceDeclared.contains(collection.getFullName())) {
        final FongoDBCollection dbCollectionNamespace = this.getCollection(SYSTEM_NAMESPACES);
        dbCollectionNamespace.insert(new BasicDBObject("name", collection.getFullName()).append("options", new BasicDBObject()));
        if (this.namespaceDeclared.isEmpty() && dbCollectionNamespace.count(new BasicDBObject("name", collection.getDB().getName() + ".system.indexes")) == 0) {
          dbCollectionNamespace.insert(new BasicDBObject("name", collection.getDB().getName() + ".system.indexes").append("options", new BasicDBObject()));
        }
        this.namespaceDeclared.add(collection.getFullName());
      }
    }
  }

  private CommandResult runFindAndModify(DBObject cmd, String key) {
    if (!cmd.containsField("remove") && !cmd.containsField("update")) {
      return notOkErrorResult(null, "need remove or update");
    }

    DBObject result = findAndModify(
        (String) cmd.get(key),
        ExpressionParser.toDbObject(cmd.get("query")),
        ExpressionParser.toDbObject(cmd.get("sort")),
        Boolean.TRUE.equals(cmd.get("remove")),
        ExpressionParser.toDbObject(cmd.get("update")),
        Boolean.TRUE.equals(cmd.get("new")),
        ExpressionParser.toDbObject(cmd.get("fields")),
        Boolean.TRUE.equals(cmd.get("upsert")));
    CommandResult okResult = okResult();
    okResult.put("value", result);
    return okResult;
  }

  private CommandResult runMapReduce(DBObject cmd, String key) {
    MapReduceOutput result = doMapReduce(
        (String) cmd.get(key),
        (String) cmd.get("map"),
        (String) cmd.get("reduce"),
        (String) cmd.get("finalize"),
        (Map) cmd.get("scope"),
        ExpressionParser.toDbObject(cmd.get("out")),
        ExpressionParser.toDbObject(cmd.get("query")),
        ExpressionParser.toDbObject(cmd.get("sort")),
        (Number) cmd.get("limit"));
    if (result == null) {
      return notOkErrorResult("can't mapReduce");
    }
    CommandResult okResult = okResult();
    if (result.results() instanceof List) {
      // INLINE case.
      okResult.put("results", result.results());
    } else {
      okResult.put("result", result.getCommand());
    }
    return okResult;
  }

  private CommandResult runInsert(DBObject cmd) {
    if (!cmd.containsField("insert") && !cmd.containsField("documents")) {
      return notOkErrorResult(null, "need insert and documents");
    }

    final FongoDBCollection collection = doGetCollection((String) cmd.get("insert"));

    List<DBObject> documentsToInsert = (List<DBObject>) cmd.get("documents");

    for (DBObject document : documentsToInsert) {
      collection.insert(document);
    }

    final CommandResult commandResult = okResult();
    commandResult.put("ok", 1);
    commandResult.append("n", documentsToInsert.size());
    return commandResult;
  }

  private CommandResult runDelete(DBObject cmd) {
    if (!cmd.containsField("delete") && !cmd.containsField("deletes")) {
      return notOkErrorResult(null, "need delete and deletes");
    }

    final FongoDBCollection collection = doGetCollection((String) cmd.get("delete"));

    List<DBObject> documentsToDelete = (List<DBObject>) cmd.get("deletes");

    for (DBObject document : documentsToDelete) {
      if (!document.containsField("limit")) {
        return notOkErrorResult(9, null, "missing limit field");
      }
    }

    boolean okIsInteger = false; // I dont understand

    int deletedDocuments = 0;
    for (DBObject document : documentsToDelete) {
      DBObject deleteQuery = (DBObject) document.get("q");
      Integer limit = (Integer) document.get("limit");

      WriteResult result = null;
      if (limit != null && limit < 1) {
        result = collection.remove(deleteQuery);
      } else {
        okIsInteger = true;
        Iterator<DBObject> iterator = collection.find(deleteQuery).limit(1).iterator();

        if (iterator.hasNext()) {
          DBObject docToDelete = iterator.next();
          result = collection.remove(new BasicDBObject("_id", docToDelete.get("_id")));
        }
      }

      if (result != null) {
        deletedDocuments += result.getN();
      }

    }

    final CommandResult commandResult = okResult();
    if (okIsInteger) {
      commandResult.put("ok", 1);
    }
    commandResult.append("n", deletedDocuments);
    return commandResult;
  }
}
