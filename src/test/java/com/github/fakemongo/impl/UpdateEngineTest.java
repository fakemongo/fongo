package com.github.fakemongo.impl;

import com.github.fakemongo.FongoException;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import com.mongodb.util.FongoJSON;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;
import org.junit.Test;

import static org.junit.Assert.*;

public class UpdateEngineTest {

  @Test
  public void testBasicUpdate() {
    UpdateEngine updateEngine = new UpdateEngine();
    assertEquals(new BasicDBObject("_id", 1).append("a", 1),
        updateEngine.doUpdate(new BasicDBObject("_id", 1).append("a", 5).append("b", 1),
            new BasicDBObject("a", 1)));
  }

  @Test(expected = Exception.class)
  public void testOnlyOneAtomicUpdatePerKey() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$set").append("a", 5).pop()
        .push("$inc").append("a", 3).pop().get();

    updateEngine.doUpdate(new BasicDBObject(), update);
  }

  @Test
  public void testSetOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$set").append("a", 5).pop().get();

    assertEquals(new BasicDBObject("_id", 1).append("a", 5).append("b", 1),
        updateEngine.doUpdate(new BasicDBObject("_id", 1).append("a", 1).append("b", 1), update));
  }

  @Test
  public void testEmbeddedSetOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$set").append("a.b", 5).pop().get();

    assertEquals(new BasicDBObject("a", new BasicDBObject("b", 5)),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testUnSetOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$unset").append("a", 1).pop().get();

    assertEquals(new BasicDBObject("_id", 1).append("b", 1),
        updateEngine.doUpdate(new BasicDBObject("_id", 1).append("a", 1).append("b", 1), update));
  }

  @Test
  public void testEmbeddedUnSetOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$unset").append("a.b", 1).pop().get();

    assertEquals(new BasicDBObject("_id", 1).append("a", new BasicDBObject()),
        updateEngine.doUpdate(new BasicDBObject("_id", 1).append("a", new BasicDBObject("b", 1)), update));
  }

  @Test
  public void testEmbeddedRenameOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$rename").append("a.b", "a.c").append("x", "y").append("h.i", "u.r").pop().get();

    assertEquals(new BasicDBObject("_id", 1).append("a", new BasicDBObject("c", 1)).append("y", 3).append("u", new BasicDBObject("r", 8)).append("h", new BasicDBObject()),
        updateEngine.doUpdate(new BasicDBObject("_id", 1).append("a", new BasicDBObject("b", 1)).append("x", 3).append("h", new BasicDBObject("i", 8)), update));
  }

  @Test
  public void testIncOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$inc").append("a", 5).pop().get();

    assertEquals(new BasicDBObject("a", 6),
        updateEngine.doUpdate(new BasicDBObject("a", 1), update));
    assertEquals(new BasicDBObject("a", 5),
        updateEngine.doUpdate(new BasicDBObject(), update));
    assertEquals(new BasicDBObject("a", 8.1),
        updateEngine.doUpdate(new BasicDBObject("a", 3.1), update));
  }

  @Test
  public void testPushOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$push").append("a", 2).pop().get();

    assertEquals(new BasicDBObject("a", Util.list(1, 2)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1)), update));
    assertEquals(new BasicDBObject("a", Util.list(2)),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testPushEachOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$push")
        .push("a").append("$each", Util.list(2, 3)).pop().pop().get();

    assertEquals(new BasicDBObject("a", Util.list(1, 2, 3)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1)), update));
    assertEquals(new BasicDBObject("a", Util.list(1, 2, 2, 3)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2)), update));
    assertEquals(new BasicDBObject("a", Util.list(2, 3)),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testPushEachSliceFirstOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$push").push("a")
        .append("$each", Util.list(2, 3))
        .append("$slice", 2)
        .pop().pop().get();

    assertEquals(new BasicDBObject("a", Util.list(1, 2)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1)), update));
    assertEquals(new BasicDBObject("a", Util.list(1, 4)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 4)), update));
    assertEquals(new BasicDBObject("a", Util.list(2, 3)),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testPushEachSliceZeroOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$push").push("a")
        .append("$each", Util.list(2, 3))
        .append("$slice", 0)
        .pop().pop().get();

    assertEquals(new BasicDBObject("a", Util.list()),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1)), update));
    assertEquals(new BasicDBObject("a", Util.list()),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testPushEachSliceLastOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$push").push("a")
        .append("$each", Util.list(0))
        .append("$slice", -2)
        .pop().pop().get();

    assertEquals(new BasicDBObject("a", Util.list(3, 0)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2, 3)), update));
    assertEquals(new BasicDBObject("a", Util.list(0)),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testPushEachPositionOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update0 = new BasicDBObjectBuilder().push("$push").push("a")
        .append("$each", Util.list(0))
        .append("$position", 0)
        .pop().pop().get();
    assertEquals(new BasicDBObject("a", Util.list(0)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list()), update0));
    assertEquals(new BasicDBObject("a", Util.list(0, 1, 2, 3)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2, 3)), update0));

    DBObject update1 = new BasicDBObjectBuilder().push("$push").push("a")
        .append("$each", Util.list(0))
        .append("$position", 1)
        .pop().pop().get();
    assertEquals(new BasicDBObject("a", Util.list(0)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list()), update1));
    assertEquals(new BasicDBObject("a", Util.list(1, 0)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1)), update1));
    assertEquals(new BasicDBObject("a", Util.list(1, 0, 2, 3)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2, 3)), update1));
  }

  @Test
  public void testPushEachSlicePositionOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update0 = new BasicDBObjectBuilder().push("$push").push("a")
        .append("$slice", 2)
        .append("$position", 1)
        .append("$each", Util.list(0))
        .pop().pop().get();

    assertEquals(new BasicDBObject("a", Util.list(0)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list()), update0));
    assertEquals(new BasicDBObject("a", Util.list(1, 0)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2, 3)), update0));
  }

  @Test
  public void testPushEachSortObjectOperation() {
    Date date = new Date();
    DBObject obja0 = new BasicDBObjectBuilder().append("a", 0).get();
    DBObject obja2 = new BasicDBObjectBuilder().append("a", 2).get();
    DBObject obja1b2 = new BasicDBObjectBuilder().append("a", 1).append("b", 2).get();
    DBObject objb0 = new BasicDBObjectBuilder().append("b", 0).get();
    List list = new BasicDBList();
    double d = 0.5D;
    int i = 0;
    MinKey minKey = new MinKey();
    MaxKey maxKey = new MaxKey();
    long l = 1L;
    ObjectId objId = new ObjectId();
    Pattern regex = Pattern.compile("\\s*");

    // arbitrary order
    List<Object> objects = Util.list(obja0, obja2, objb0, obja1b2, d, i, null, minKey, maxKey, l, false, true, date, regex, objId, list, "");

    UpdateEngine updateEngine = new UpdateEngine();
    DBObject updateASortAsc = new BasicDBObjectBuilder().push("$push").push("a")
        .append("$sort", new BasicDBObjectBuilder().append("a", 1).get())
        .append("$each", Util.list())
        .pop().pop().get();

    assertArrayEquals(
        new Object[]{minKey, null, i, d, l, "", objb0, list, objId, false, true, date, regex, maxKey, obja0, obja1b2, obja2},
        ((List) updateEngine.doUpdate(new BasicDBObject("a", objects), updateASortAsc).get("a")).toArray()
    );

    DBObject updateAsc = new BasicDBObjectBuilder().push("$push").push("a")
        .append("$sort", 1)
        .append("$each", Util.list())
        .pop().pop().get();

    Object[] actual = ((List) updateEngine.doUpdate(new BasicDBObject("a", objects), updateAsc).get("a")).toArray();
    assertArrayEquals(
        new Object[]{minKey, null, i, d, l, "", obja0, obja1b2, obja2, objb0, list, objId, false, true, date, regex, maxKey},
        actual
    );
  }

  @Test
  public void testPushAndIncOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject query = new BasicDBObject("_id", 1);
    DBObject update = new BasicDBObjectBuilder()
        .push("$push").push("n").append("_id", 2).append("u", 3).pop().pop()
        .push("$inc").append("c", 4).pop().get();
    DBObject expected = new BasicDBObjectBuilder().append("_id", 1).append("n", Util.list(new BasicDBObject("_id", 2).append("u", 3))).append("c", 4).get();
    assertEquals(expected, updateEngine.doUpdate(query, update));
  }

  @Test
  public void testPushAllOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$pushAll").append("a", Util.list(2, 3)).pop().get();

    assertEquals(new BasicDBObject("a", Util.list(1, 2, 3)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1)), update));
    assertEquals(new BasicDBObject("a", Util.list(2, 3)),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testAddToSetOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$addToSet").append("a", 2).pop().get();

    assertEquals(new BasicDBObject("a", Util.list(1, 2)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1)), update));
    assertEquals(new BasicDBObject("a", Util.list(1, 2)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2)), update));
    assertEquals(new BasicDBObject("a", Util.list(2)),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testAddToSetEachOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$addToSet")
        .push("a").append("$each", Util.list(2, 3)).pop().pop().get();

    assertEquals(new BasicDBObject("a", Util.list(1, 2, 3)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1)), update));
    assertEquals(new BasicDBObject("a", Util.list(1, 2, 3)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2)), update));
    assertEquals(new BasicDBObject("a", Util.list(2, 3)),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testPopLastOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$pop").append("a", 1).pop().get();

    assertEquals(new BasicDBObject("a", Util.list(1)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2)), update));
    assertEquals(new BasicDBObject("a", Util.list()),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1)), update));
    assertEquals(new BasicDBObject(),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }

  @Test
  public void testPopFirstOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$pop").append("a", -1).pop().get();

    assertEquals(new BasicDBObject("a", Util.list(2)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2)), update));
  }

  @Test
  public void testSimplePullOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$pull").append("a", 1L).pop().get();

    assertEquals(new BasicDBObject("a", Util.list(2, 3)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(1, 2, 1, 3, 1)), update));
  }

  @Test
  public void testEmbeddedPullOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$pull").push("a")
        .append("b", 1).pop().get();

    assertEquals(new BasicDBObject("a", Util.list(new BasicDBObject("b", 2).append("f", 1))),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(
            new BasicDBObject("b", 1).append("f", 1),
            new BasicDBObject("b", 2).append("f", 1),
            new BasicDBObject("b", 1).append("f", 1)
        )), update));
  }

  @Test
  public void testEmbeddedPullWithInPatternOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder()
            .push("$pull").push("field").append("$in", Util.list(Pattern.compile("a+"))).pop().pop()
            .get();

    assertEquals(new BasicDBObject("field", Util.list("bb")),
        updateEngine.doUpdate(new BasicDBObject("field", Util.list("baab", "bb")), update));
  }

  @Test
  public void testEmbeddedPullWithInNumberOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder()
            .push("$pull").push("field").append("$in", Util.list(2L)).pop().pop()
            .get();

    assertEquals(new BasicDBObject("field", Util.list(1)),
        updateEngine.doUpdate(new BasicDBObject("field", Util.list(1, 2.0)), update));
  }

  @Test
  public void testPullAllOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$pullAll").append("a", Util.list(2, 3)).pop().get();

    assertEquals(new BasicDBObject("a", Util.list(1, 4)),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(2, 1, 2, 3, 4, 2, 3)), update));
  }

  @Test
  public void testBitAndOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$bit")
        .push("a").append("and", 5).pop().get();

    assertEquals(new BasicDBObject("a", 11 & 5),
        updateEngine.doUpdate(new BasicDBObject("a", 11), update));
  }

  @Test
  public void testBitOrOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$bit")
        .push("a").append("or", 5).pop().get();

    assertEquals(new BasicDBObject("a", 11 | 5),
        updateEngine.doUpdate(new BasicDBObject("a", 11), update));
  }

  @Test
  public void testBitAndOrOperation() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$bit")
        .push("a").append("and", 5).append("or", 2).pop().get();

    assertEquals(new BasicDBObject("a", (11 & 5) | 2),
        updateEngine.doUpdate(new BasicDBObject("a", 11), update));
  }

  @Test
  public void testPositionalOperator() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$inc")
        .append("b.$.c", 1).pop().get();

    assertEquals(new BasicDBObject("b", Util.list(new BasicDBObject("c", 2).append("n", "jon"))),
        updateEngine.doUpdate(new BasicDBObject("b", Util.list(
            new BasicDBObject("c", 1).append("n", "jon"))), update, new BasicDBObject("b.n", "jon"), false));
  }

  @Test
  public void testPositionalOperatorWithElemMatchWithPostPath() {
    String random1 = UUID.randomUUID().toString();
    String random2 = UUID.randomUUID().toString();

    DBObject object = (DBObject) FongoJSON.parse("{ \"name\" : \"Tenant 1\", \"appAllocations\" : [ { \"appId\" : \""
        + random1 + "\" , \"maxUser\" : 4} , { \"appId\""
        + ": \"" + random2 + "\" , \"maxUser\" : 42}]}");
    DBObject update = (DBObject) FongoJSON.parse("{ \"$set\" : { \"appAllocations.$.maxUser\" : 9}}");
    DBObject query = (DBObject) FongoJSON.parse("{ \"appAllocations\" : { \"$elemMatch\" : { \"appId\" : \"" + random1 + "\"}}}");

    DBObject expected = (DBObject) FongoJSON.parse("{ \"name\" : \"Tenant 1\", \"appAllocations\" : [ { \"appId\" : \""
        + random1 + "\" , \"maxUser\" : 9} , { \"appId\""
        + ": \"" + random2 + "\" , \"maxUser\" : 42}]}");
    UpdateEngine updateEngine = new UpdateEngine();

    assertEquals(expected.toString(),
        updateEngine.doUpdate(object, update, query, false).toString());
  }

  @Test
  public void testPositionalOperatorWithElemMatchWithoutPostPath() {
    String random1 = UUID.randomUUID().toString();
    String random2 = UUID.randomUUID().toString();

    DBObject object = (DBObject) FongoJSON.parse("{ \"name\" : \"Tenant 1\", \"appAllocations\" : [ { \"appId\" : \""
        + random1 + "\" , \"maxUser\" : 4} , { \"appId\""
        + ": \"" + random2 + "\" , \"maxUser\" : 42}]}");
    DBObject query = (DBObject) FongoJSON.parse("{ \"appAllocations\" : { \"$elemMatch\" : { \"appId\" : \"" + random1 + "\"}}}");
    DBObject update = (DBObject) FongoJSON.parse("{ \"$set\" : { \"appAllocations.$\" : { \"appId\" : \"" + random1 + "\", \"maxUser\" : 9 }}}");

    DBObject expected = (DBObject) FongoJSON.parse("{ \"name\" : \"Tenant 1\", \"appAllocations\" : [ { \"appId\" : \""
        + random1 + "\" , \"maxUser\" : 9} , { \"appId\""
        + ": \"" + random2 + "\" , \"maxUser\" : 42}]}");
    UpdateEngine updateEngine = new UpdateEngine();

    assertEquals(expected.toString(),
        updateEngine.doUpdate(object, update, query, false).toString());
  }

  @Test
  public void testAddOrReplaceElementMustWorkWithDollarOperator() {
    String random1 = UUID.randomUUID().toString();
    String random2 = UUID.randomUUID().toString();

    DBObject object = (DBObject) FongoJSON.parse("{ \"_id\" : \"1234\" , \"var1\" : \"val1\" , \"parentObject\" : { \"var2\" : \"val21\" , \"subObject\" : [ { \"_id\" : \"" + random1 + "\" , \"var3\" : \"val31\"}, { \"_id\" : \"" + random2 + "\" , \"var3\" : \"val32\"}]}}");
    DBObject update = (DBObject) FongoJSON.parse("{ \"$set\" : { \"parentObject.subObject.$\" : { \"_id\" : \"" + random1 + "\" , \"var3\" : \"val33\"}}}");
    DBObject query = (DBObject) FongoJSON.parse("{ \"_id\" : \"1234\" , \"parentObject.subObject._id\" : \"" + random1 + "\"}");

    DBObject expected = (DBObject) FongoJSON.parse("{ \"_id\" : \"1234\" , \"var1\" : \"val1\" , \"parentObject\" : { \"var2\" : \"val21\" , \"subObject\" : [ { \"_id\" : \"" + random1 + "\" , \"var3\" : \"val33\"} , { \"_id\" : \"" + random2 + "\" , \"var3\" : \"val32\"}]}}");
    UpdateEngine updateEngine = new UpdateEngine();

    assertEquals(expected.toString(),
        updateEngine.doUpdate(object, update, query, false).toString());
  }

  @Test(expected = FongoException.class)
  public void testPositionalArrayOperatorForInvalidPath() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$inc")
        .append("b.$.c", 1).pop().get();

    updateEngine.doUpdate(new BasicDBObject("b", Util.list(1, 2, 3)), update, new BasicDBObject("b", 2), false);
  }

  @Test
  public void testPositionalArrayOperator() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$inc")
        .append("b.$", 1).pop().get();

    assertEquals(new BasicDBObject("b", Util.list(1, 3, 3)),
        updateEngine.doUpdate(new BasicDBObject("b", Util.list(1, 2, 3)), update, new BasicDBObject("b", 2), false));
  }

  @Test
  public void testPositionalArrayDBObjectOperator() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$set")
        .append("b.$", new BasicDBObject("c", 3)).pop().get();

    assertEquals(new BasicDBObject("b", Util.list(new BasicDBObject("a", 2), new BasicDBObject("c", 3))),
        updateEngine.doUpdate(new BasicDBObject("b",
            Util.list(new BasicDBObject("a", 2), new BasicDBObject("a", 1))), update, new BasicDBObject("b.a", 1), false));
  }

  @Test
  public void testArrayIndexOperator() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$inc")
        .append("a.1.b", 1).pop().get();

    assertEquals(new BasicDBObject("a", Util.list(new BasicDBObject("b", 1), new BasicDBObject("b", 2))),
        updateEngine.doUpdate(new BasicDBObject("a", Util.list(
            new BasicDBObject("b", 1),
            new BasicDBObject("b", 1))
        ), update));
  }

  @Test
  public void testEmbeddedIntOperator() {
    UpdateEngine updateEngine = new UpdateEngine();
    DBObject update = new BasicDBObjectBuilder().push("$inc")
        .append("a.1", 1).pop().get();

    assertEquals(new BasicDBObject("a", new BasicDBObject("1", 1)),
        updateEngine.doUpdate(new BasicDBObject(), update));
  }


  @Test
  public void testCurrentDateOperation() {
      UpdateEngine updateEngine = new UpdateEngine();
      DBObject update = new BasicDBObjectBuilder().push("$currentDate").append("a", true).pop().get();

      long timeBeforeUpdate = System.currentTimeMillis();
      DBObject updateResult = updateEngine.doUpdate(new BasicDBObject("_id", 1), update);
      long timeAfterUpdate = System.currentTimeMillis();

      assertNotNull(updateResult.get("a"));

      Date updateTime = (Date) updateResult.get("a");
      assertTrue(updateTime.getTime() >= timeBeforeUpdate);
      assertTrue(updateTime.getTime() <= timeAfterUpdate);
  }

  @Test
  public void testCurrentDateByTypeOperation() {
      UpdateEngine updateEngine = new UpdateEngine();
      DBObject update = new BasicDBObjectBuilder().push("$currentDate").append("a", new BasicDBObject("$type", "date")).pop().get();

      long timeBeforeUpdate = System.currentTimeMillis();
      DBObject updateResult = updateEngine.doUpdate(new BasicDBObject("_id", 1), update);
      long timeAfterUpdate = System.currentTimeMillis();

      assertNotNull(updateResult.get("a"));

      Date updateTime = (Date) updateResult.get("a");
      assertTrue(updateTime.getTime() >= timeBeforeUpdate);
      assertTrue(updateTime.getTime() <= timeAfterUpdate);
  }

  @Test
  public void testOverrideCurrentDateOperation() {
      UpdateEngine updateEngine = new UpdateEngine();
      DBObject update = new BasicDBObjectBuilder().push("$currentDate").append("a", true).pop().get();

      long timeBeforeUpdate = System.currentTimeMillis();
      DBObject updateResult = updateEngine.doUpdate(new BasicDBObject("_id", 1).append("a", timeBeforeUpdate - 1), update);
      long timeAfterUpdate = System.currentTimeMillis();

      assertNotNull(updateResult.get("a"));

      Date updateTime = (Date) updateResult.get("a");
      assertTrue(updateTime.getTime() >= timeBeforeUpdate);
      assertTrue(updateTime.getTime() <= timeAfterUpdate);
  }

  @Test
  public void testEmbeddedCurrentOperation() {
      UpdateEngine updateEngine = new UpdateEngine();
      DBObject update = new BasicDBObjectBuilder().push("$currentDate").append("a.b", true).pop().get();

      long timeBeforeUpdate = System.currentTimeMillis();
      DBObject updateResult = updateEngine.doUpdate(new BasicDBObject("_id", 1), update);
      long timeAfterUpdate = System.currentTimeMillis();

      assertNotNull(updateResult.get("a"));
      DBObject aObject = (DBObject) updateResult.get("a");
      assertNotNull(aObject.get("b"));

      Date updateTime = (Date) aObject.get("b");
      assertTrue(updateTime.getTime() >= timeBeforeUpdate);
      assertTrue(updateTime.getTime() <= timeAfterUpdate);
  }
}
