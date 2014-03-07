package com.github.fakemongo.impl.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fakemongo.impl.ExpressionParser;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.FongoDBCollection;

/**
 * An index for the MongoDB.
 */
public class Index extends IndexAbstract<DBObject> {
  static final Logger LOG = LoggerFactory.getLogger(Index.class);

  Index(String name, DBObject keys, boolean unique) {
    super(name, keys, unique, createMap(keys, unique), null);
  }

  private static Map<DBObject, List<DBObject>> createMap(DBObject keys, boolean unique) {
    // Preserve order only for id.
    if (unique && keys.containsField(FongoDBCollection.ID_KEY) && keys.toMap().size() == 1) {
      return new LinkedHashMap<DBObject, List<DBObject>>();
    } else {
      //noinspection unchecked
      return new TreeMap<DBObject, List<DBObject>>(new ExpressionParser().buildObjectComparator(isAsc(keys)));
    }
  }

  @Override
  public DBObject embedded(DBObject object) {
		return expandObject(object); // Important : do not clone, indexes share objects between them.
  }

	/**
	 * Expand all flattened {@link DBObject}s to match the current MongoDB behaviour.
	 * 
	 * @param object
	 *            The {@link DBObject} to insert.
	 * @return The expanded {@link DBObject}.
	 */
	private DBObject expandObject(final DBObject object)
	{
		final List<String> keysToRemove = new ArrayList<String>();
		final List<DBObject> objectsToPut = new ArrayList<DBObject>();

		for (final String key : object.keySet())
		{
			if (key.contains("."))
			{
				final Object actualValue = object.get(key);

				DBObject expandedObject = null;
				final String[] splittedKeys = key.split("\\.");

				for (int i = splittedKeys.length - 1; i >= 0; i--)
				{
					if (expandedObject == null)
					{
						expandedObject = new BasicDBObject(splittedKeys[i], actualValue);
					}
					else
					{
						final DBObject partialObject = expandedObject;
						if(partialObject.containsField(splittedKeys[i]))
						{
							final DBObject existingFieldObject = (DBObject) partialObject.get(splittedKeys[i]);
						}
						expandedObject = new BasicDBObject(splittedKeys[i], partialObject);
					}
				}

				keysToRemove.add(key);
				objectsToPut.add(expandedObject);
			}
		}

		for (final String keyToRemove : keysToRemove)
		{
			object.removeField(keyToRemove);
		}

		for (final DBObject objectToPut : objectsToPut)
		{
			final String rootElement = objectToPut.keySet().iterator().next();
			if (object.containsField(rootElement))
			{
				DBObject objectToAdd = (DBObject) objectToPut.get(rootElement);
				((DBObject) object.get(rootElement)).putAll(objectToAdd);
			}
			else
			{
				object.putAll(objectToPut);
			}
		}

		return object;
	}
}
