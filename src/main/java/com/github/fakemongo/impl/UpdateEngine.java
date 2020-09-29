package com.github.fakemongo.impl;

import com.github.fakemongo.FongoException;
import static com.github.fakemongo.impl.Util.genericMax;
import static com.github.fakemongo.impl.Util.genericMin;
import static com.github.fakemongo.impl.Util.genericMul;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateEngine {
  final static Logger LOG = LoggerFactory.getLogger(UpdateEngine.class);

  private final ExpressionParser expressionParser = new ExpressionParser();


  void keyCheck(String key, Set<String> seenKeys) {
    if (!seenKeys.add(key)) {
      throw new FongoException("attempting more than one atomic update on on " + key);
    }
  }


  abstract class BasicUpdate {

    private final boolean createMissing;
    final String command;

    public BasicUpdate(String command, boolean createMissing) {
      this.command = command;
      this.createMissing = createMissing;
    }

    abstract void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated);

    public DBObject doUpdate(DBObject obj, DBObject update, Set<String> seenKeys, DBObject query, boolean isCreated) {
      DBObject updateObject = ExpressionParser.toDbObject(update.get(command));
      HashSet<String> keySet = new HashSet<String>(updateObject.keySet());

      LOG.debug("KeySet is of length {}", keySet.size());

      for (String updateKey : keySet) {
        LOG.debug("\tfound a key {}", updateKey);

        keyCheck(updateKey, seenKeys);
        doSingleKeyUpdate(updateKey, obj, updateObject.get(updateKey), query, isCreated);
      }
      return obj;
    }

    void doSingleKeyUpdate(final String updateKey, final DBObject objOriginal, Object object, DBObject query, boolean isCreated) {
      List<String> path = Util.split(updateKey);
      String subKey = path.get(0);
      DBObject obj = objOriginal;
      boolean isPositional = updateKey.contains(".$");
      if (isPositional) {
        LOG.debug("got a positional for query {}", query);
      }
      for (int i = 0; i < path.size() - 1; i++) {
        if (!obj.containsField(subKey)) {
          if (createMissing && !isPositional) {
            obj.put(subKey, new BasicDBObject());
          } else {
            return;
          }
        }
        Object value = obj.get(subKey);
        if ((value instanceof List) && "$".equals(path.get(i + 1))) {
          handlePositionalUpdate(updateKey, object, (List) value, obj, query, objOriginal);
        } else if (ExpressionParser.isDbObject(value)) {
          obj = ExpressionParser.toDbObject(value);
        } else if (value instanceof List) {
          BasicDBList newList = Util.wrap((List) value);

          obj = newList;
        } else {
          throw new FongoException("subfield must be object. " + updateKey + " not in " + objOriginal);
        }
        subKey = path.get(i + 1);
      }
      if (!isPositional) {

        LOG.debug("Subobject is {}", obj);
        mergeAction(subKey, obj, object, objOriginal, isCreated);
        LOG.debug("Full object is {}", objOriginal);

      }
    }

    public void handlePositionalUpdate(final String updateKey, Object object, List valueList, DBObject ownerObj, DBObject query, DBObject objOriginal) {
      int dollarIndex = updateKey.indexOf("$");
      String postPath = (dollarIndex == updateKey.length() - 1) ? "" : updateKey.substring(dollarIndex + 2);
      String prePath = updateKey.substring(0, dollarIndex - 1);
      //create a filter from the original query
      Filter filter = null;
      for (String key : query.keySet()) {
        if (key.startsWith(prePath)) {
          String matchKey = prePath.equals(key) ? key : key.substring(prePath.length() + 1);
          filter = expressionParser.buildFilter(new BasicDBObject(matchKey, query.get(key)));
        }
      }
      if (filter == null) {
        throw new FongoException("positional operator " + updateKey + " must be used on query key " + query);
      }

      // find the right item
      for (int i = 0; i < valueList.size(); i++) {
        Object listItem = valueList.get(i);
        if (LOG.isDebugEnabled()) {
          LOG.debug("found a positional list item " + listItem + " " + prePath + " " + postPath);
        }
        if (!postPath.isEmpty()) {
          if (!ExpressionParser.isDbObject(listItem)) {
            throw new FongoException("can not update \"" + postPath + "\" field of non-DBObject object");
          }

          BasicDBList listWithSingleItem = new BasicDBList();
          listWithSingleItem.add(listItem);
          if (filter.apply(ExpressionParser.toDbObject(listItem)) ||
              //Case of a nested $elemMatch
              filter.apply(new BasicDBObject(prePath, listWithSingleItem))) {
            doSingleKeyUpdate(postPath, ExpressionParser.toDbObject(listItem), object, query, false);
            break;
          }
        } else {
          //this is kind of a waste
          DBObject o = ExpressionParser.isDbObject(listItem) ? ExpressionParser.toDbObject(listItem) : new BasicDBObject(prePath, listItem);
          BasicDBList listWithSingleItem = new BasicDBList();
          listWithSingleItem.add(listItem);
          if (filter.apply(o) ||
              //Case of a nested $elemMatch
              filter.apply(new BasicDBObject(prePath, listWithSingleItem))) {
            BasicDBList newList = new BasicDBList();
            newList.addAll(valueList);
            //do not put any data on ownerObj, because the prePath can be composed of different parts
            mergeAction(String.valueOf(i), newList, object, objOriginal, false);
            //repopulate the valueList
            valueList.clear();
            valueList.addAll(newList);
            break;
          }
        }
      }
    }
  }

  final List<BasicUpdate> commands = Arrays.<BasicUpdate>asList(
      new BasicUpdate("$set", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          subObject.put(subKey, object);
        }
      },
      new BasicUpdate("$setOnInsert", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          if (isCreated) {
            subObject.put(subKey, object);
          }
        }
      },
      new BasicUpdate("$max", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          if (object instanceof Number) {
            Number updateNumber = expressionParser.typecast(command + " value", object, Number.class);
            Object oldValue = subObject.get(subKey);
            if (oldValue == null) {
              subObject.put(subKey, updateNumber);
            } else {
              Number oldNumber = expressionParser.typecast(subKey + " value", oldValue, Number.class);
              subObject.put(subKey, genericMax(oldNumber, updateNumber));
            }
          } else if (object instanceof Date) {
            Date updateNumber = expressionParser.typecast(command + " value", object, Date.class);
            Object oldValue = subObject.get(subKey);
            if (oldValue == null) {
              subObject.put(subKey, updateNumber);
            } else {
              Date oldNumber = expressionParser.typecast(subKey + " value", oldValue, Date.class);
              subObject.put(subKey, genericMax(oldNumber, updateNumber));
            }
          } else {
            throw new FongoException(subKey + " expected to be of type Date/Number but is " + (object != null ? object.getClass() : "null") + " toString:" + object);
          }
        }
      },
      new BasicUpdate("$min", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          if (object instanceof Number) {
            Number updateNumber = expressionParser.typecast(command + " value", object, Number.class);
            Object oldValue = subObject.get(subKey);
            if (oldValue == null) {
              subObject.put(subKey, updateNumber);
            } else {
              Number oldNumber = expressionParser.typecast(subKey + " value", oldValue, Number.class);
              subObject.put(subKey, genericMin(oldNumber, updateNumber));
            }
          } else if (object instanceof Date) {
            Date updateNumber = expressionParser.typecast(command + " value", object, Date.class);
            Object oldValue = subObject.get(subKey);
            if (oldValue == null) {
              subObject.put(subKey, updateNumber);
            } else {
              Date oldNumber = expressionParser.typecast(subKey + " value", oldValue, Date.class);
              subObject.put(subKey, genericMin(oldNumber, updateNumber));
            }
          } else {
            throw new FongoException(subKey + " expected to be of type Date/Number but is " + (object != null ? object.getClass() : "null") + " toString:" + object);
          }
        }
      },
      new BasicUpdate("$inc", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          Number updateNumber = expressionParser.typecast(command + " value", object, Number.class);
          Object oldValue = subObject.get(subKey);
          if (oldValue == null) {
            subObject.put(subKey, updateNumber);
          } else {
            Number oldNumber = expressionParser.typecast(subKey + " value", oldValue, Number.class);
            subObject.put(subKey, Util.genericAdd(oldNumber, updateNumber));
          }
        }
      },
      new BasicUpdate("$mul", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          Number updateNumber = expressionParser.typecast(command + " value", object, Number.class);
          Object oldValue = subObject.get(subKey);
          if (oldValue == null) {
            subObject.put(subKey, genericMul(0, updateNumber));
          } else {
            Number oldNumber = expressionParser.typecast(subKey + " value", oldValue, Number.class);
            subObject.put(subKey, genericMul(oldNumber, updateNumber));
          }
        }
      },
      new BasicUpdate("$unset", false) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          subObject.removeField(subKey);
        }
      },
      new BasicUpdate("$rename", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          if(!subObject.containsField(subKey)) return;
          Object objValue = subObject.removeField(subKey);
          String newKey = (String) object;
          Util.putValue(objOriginal, newKey, objValue);
        }
      },
      new BasicUpdate("$push", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          BasicDBList currentValue;
          if (subObject.containsField(subKey)) {
            currentValue = expressionParser.typecast(subKey, subObject.get(subKey), BasicDBList.class);
          } else {
            currentValue = new BasicDBList();
          }

          if (ExpressionParser.isDbObject(object) && ((ExpressionParser.toDbObject(object)).get("$each") != null)) {
            DBObject dbObject = ExpressionParser.toDbObject(object);
            Object eachObject = dbObject.get("$each");
            BasicDBList eachList = expressionParser.typecast(command + ".$each value", eachObject, BasicDBList.class);

            // position
            int pos = currentValue.size();
            Object positionObject = dbObject.get("$position");
            if (positionObject != null) {
              pos = expressionParser.typecast(command + ".$position value", positionObject, Number.class).intValue();
              if (pos >= currentValue.size()) {
                pos = currentValue.size();
              }
            }
            currentValue.addAll(pos, eachList);

            // sort
            Object sortObj = dbObject.get("$sort");
            if (sortObj != null) {
              if (sortObj instanceof Number) {
                int sortDirection = ((Number) sortObj).intValue();
                Collections.sort(currentValue, expressionParser.objectComparator(sortDirection));
              } else if (ExpressionParser.isDbObject(sortObj)) {
                Collections.sort(currentValue, expressionParser.sortSpecificationComparator(ExpressionParser.toDbObject(sortObj)));
              }
            }

            // slice
            Object sliceObject = dbObject.get("$slice");
            if (sliceObject != null) {
              int slice = expressionParser.typecast(command + ".slice value", sliceObject, Number.class).intValue();
              if (slice == 0) {
                currentValue.clear();
                currentValue.trimToSize();
              } else if (slice > 0) {
                BasicDBList subList = new BasicDBList();
                subList.addAll(currentValue.subList(0, Math.min(slice, currentValue.size())));
                currentValue = subList;
              } else if (slice < 0 && currentValue.size() + slice >= 0) {
                BasicDBList subList = new BasicDBList();
                subList.addAll(currentValue.subList(Math.max(currentValue.size() + slice, 0), currentValue.size()));
                currentValue = subList;
              }
            }
          } else {
            currentValue.add(object);
          }

          subObject.put(subKey, currentValue);
        }
      },
      new BasicUpdate("$pushAll", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          List newList = expressionParser.typecast(command + " value", object, List.class);
          if (!subObject.containsField(subKey)) {
            subObject.put(subKey, newList);
          } else {
            BasicDBList currentValue = expressionParser.typecast(subKey, subObject.get(subKey), BasicDBList.class);
            currentValue.addAll(newList);
            subObject.put(subKey, currentValue);
          }
        }
      },
      new BasicUpdate("$addToSet", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          boolean isEach = false;
          BasicDBList currentValue = expressionParser.typecast(subKey, subObject.get(subKey), BasicDBList.class);
          currentValue = (currentValue == null) ? new BasicDBList() : currentValue;
          if (ExpressionParser.isDbObject(object)) {
            Object eachObject = ExpressionParser.toDbObject(object).get("$each");
            if (eachObject != null) {
              isEach = true;
              BasicDBList newList = expressionParser.typecast(command + ".$each value", eachObject, BasicDBList.class);
              if (newList == null) {
                throw new FongoException(command + ".$each must not be null");
              }
              for (Object newValue : newList) {
                if (!currentValue.contains(newValue)) {
                  currentValue.add(newValue);
                }
              }
            }
          }
          if (!isEach) {
            if (!currentValue.contains(object)) {
              currentValue.add(object);
            }
          }
          subObject.put(subKey, currentValue);
        }
      },
      new BasicUpdate("$pop", false) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          BasicDBList currentList = expressionParser.typecast(command, subObject.get(subKey), BasicDBList.class);
          if (currentList != null && currentList.size() > 0) {
            int direction = expressionParser.typecast(command, object, Number.class).intValue();
            if (direction > 0) {
              currentList.remove(currentList.size() - 1);
            } else {
              currentList.remove(0);
            }
          }
        }
      },
      new BasicUpdate("$pull", false) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          BasicDBList currentList = expressionParser.typecast(command + " only works on arrays", subObject.get(subKey), BasicDBList.class);
          if (currentList != null && currentList.size() > 0) {
            BasicDBList newList = new BasicDBList();
            if (ExpressionParser.isDbObject(object)) {
              ValueFilter filter = expressionParser.buildValueFilter(ExpressionParser.toDbObject(object));
              for (Object item : currentList) {
                if (!filter.apply(item)) {
                  newList.add(item);
                }
              }
            } else if (object != null) {
              for (Object item : currentList) {
                if (expressionParser.compareObjects(object, item) != 0) {
                  newList.add(item);
                }
              }
            }
            subObject.put(subKey, newList);
          }
        }
      },
      new BasicUpdate("$pullAll", false) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          BasicDBList currentList = expressionParser.typecast(command + " only works on arrays", subObject.get(subKey), BasicDBList.class);
          if (currentList != null && currentList.size() > 0) {
            Set<Object> pullSet = new HashSet<Object>(expressionParser.typecast(command, object, List.class));
            BasicDBList newList = new BasicDBList();
            for (Object item : currentList) {
              if (!pullSet.contains(item)) {
                newList.add(item);
              }
            }
            subObject.put(subKey, newList);
          }
        }
      },
      new BasicUpdate("$bit", false) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          Number currentNumber = expressionParser.typecast(command + " only works on integers", subObject.get(subKey), Number.class);
          if (currentNumber != null) {
            if (currentNumber instanceof Float || currentNumber instanceof Double) {
              throw new FongoException(command + " only works on integers");
            }
            DBObject bitOps = expressionParser.typecast(command, object, DBObject.class);
            for (String op : bitOps.keySet()) {
              Number opValue = expressionParser.typecast(command + "." + op, bitOps.get(op), Number.class);
              if ("and".equals(op)) {
                if (opValue instanceof Long || currentNumber instanceof Long) {
                  currentNumber = currentNumber.longValue() & opValue.longValue();
                } else {
                  currentNumber = currentNumber.intValue() & opValue.intValue();
                }
              } else if ("or".equals(op)) {
                if (opValue instanceof Long || currentNumber instanceof Long) {
                  currentNumber = currentNumber.longValue() | opValue.longValue();
                } else {
                  currentNumber = currentNumber.intValue() | opValue.intValue();
                }
              } else {
                throw new FongoException(command + "." + op + " is not valid.");
              }
            }
            subObject.put(subKey, currentNumber);
          }
        }
      },
      new BasicUpdate("$currentDate", true) {
        @Override
        void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
          if (Boolean.TRUE.equals(object)) {
            subObject.put(subKey, new Date());
          } else if ((objOriginal != null) && (ExpressionParser.isDbObject(object))) {
            Object typeObject = ExpressionParser.toDbObject(object).get("$type");
            String type = expressionParser.typecast(command, typeObject, String.class);
            if ("date".equals(type)) {
              subObject.put(subKey, new Date());
            } else {
              throw new FongoException(command + " called with unsupported type");
            }
          } else {
            throw new FongoException(command + " parameters should be either a boolean true or a document specifying a type");
          }
        }
      }
  );
  final Map<String, BasicUpdate> commandMap = createCommandMap();
  private final BasicUpdate basicUpdateForUpsert = new BasicUpdate("upsert", true) {
    @Override
    void mergeAction(String subKey, DBObject subObject, Object object, DBObject objOriginal, boolean isCreated) {
      subObject.put(subKey, object);
    }
  };

  private Map<String, BasicUpdate> createCommandMap() {
    Map<String, BasicUpdate> map = new HashMap<String, BasicUpdate>();
    for (BasicUpdate item : commands) {
      map.put(item.command, item);
    }
    return map;
  }

  public DBObject doUpdate(final DBObject obj, final DBObject update) {
    return doUpdate(obj, update, new BasicDBObject(), true);
  }

  /**
   * @param obj
   * @param update
   * @param query
   * @param isCreated true if it's a new object.
   * @return
   */
  public DBObject doUpdate(final DBObject obj, final DBObject update, DBObject query, boolean isCreated) {
    boolean updateDone = false;
    Set<String> seenKeys = new HashSet<String>();
    for (String command : update.keySet()) {
      BasicUpdate basicUpdate = commandMap.get(command);
      if (basicUpdate != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Doing update for command {}", command);
        }
        basicUpdate.doUpdate(obj, update, seenKeys, query, isCreated);
        updateDone = true;
      } else if (command.startsWith("$")) {
        throw new FongoException("unsupported update: " + update);
      }
    }
    if (!updateDone) {
      for (Iterator<String> iter = obj.keySet().iterator(); iter.hasNext(); ) {
        String key = iter.next();
        if (!key.equals("_id")) {
          iter.remove();
        }
      }
      obj.putAll(update);
    }
    return obj;
  }

  public void mergeEmbeddedValueFromQuery(BasicDBObject newObject, DBObject q) {
    basicUpdateForUpsert.doUpdate(newObject, new BasicDBObject(basicUpdateForUpsert.command, q), new HashSet<String>(), q, false);
  }
}
