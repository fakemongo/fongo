package com.github.fakemongo.junit;

import com.github.fakemongo.Fongo;
import static com.github.fakemongo.Fongo.DEFAULT_SERVER_VERSION;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.ServerVersion;
import com.mongodb.util.FongoJSON;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a Junit Rule to use with annotation
 * <p>
 * &#64;Rule
 * public FongoRule rule = new FongoRule().
 * </p>
 * <p>
 * Note than you can switch to a real mongodb on your localhost (for now).
 * </p>
 * <p><b>
 * WARNING : database is dropped after the test !!
 * </b></P>
 */
public class FongoRule extends ExternalResource {

  private final static Logger LOG = LoggerFactory.getLogger(FongoRule.class);
  /**
   * Will be true if we use the real MongoDB to test things against real world.
   */
  private final boolean realMongo;
  private final String dbName;
  private final Fongo fongo;
  private MongoClient mongo;
  private DB db;
  private MongoDatabase mongoDatabase;
  private CodecRegistry codecRegistry;

  /**
   * Setup a rule with a real MongoDB.
   *
   * @param dbName            the dbName to use.
   * @param serverVersion     version of the server to use for fongo.
   * @param realMongo         set to true if you want to use a real mongoDB.
   * @param mongoClientIfReal real client to use if realMongo si true.
   * @param codecRegistry     the codec registry used by fongo.
   */
  public FongoRule(final String dbName, final ServerVersion serverVersion, final boolean realMongo, final MongoClient mongoClientIfReal, final CodecRegistry codecRegistry) {
    this.dbName = dbName;
    this.realMongo = realMongo || "true".equals(System.getProperty("fongo.force.realMongo"));
    this.fongo = realMongo ? null : newFongo(serverVersion, codecRegistry);
    this.mongo = mongoClientIfReal;
    this.codecRegistry = codecRegistry;
  }

  public FongoRule(final String dbName, final ServerVersion serverVersion, final boolean realMongo, final MongoClient mongoClientIfReal) {
    this(dbName, serverVersion, realMongo, mongoClientIfReal, MongoClient.getDefaultCodecRegistry());
  }

  public FongoRule() {
    this(DEFAULT_SERVER_VERSION);
  }

  public FongoRule(CodecRegistry codecRegistry) {
    this(false, codecRegistry);
  }

  public FongoRule(final ServerVersion serverVersion) {
    this(randomName(), serverVersion, false, null);
  }

  public FongoRule(boolean realMongo) {
    this(realMongo, DEFAULT_SERVER_VERSION);
  }

  public FongoRule(boolean realMongo, CodecRegistry codecRegistry) {
    this(randomName(), DEFAULT_SERVER_VERSION, realMongo, null, codecRegistry);
  }

  public FongoRule(boolean realMongo, ServerVersion serverVersion) {
    this(randomName(), serverVersion, realMongo, null);
  }

  public FongoRule(boolean realMongo, MongoClient mongoClientIfReal) {
    this(randomName(), DEFAULT_SERVER_VERSION, realMongo, mongoClientIfReal);
  }

  public FongoRule(String dbName, boolean realMongo) {
    this(dbName, DEFAULT_SERVER_VERSION, realMongo, null);
  }

  public FongoRule(String dbName) {
    this(dbName, DEFAULT_SERVER_VERSION, false, null);
  }

  public boolean isRealMongo() {
    return this.realMongo;
  }

  @Override
  protected void before() throws UnknownHostException {
    if (realMongo) {
      if (mongo == null) {
        MongoClientOptions options = MongoClientOptions.builder().codecRegistry(codecRegistry).build();
        mongo = new MongoClient(new ServerAddress(), options);
      }
    } else {
      mongo = this.fongo.getMongo();
    }
    db = mongo.getDB(dbName);
    mongoDatabase = mongo.getDatabase(dbName);
  }

  @Override
  protected void after() {
    db.dropDatabase();
  }

  public DBCollection insertJSON(DBCollection coll, String json) {
    List<DBObject> objects = parseList(json);
    for (DBObject object : objects) {
      coll.insert(object);
    }
    return coll;
  }

  public MongoCollection insertJSON(MongoCollection coll, String json) {
    List<DBObject> objects = parseList(json);
    for (DBObject object : objects) {
      coll.insertOne(new Document(object.toMap()));
    }
    return coll;
  }

  public DBCollection insertFile(DBCollection coll, String filename) throws IOException {
    InputStream is = this.getClass().getResourceAsStream(filename);
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      String line = br.readLine();
      while (line != null) {
        coll.insert(this.parseDBObject(line));
        line = br.readLine();
      }
    } finally {
      if (is != null) {
        is.close();
      }
    }
    return coll;
  }

  public MongoCollection<Document> insertDocumentsFromFile(String filename) throws IOException {
    return insertDocumentsFromFile(newMongoCollection(), filename);
  }

  public MongoCollection<Document> insertDocumentsFromFile(MongoCollection<Document> coll, String filename) throws IOException {
    InputStream is = this.getClass().getResourceAsStream(filename);
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      String line = br.readLine();
      while (line != null) {
        coll.insertOne(Document.parse(line));
        line = br.readLine();
      }
    } finally {
      if (is != null) {
        is.close();
      }
    }
    return coll;
  }

  public List<DBObject> parseList(String json) {
    return parse(json);
  }

  public DBObject parseDBObject(String json) {
    return parse(json);
  }

  @SuppressWarnings("unchecked")
  public <T> T parse(String json) {
    return (T) FongoJSON.parse(json);
  }

  public DBCollection newCollection() {
    return newCollection(randomName());
  }

  public DBCollection newCollection(String collectionName) {
    return db.getCollection(collectionName);
  }

  public MongoCollection<Document> newMongoCollection() {
    return newMongoCollection(randomName());
  }

  public MongoCollection<Document> newMongoCollection(final String collectionName) {
    return mongoDatabase.getCollection(collectionName);
  }

  public <T> MongoCollection<T> newMongoCollection(final Class<T> documentClass) {
    return newMongoCollection(randomName(), documentClass);
  }

  public <T> MongoCollection<T> newMongoCollection(final String collectionName, final Class<T> documentClass) {
    return mongoDatabase.getCollection(collectionName, documentClass);
  }

  protected Fongo newFongo(ServerVersion serverVersion, CodecRegistry codecRegistry) {
    return new Fongo("test", serverVersion, codecRegistry);
  }

  public Fongo getFongo() {
    return this.fongo;
  }

  @Deprecated
  public DB getDb() {
    return this.db;
  }

  @Deprecated
  public DB getDb(String name) {
    return this.mongo.getDB(name);
  }

  public DB getDB() {
    return this.db;
  }

  public DB getDB(String name) {
    return this.mongo.getDB(name);
  }

  public MongoDatabase getDatabase(String name) {
    return this.mongo.getDatabase(name);
  }

  public MongoDatabase getDatabase() {
    return this.mongoDatabase;
  }

  @Deprecated
  public Mongo getMongo() {
    return this.mongo;
  }

  public MongoClient getMongoClient() {
    return this.mongo;
  }

  public static String randomName() {
    return UUID.randomUUID().toString();
  }

  public static String randomName(String prefix) {
    return prefix + UUID.randomUUID().toString();
  }

  public boolean mustContainsSystemIndexes() {
    final ServerVersion serverVersion = getServerVersion();
    return serverVersion.compareTo(Fongo.V3_2_SERVER_VERSION) < 0;
  }

  public ServerVersion getServerVersion() {
    final ServerVersion serverVersion;
    if (isRealMongo()) {
      final Document document = getMongoClient().getDatabase("test").runCommand(new BsonDocument("buildinfo", new BsonInt32(1)));
      List source = (List) document.get("versionArray");
      List version = new ArrayList();
      for (int i = 0; i < 3; i++) {
        version.add(source.get(i));
      }
      serverVersion = new ServerVersion(version);
    } else {
      serverVersion = getFongo().getServerVersion();
    }
    LOG.debug("version for mongodb server:{}", serverVersion);
    return serverVersion;
  }


}
