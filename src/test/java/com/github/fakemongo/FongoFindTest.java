package com.github.fakemongo;

import com.github.fakemongo.impl.Util;
import com.github.fakemongo.junit.FongoRule;
import static com.google.common.collect.Lists.newArrayList;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.DBRef;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Rule;
import org.junit.Test;


public class FongoFindTest {

  @Rule
  public FongoRule fongoRule = new FongoRule(!true);

  @Test
  public void testFindByNotExactType() {
    // Given
    DBCollection collection = fongoRule.newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("field", 12L));

    // When
    DBObject result = collection.findOne(new BasicDBObject("field", 12));

    // Then
    assertThat(result).isEqualTo(new BasicDBObject("_id", 1).append("field", 12L));
  }


  /**
   * Checks that specified fields in find()'s projection from a list are actually returned (and are the only returned).
   */
  @Test
  public void testListFieldsProjection() {
    // Given
    DBCollection collection = fongoRule.newCollection();
    BasicDBList list = new BasicDBList();
    list.add(new BasicDBObject("a", "1").append("b", "2").append("c", "3"));
    list.add(new BasicDBObject("a", "4").append("b", "5").append("d", "6"));
    collection.insert(new BasicDBObject("_id", 1).append("lst", list));

    // When
    DBCursor cursor = collection.find(new BasicDBObject(), new BasicDBObject("lst.a", 1).append("lst.b", 1));

    // Then
    BasicDBList lst = (BasicDBList) cursor.next().get("lst");
    for (Object o : lst) {
      DBObject item = (DBObject) o;
      assertThat(item.get("a")).as("'a' is expected from projection").isNotNull();
      assertThat(item.get("b")).as("'b' is expected from projection").isNotNull();
      assertThat(item.get("c")).as("'c' is not expected since it is not in projection").isNull();
    }
  }

  @Test
  public void should_handle_mixed_type_in_$in() {
    // Given
    DBCollection collection = fongoRule.newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("other", 12L));
    collection.insert(new BasicDBObject("_id", 2).append("other", 13L));
    collection.insert(new BasicDBObject("_id", 3).append("other", 14L));

    // When
    DBObject inFind = new BasicDBObject("other", new BasicDBObject("$in", Util.list(12, 13)));
    DBCursor cursor = collection.find(inFind);

    // Then
    assertThat(cursor.size()).isEqualTo(2);
  }

  @Test
  public void should_handle_$in_with_dbref() {
    // Given
    DBCollection collection = fongoRule.newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("lang", "de").append("platform", new DBRef("platforms", "demo")));
    collection.insert(new BasicDBObject("_id", 2).append("lang", "de").append("platform", new DBRef("platforms", "demo2")));
    collection.insert(new BasicDBObject("lang", "de").append("platform", new DBRef("platforms", "demo3")));

    BasicDBList platforms = new BasicDBList();
    platforms.add(new DBRef("platforms", "demo2"));
    DBObject query = new BasicDBObject("lang", "de").append("platform", new BasicDBObject("$in", platforms));

    // When
    DBCursor cursor = collection.find(query);

    // Then
    assertThat(cursor.toArray()).isEqualTo(newArrayList(new BasicDBObject("_id", 2).append("lang", "de").append("platform", new DBRef("platforms", "demo2"))));
  }

  @Test
  public void should_handle_collection_in_query() {
    DBCollection collection = fongoRule.newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("lang", "de"));
    assertThat(collection.findOne(
        new BasicDBObject("lang", new BasicDBObject("$in", new HashSet<String>(newArrayList("de")))))
    ).isNotNull();
  }

  @Test
  public void testCursorLength() {
    DBCollection collection = fongoRule.newCollection();
    collection.insert(new BasicDBObject("_id", 1).append("lang", "de"));
    assertThat(collection.find().length())
            .isEqualTo(1);
  }
}
