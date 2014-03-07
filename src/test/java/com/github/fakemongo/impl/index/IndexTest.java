/*************************************************************************
 * ADOBE CONFIDENTIAL
 * ___________________
 *
 *  Copyright 2014 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and are protected by all applicable intellectual property
 * laws, including trade secret and or copyright laws.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/
package com.github.fakemongo.impl.index;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.UUID;

import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Unit tests for {@link Index}.
 * 
 * @author <a href="mailto:meder@adobe.com">Nils Meder</a>
 * 
 */
public class IndexTest
{
	// ----------------------------------------------------------------------------------------------- Private Constants

	private static final String INDEX_NAME = UUID.randomUUID().toString();

	private static final boolean UNIQUE = true;

	private static final String ID_KEY = "_id";

	private static final String ID = UUID.randomUUID().toString();

	private static final DBObject DEFAULT_ID_KEY = new BasicDBObject(ID_KEY, 1);

	private static final String TOP_LEVEL_KEY = UUID.randomUUID().toString();

	private static final String SECOND_LEVEL_KEY = UUID.randomUUID().toString();

	private static final String SECOND_LEVEL_VALUE = UUID.randomUUID().toString();

	private static final String THIRD_LEVEL_KEY = UUID.randomUUID().toString();

	private static final String FOURTH_LEVEL_KEY = UUID.randomUUID().toString();

	private static final String FIFTH_LEVEL_KEY = UUID.randomUUID().toString();

	private static final String FIFTH_LEVEL_VALUE = UUID.randomUUID().toString();

	// -------------------------------------------------------------------------------------------------- Public Methods

	/**
	 * Test {@link Index#embedded(DBObject)} with an expended {@link DBObject}.
	 */
	@Test
	public void testEmbeddedExpandedDBObject()
	{
		final DBObject sourceObject = new BasicDBObject();
		sourceObject.put(ID_KEY, ID);
		sourceObject.put(TOP_LEVEL_KEY, new BasicDBObject(SECOND_LEVEL_KEY, SECOND_LEVEL_VALUE));
		
		final Index iut = new Index(INDEX_NAME, DEFAULT_ID_KEY, UNIQUE);
		final DBObject resultObject = iut.embedded(sourceObject);

		assertThat("The result is not the same object as the source.", resultObject, is(sourceObject));
		assertThat("The result has no '_id' field.", resultObject.containsField(ID_KEY));
		assertThat("The result has no top level field.", resultObject.containsField(TOP_LEVEL_KEY));
		
		final DBObject topLevelObject = (DBObject) resultObject.get(TOP_LEVEL_KEY);
		assertThat("The result has no second level field.", topLevelObject.containsField(SECOND_LEVEL_KEY));

		final String secondLevelValue = (String) topLevelObject.get(SECOND_LEVEL_KEY);
		assertThat("The second level result is not the expected.", secondLevelValue, is(SECOND_LEVEL_VALUE));
	}

	/**
	 * Test {@link Index#embedded(DBObject)} with an flattened {@link DBObject}.
	 */
	@Test
	public void testEmbeddedFlattenedDBObject()
	{
		final DBObject sourceObject = new BasicDBObject();
		sourceObject.put(ID_KEY, ID);
		sourceObject.put(TOP_LEVEL_KEY + "." + SECOND_LEVEL_KEY, SECOND_LEVEL_VALUE);

		final Index iut = new Index(INDEX_NAME, DEFAULT_ID_KEY, UNIQUE);
		final DBObject resultObject = iut.embedded(sourceObject);

		assertThat("The result is not the same object as the source.", resultObject, is(sourceObject));
		assertThat("The result has no '_id' field.", resultObject.containsField(ID_KEY));
		assertThat("The result has no top level field.", resultObject.containsField(TOP_LEVEL_KEY));

		final DBObject topLevelObject = (DBObject) resultObject.get(TOP_LEVEL_KEY);
		assertThat("The result has no second level field.", topLevelObject.containsField(SECOND_LEVEL_KEY));

		final String secondLevelValue = (String) topLevelObject.get(SECOND_LEVEL_KEY);
		assertThat("The second level result is not the expected.", secondLevelValue, is(SECOND_LEVEL_VALUE));
	}

	/**
	 * Test {@link Index#embedded(DBObject)} with an flattened {@link DBObject} with five levels depth.
	 */
	@Test
	public void testEmbeddedFlattenedDBObjectWithFiveLevelsDepth()
	{
		final DBObject sourceObject = new BasicDBObject();
		sourceObject.put(ID_KEY, ID);
		sourceObject.put(TOP_LEVEL_KEY + "." 
					+ SECOND_LEVEL_KEY + "." 
					+ THIRD_LEVEL_KEY + "." 
					+ FOURTH_LEVEL_KEY + "."
					+ FIFTH_LEVEL_KEY, FIFTH_LEVEL_VALUE);

		final Index iut = new Index(INDEX_NAME, DEFAULT_ID_KEY, UNIQUE);
		final DBObject resultObject = iut.embedded(sourceObject);

		assertThat("The result is not the same object as the source.", resultObject, is(sourceObject));
		assertThat("The result has no '_id' field.", resultObject.containsField(ID_KEY));
		assertThat("The result has no top level field.", resultObject.containsField(TOP_LEVEL_KEY));

		final DBObject topLevelObject = (DBObject) resultObject.get(TOP_LEVEL_KEY);
		assertThat("The result has no second level field.", topLevelObject.containsField(SECOND_LEVEL_KEY));

		final DBObject secondLevelObject = (DBObject) topLevelObject.get(SECOND_LEVEL_KEY);
		assertThat("The result has no third level field.", secondLevelObject.containsField(THIRD_LEVEL_KEY));

		final DBObject thirdLevelObject = (DBObject) secondLevelObject.get(THIRD_LEVEL_KEY);
		assertThat("The result has no fourth level field.", thirdLevelObject.containsField(FOURTH_LEVEL_KEY));

		final DBObject fourthLevelObject = (DBObject) thirdLevelObject.get(FOURTH_LEVEL_KEY);
		assertThat("The result has no fifth level field.", fourthLevelObject.containsField(FIFTH_LEVEL_KEY));

		final String fithLevelValue = (String) fourthLevelObject.get(FIFTH_LEVEL_KEY);
		assertThat("The fifth level result is not the expected.", fithLevelValue, is(FIFTH_LEVEL_VALUE));
	}

	/**
	 * Test {@link Index#embedded(DBObject)} with an flattened {@link DBObject} with two elements on the same level.
	 */
	@Test
	public void testEmbeddedFlattenedDBObjectWithTwoElementOnTheSameLevel()
	{
		final DBObject sourceObject = new BasicDBObject();
		sourceObject.put(ID_KEY, ID);
		sourceObject.put(TOP_LEVEL_KEY + "." + SECOND_LEVEL_KEY, SECOND_LEVEL_VALUE);
		sourceObject.put(TOP_LEVEL_KEY + "." + FIFTH_LEVEL_KEY, FIFTH_LEVEL_VALUE);

		final Index iut = new Index(INDEX_NAME, DEFAULT_ID_KEY, UNIQUE);
		final DBObject resultObject = iut.embedded(sourceObject);

		assertThat("The result is not the same object as the source.", resultObject, is(sourceObject));
		assertThat("The result has no '_id' field.", resultObject.containsField(ID_KEY));
		assertThat("The result has no top level field.", resultObject.containsField(TOP_LEVEL_KEY));

		final DBObject topLevelObject = (DBObject) resultObject.get(TOP_LEVEL_KEY);
		assertThat("The result has no second level field.", topLevelObject.containsField(SECOND_LEVEL_KEY));
		assertThat("The result has no second level field.", topLevelObject.containsField(FIFTH_LEVEL_KEY));

		final String secondLevelObject = (String) topLevelObject.get(SECOND_LEVEL_KEY);
		assertThat("The second level element 1 result is not the expected.", secondLevelObject, is(SECOND_LEVEL_VALUE));

		final String fithLevelValue = (String) topLevelObject.get(FIFTH_LEVEL_KEY);
		assertThat("The second level element 2 result is not the expected.", fithLevelValue, is(FIFTH_LEVEL_VALUE));
	}
}
