[![Build Status](https://travis-ci.org/fakemongo/fongo.svg?branch=master)](https://travis-ci.org/fakemongo/fongo)

# fongo

Fongo is an in-memory java implementation of MongoDB. It intercepts calls to the standard mongo-java-driver for 
finds, updates, inserts, removes and other methods. The primary use is for lightweight unit testing where you
don't want to spin up a `mongod` process.

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.fakemongo/fongo/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.fakemongo/fongo/) ![License Apache2](https://go-shields.herokuapp.com/license-apache2-blue.png)

## Usage
Add dependency to your project:

### If you use 3.X drivers (async included)

```xml
<dependency>
  <groupId>com.github.fakemongo</groupId>
  <artifactId>fongo</artifactId>
  <version>2.1.0</version>
  <scope>test</scope>
</dependency>
```

[Other dependency management](http://search.maven.org/#artifactdetails|com.github.fakemongo|fongo|2.1.0)

### If you use 2.X drivers (this branch *fongo-drivers-2.x*) will be deprecated soon

```xml
<dependency>
  <groupId>com.github.fakemongo</groupId>
  <artifactId>fongo</artifactId>
  <version>1.6.5</version>
  <scope>test</scope>
</dependency>
```

[Other dependency management](http://search.maven.org/#artifactdetails|com.github.fakemongo|fongo|1.6.5)


*Alternatively: clone this repo and build the jar: `mvn package` then copy jar to your classpath*

Use in place of regular `com.mongodb.Mongo` instance:

```java
import com.github.fakemongo.Fongo;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mognodb.DBCollection;
...
Fongo fongo = new Fongo("mongo server 1");

// once you have a DB instance, you can interact with it
// just like you would with a real one.
DB db = fongo.getDB("mydb");
DBCollection collection = db.getCollection("mycollection");
collection.insert(new BasicDBObject("name", "jon"));
```

## Scope

Fongo doesn't implement all MongoDB functionality. Most query and update syntax is supported. 
Gridfs and capped collections are not supported.
MapReduce is in minimal way but will be enhanced soon.

 * `$near` can be used
 * `$geoWithin` can be used with $box for now.

## Implementation Details

Fongo depends on [Objenesis](http://objenesis.org/) to hijack the `com.mongodb.MongoClient` class. It has a "provided" dependency on the mongo-java-driver and was tested with *2.13.0*
and *3.0.1*.
It also has a "provided" dependency on sl4j-api for logging. If you don't already have sl4j in your project, you can add a maven dependency to the logback implementation like this:

```xml
<dependency> 
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <version>1.1.7</version>
  <scope>test</scope>
</dependency>
```

Fongo should be thread safe. All read and write operations on collections are synchronized. It's pretty coarse, but
should be good enough for simple testing. Fongo doesn't have any shared state (no statics). Each fongo instance is completely independent.

## Usage Details

```java
// Fongo instance methods

// get all created databases (they are created automatically the first time requested)
Collection<DB> dbs = fongo.getUsedDatabases();
// also
List<String> dbNames = fongo.getDatabaseNames();
// also
fongo.dropDatabase("dbName");

// get an instance of the hijacked com.mongodb.Mongo
Mongo mongo = fongo.getMongo();
```
If you use Spring, you can configure fongo in your XML configuration context:

```xml
<bean name="fongo" class="com.github.fakemongo.Fongo">
    <constructor-arg value="InMemoryMongo" />
</bean>
<bean id="mongo" factory-bean="fongo" factory-method="getMongo" />

<mongo:db-factory id="mongoDbFactory" mongo-ref="mongo" />

<!-- localhost settings for mongo -->
<!--<mongo:db-factory id="mongoDbFactory" />-->

<bean id="mongoTemplate" class="org.springframework.data.mongodb.core.MongoTemplate">
    <constructor-arg ref="mongoDbFactory"/>
</bean>
```

## Junit

If you use JUnit in your project, you can use Rule to instantiate a `Fongo` object :

```java
@Rule
public FongoRule fongoRule = new FongoRule();
```

If you need, you can easily switch to your real MongoDB server (on localhost for now).

```java
@Rule
public FongoRule fongoRule = new FongoRule(true);
```

WARNING : In this case, the database WILL BE DROPPED when test is finish.
So, use a random database name (e.g. UUID), BUT NOT your real database.

You can specify the version of the database with :

```java
@Rule
public FongoRule fongoRule = new FongoRule(new ServerVersion(2, 6));
```

In this case, the drivers didn't handle queries with the same way.

## Junit (async drivers)    

If you use JUnit in your project, you can use Rule to instantiate a `Fongo` object :

```java
@Rule
public FongoAsyncRule fongoAsyncRule = new FongoAsyncRule();
```

If you need, you can easily switch to your real MongoDB server (on localhost for now).

```java
@Rule
public FongoAsyncRule fongoAsyncRule = new FongoAsyncRule(true);
```

WARNING : In this case, the database WILL BE DROPPED when test is finish.
So, use a random database name (e.g. UUID), BUT NOT your real database.

You can specify the version of the database with :

```java
@Rule
public FongoAsyncRule fongoAsyncRule = new FongoAsyncRule(new ServerVersion(2, 6));
```

In this case, the drivers didn't handle queries with the same way.


## Text Search Simulation
Fongo simulates [text search](http://docs.mongodb.org/manual/reference/command/text/) now.
The results of text search are quite similar to real, but not exactly.

### Next features are supported:
* Plain words search
* Search strings
* Negated words
* Projections in search query
* Limits

### Fongo text search simulation does not support:
* Languages (including language-specific stop words)
* Filter (maybe in future)
* Weights in text index (we plan to support them in future)

### Limitations, Differences:
* Only [text command](http://docs.mongodb.org/manual/reference/command/text/) search is supported. We will support [find query with $text operator](http://docs.mongodb.org/master/reference/operator/query/text/) probably in future.
* Scores in returned results are not always the same as the real Mongo's scores.
* Only one field can be indexed as text field now. This limitation will be removed soon.

### Usage example of the text search simulation:
```java
    @Test
    public void findByTextTest() {
    //....
    //Define index:
    collection.createIndex(new BasicDBObject("myTextFieldToIndex", "text"));

    DBObject textSearchCommand = new BasicDBObject("search", "my search \"my phrase\" -without -this -words");

    //Search Command
    DBObject textSearchResult = collection.getDB()
            .command(new BasicDBObject("collection", new BasicDBObject("text", textSearchCommand)));

    //Make your assertions
    //....
	}
```

## Todo

* more testing
* complete compatibility with Jongo

## Reporting Bugs and submitting patches

If you discover a bug with fongo you can file an issue in github. At the very least, please include a complete description of the problem with steps to reproduce.
If there were exceptions thrown, please include the complete stack traces. If it's not easy to explain the problem, failing test code would be helpful.
You can fork the project and add a new failing test case. 

It's even better if you can both add the test case and fix the bug. I will merge pull requests with test cases and add 
your name to the patch contributors below. Please maintain the same code formatting and style as the rest of the project.

## Changelog

Version 2.1.0 include compatibility with 3.X async driver version.
Version 2.0.0 break compatibility with 2.X driver version.
Version 1.6.0 break compatibility with 2.12.X driver version.

## Original Author
* [Jon Hoffman](https://github.com/hoffrocket)

## Patch Contributers
* [Guido García](https://github.com/palmerabollo)
* [rid9](https://github.com/rid9)
* [aakhmerov](https://github.com/aakhmerov)
* [Eduardo Franceschi](https://github.com/efranceschi)
* [Tobias Clemson](https://github.com/tobyclemson)
* [Philipp Knobel](https://github.com/philnate)
* [Roger Lindsjö](https://github.com/rlindsjo)
* [Vadim Platono](https://github.com/dm3)
* [grahamar](https://github.com/grahamar)
* [Boris Granveaud](https://github.com/bgranvea)
* [Philipp Jardas](https://github.com/phjardas)
* [Sergey Passichenko](https://github.com/serj-de-sudden)
* [William Delanoue](https://github.com/twillouer)
* [Juan F. Codagnone](https://github.com/jcodagnone)
* Anton Bobukh
* [Matthew Reid](https://github.com/drei01)
* [jasondemorrow](https://github.com/jasondemorrow)
* [lldata](https://github.com/lldata)
* [renej-github](https://github.com/renej-github)
* [mathieubodin](https://github.com/mathieubodin)
* [Alex Art](https://github.com/elennaro)
* [htmldoug](https://github.com/htmldoug)
* [antonbobukh](https://github.com/antonbobukh)
* [Alban Dericbourg](https://github.com/adericbourg)
* [louisnerys](https://github.com/louisnerys)
* [Changgeng Li](https://github.com/changgengli)
* [Nils Meder](https://github.com/nilstgmd)
* [Liran Moysi](https://github.com/liranms)
* [Kong TO](https://github.com/newlight77)
* [tomdearman](https://github.com/tomdearman)
* [James Jory](https://github.com/james-jory)
* [Riaz Ahmed](https://github.com/riyyaz)
* [Martin W. Kirst](https://github.com/nitram509)
* [LiBe](https://github.com/libetl)
* [Vladimir Shakhov](https://github.com/bogdad)
* [Guy de Pourtalès](https://github.com/gdepourtales)
* [Heng-Scheng Chuang](https://github.com/ddchengschengc)
* [Guillermo Campelo](https://github.com/guicamest)
* [Arthur Cinader](https://github.com/acinader)
* [Jimmy Royer](https://github.com/jimleroyer)
* [Akbashev Alexander](https://github.com/Jimilian)
* [Ben](https://github.com/BenRomberg)
* [Daniil Gitelson](https://github.com/daniilguit)
* [Mark Crossfield](https://github.com/mrmanc)
* [Rory Douglas](https://github.com/worrel)
* [Georg Meyer](https://github.com/scho)
* [Igor](https://github.com/ilaborie)
* [Corey Vaillancourt](https://github.com/coreyjv)
* [Eric Karge](https://github.com/e-karge)
* [Matthias Egli](https://github.com/MatthiasEgli)
* [Yaniv Oliver](https://github.com/yanivoliver)
* [Yunchi Luo](https://github.com/mightyguava)
* [Avihai Berkovitz](https://github.com/aciduck)
* [Kollivakkam Raghavan](https://github.com/krraghavan)
* [Ann Katrin Gagnat](https://github.com/akgagnat)
* [Nicola Viola](https://github.com/nicolaViola)
* [Michael Childs](https://github.com/mchildspelco)
* [Dmitri Maksimov](https://github.com/mcdimus)
* [Enrico Pelizzon](https://github.com/theimplementer)
* [Krzysztof Sukienniczak](https://github.com/krs)
* [zigzago](https://github.com/zigzago)
