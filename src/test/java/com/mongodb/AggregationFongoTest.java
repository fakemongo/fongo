package com.mongodb;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.fakemongo.Fongo;
import com.github.fakemongo.FongoException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class AggregationFongoTest {

	
	private MongoCollection<Document> collection;
	
	@Before
	public void beforeMethod(){
		Fongo fongo = new Fongo("test");
		
		MongoDatabase mongoDatabase = fongo.getMongo().getDatabase("test");
		collection = mongoDatabase.getCollection("example");
	}
	
	@After
	public void afetrMethod(){
		collection.drop();
	}
	
	@Test
	public void AValid$Project$filterPipelineWithCondition$eq50_AValidDocumentWithAnArrayWithTwoElementWhereOnlyOneMatchWithThePipeline_thenReturnADocumentWithOnlyOneArrayElement(){
		
		Document workstation1 = new Document("name", "WK1").append("state", 700);
		Document workstation2 = new Document("name", "WK2").append("state", 50);
		Document  workstations = new Document("_id", "1");
		workstations.append("workstations", Arrays.asList(workstation1, workstation2));
		
		
		
		Document under_filter = new Document();
		under_filter.append("input", "$workstations")
		            .append("as", "wk")
		            .append("cond", new Document("$eq", Arrays.asList("$$wk.state", 50)));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		
		Document  workstationsResult = new Document("_id", "1");
		workstationsResult.append("items", Arrays.asList(new Document("name", "WK2").append("state", 50)));
		
		
		collection.insertMany(Arrays.asList(workstations));
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		Document result = iterable.first();
		
		assertEquals(workstationsResult.toJson(), result.toJson());
				
	}
	
	@Test
	public void AValid$Project$filterPipelineWithCondition$eq700_AValidDocumentWithAnArrayWithTwoElementWhereOnlyOneMatchWithThePipeline_thenReturnADocumentWithOnlyOneArrayElement(){
		Document workstation1 = new Document("name", "WK1").append("state", 700);
		Document workstation2 = new Document("name", "WK2").append("state", 50);
		Document  workstations = new Document("_id", "1");
		workstations.append("workstations", Arrays.asList(workstation1, workstation2));
		
		Document under_filter = new Document();
		under_filter.append("input", "$workstations")
		            .append("as", "wk")
		            .append("cond", new Document("$eq", Arrays.asList("$$wk.state", 700)));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		
		Document  workstationsResult = new Document("_id", "1");
		workstationsResult.append("items", Arrays.asList(new Document("name", "WK1").append("state", 700)));
		
		collection.insertMany(Arrays.asList(workstations));
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		Document result = iterable.first();
		
		assertEquals(workstationsResult.toJson(), result.toJson());
	}
	
	
	@Test
	public void AValid$Project$filterPipelineWithCondition$eq700_AValidDocumentWithAnArrayWithNoElementMatchsWithThePipeline_thenReturnADocumentWithNoArrayElement(){
		
		Document workstation1 = new Document("name", "WK1").append("state", 300);
		Document workstation2 = new Document("name", "WK2").append("state", 50);
		Document  workstations = new Document("_id", "1");
		workstations.append("workstations", Arrays.asList(workstation1, workstation2));
		
		Document under_filter = new Document();
		under_filter.append("input", "$workstations")
		            .append("as", "wk")
		            .append("cond", new Document("$eq", Arrays.asList("$$wk.state", 700)));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		Document  workstationsResult = new Document("_id", "1");
		workstationsResult.append("items", Arrays.asList());
		
		collection.insertMany(Arrays.asList(workstations));
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		Document result = iterable.first();
		
		assertEquals(workstationsResult.toJson(), result.toJson());
		
	}
	
	@Test
	public void AValid$Project$filterPipelineWithCondition$eq300_TwoValidDocumentsWithAnArrayWithTwoElementsWhichMatchsWithThePipeline_thenReturnTwoDocumentWithAArrayElementForBoth(){
		
		Document workstation1_1 = new Document("name", "WK1").append("state", 300);
		Document workstation1_2 = new Document("name", "WK2").append("state", 50);
		Document  workstations1 = new Document("_id", "1");
		workstations1.append("workstations", Arrays.asList(workstation1_1, workstation1_2));
		
		Document workstation2_1 = new Document("name", "WK1").append("state", 300);
		Document workstation2_2 = new Document("name", "WK2").append("state", 50);
		Document  workstations2 = new Document("_id", "2");
		workstations2.append("workstations", Arrays.asList(workstation2_1, workstation2_2));
		
		
		Document under_filter = new Document();
		under_filter.append("input", "$workstations")
		            .append("as", "wk")
		            .append("cond", new Document("$eq", Arrays.asList("$$wk.state", 300)));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		
		Document  workstationsResult1 = new Document("_id", "1");
		workstationsResult1.append("items", Arrays.asList(new Document("name", "WK1").append("state", 300)));
		
		Document  workstationsResult2 = new Document("_id", "2");
		workstationsResult2.append("items", Arrays.asList(new Document("name", "WK1").append("state", 300)));
		
		
		collection.insertMany(Arrays.asList(workstations1, workstations2));
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		

		final Map<String, Document> result = new HashMap<>();
		
		iterable.forEach(new Block<Document>() {

			@Override
			public void apply(Document document) {
				result.put((String) document.get("_id"), document);
				
			}
		});
				
		assertEquals(workstationsResult1.toJson(), result.get("1").toJson());
		assertEquals(workstationsResult2.toJson(), result.get("2").toJson());
	}
	

	
	@Test(expected = FongoException.class)
	public void ANotValid$Project$filterPipelineWithNoArray_AValidDocuments_thenARuntimeExceptionWillBeThrown(){
		
		Document workstation1 = new Document("name", "WK1").append("state", 700);
		Document workstation2 = new Document("name", "WK2").append("state", 50);
		Document  workstations = new Document("_id", "1");
		workstations.append("workstations", Arrays.asList(workstation1, workstation2));
		workstations.append("location", new Document("town", "rome").append("street", "Volsci street"));
		
		
		Document under_filter = new Document();
		under_filter.append("input", "$location")
		            .append("as", "wk")
		            .append("cond", new Document("$eq", Arrays.asList("$$wk.town", "Rome")));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		
		collection.insertMany(Arrays.asList(workstations));
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		iterable.first();
	}
	
	@Test
	public void ANotValid$Project$filterPipelineWithAWrongInputValue_AValidDocuments_thenReturnAdocumentWithNullItemsValue(){
		
		Document workstation1 = new Document("name", "WK1").append("state", 700);
		Document workstation2 = new Document("name", "WK2").append("state", 50);
		Document  workstations = new Document("_id", "1");
		workstations.append("workstations", Arrays.asList(workstation1, workstation2));
		workstations.append("location", new Document("town", "rome").append("street", "Volsci street"));
		
		
		Document under_filter = new Document();
		under_filter.append("input", "$WRONG_workstations")
		            .append("as", "wk")
		            .append("cond", new Document("$eq", Arrays.asList("$$wk.town", "Rome")));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		Document  workstationsResult = new Document("_id", "1");
		workstationsResult.append("items", null);
		
		
		collection.insertMany(Arrays.asList(workstations));
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		Document result = iterable.first();
		
		assertEquals(workstationsResult.toJson(), result.toJson());
		
	}
	
	
	@Test
	public void ANotValid$Project$filterPipelineWithAWrongInputValue_TwoValidDocuments_thenReturnAdocumentWithNullItemsValue(){
		
		Document workstation1_1 = new Document("name", "WK1").append("state", 700);
		Document workstation1_2 = new Document("name", "WK2").append("state", 50);
		Document  workstations1 = new Document("_id", "1");
		workstations1.append("workstations", Arrays.asList(workstation1_1, workstation1_2));
		workstations1.append("location", new Document("town", "rome").append("street", "Volsci street"));
		
		Document workstation2_1 = new Document("name", "WK1").append("state", 700);
		Document workstation2_2 = new Document("name", "WK2").append("state", 50);
		Document  workstations2 = new Document("_id", "2");
		workstations2.append("workstations", Arrays.asList(workstation2_1, workstation2_2));
		workstations2.append("location", new Document("town", "rome").append("street", "Volsci street"));
		
		
		Document under_filter = new Document();
		under_filter.append("input", "$WRONG_workstations")
		            .append("as", "wk")
		            .append("cond", new Document("$eq", Arrays.asList("$$wk.town", "Rome")));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		Document  workstationsResult1 = new Document("_id", "1");
		workstationsResult1.append("items", null);
		
		Document  workstationsResult2 = new Document("_id", "2");
		workstationsResult2.append("items", null);
		
		
		collection.insertMany(Arrays.asList(workstations1, workstations2));
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		final Map<String, Document> result = new HashMap<>();
		
		iterable.forEach(new Block<Document>() {

			@Override
			public void apply(Document document) {
				result.put((String) document.get("_id"), document);
				
			}
		});
				
		assertEquals(workstationsResult1.toJson(), result.get("1").toJson());
		assertEquals(workstationsResult2.toJson(), result.get("2").toJson());
		
	}
	
	/**
	 * This comes from the MongoDocumentation
	 * https://docs.mongodb.com/v3.2/reference/operator/aggregation/filter/
	 */
	@Test
	public void documentionTest(){
		
		Document item1_1 = new Document("item_id", 43).append("quantity", 2).append("price", 10);
		Document item1_2 = new Document("item_id", 2).append("quantity", 1).append("price", 240);
		Document document1 = new Document("_id", 0).append("items", Arrays.asList(item1_1, item1_2));
		
		Document item2_1 = new Document("item_id", 23).append("quantity", 3).append("price", 110);
		Document item2_2 = new Document("item_id", 103).append("quantity", 4).append("price", 5);
		Document item2_3 = new Document("item_id", 38).append("quantity", 1).append("price", 300);
		Document document2 = new Document("_id", 1).append("items", Arrays.asList(item2_1, item2_2, item2_3));
		
		Document item3_1 = new Document("item_id", 4).append("quantity", 1).append("price", 23);
		Document document3 = new Document("_id", 2).append("items", Arrays.asList(item3_1));
		
		
		collection.insertMany(Arrays.asList(document1, document2, document3));

		Document under_filter = new Document();
		under_filter.append("input", "$items")
		            .append("as", "item")
		            .append("cond", new Document("$gte", Arrays.asList("$$item.price", 100)));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		Document itemResult1_1 = new Document("item_id", 2).append("quantity", 1).append("price", 240);
		Document documentResult1 = new Document("_id", 0).append("items", Arrays.asList(itemResult1_1));
		
		Document itemResult2_1 = new Document("item_id", 23).append("quantity", 3).append("price", 110);
		Document itemResult2_2 = new Document("item_id", 38).append("quantity", 1).append("price", 300);
		Document document1Result2 = new Document("_id", 1).append("items", Arrays.asList(itemResult2_1, itemResult2_2));
		
		Document documentResult3 = new Document("_id", 2).append("items", Arrays.asList());
		
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		final Map<Integer, Document> result = new HashMap<>();
		
		iterable.forEach(new Block<Document>() {

			@Override
			public void apply(Document document) {
				result.put( (Integer) document.get("_id"), document);
				
			}
		});
				
		assertEquals(documentResult1.toJson(), result.get(0).toJson());
		assertEquals(document1Result2.toJson(), result.get(1).toJson());
		assertEquals(documentResult3.toJson(), result.get(2).toJson());
	}
	
	
	@Test(expected = IllegalArgumentException.class)
	public void ANotValid$Project$filterPipelineWithAAsValueWhichDoesntMAtch_AValidDocuments_thenARuntimeExceptionWillBeThrown(){
		Document item1_1 = new Document("item_id", 43).append("quantity", 2).append("price", 10);
		Document item1_2 = new Document("item_id", 2).append("quantity", 1).append("price", 240);
		Document document1 = new Document("_id", 0).append("items", Arrays.asList(item1_1, item1_2));
		
		Document item2_1 = new Document("item_id", 23).append("quantity", 3).append("price", 110);
		Document item2_2 = new Document("item_id", 103).append("quantity", 4).append("price", 5);
		Document item2_3 = new Document("item_id", 38).append("quantity", 1).append("price", 300);
		Document document2 = new Document("_id", 1).append("items", Arrays.asList(item2_1, item2_2, item2_3));
		
		Document item3_1 = new Document("item_id", 4).append("quantity", 1).append("price", 23);
		Document document3 = new Document("_id", 2).append("items", Arrays.asList(item3_1));
		
		collection.insertMany(Arrays.asList(document1, document2, document3));

		Document under_filter = new Document();
		under_filter.append("input", "$items")
		            .append("as", "WRONG_item")
		            .append("cond", new Document("$gte", Arrays.asList("$$item.price", 100)));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		iterable.first();
		
	}
	
	
	@Test
	public void AValid$Project$filterPipelineWithEmptyCondition_ValidDocuments_thenReturnDocumentsNotProjected(){
		
		Document item1_1 = new Document("item_id", 43).append("quantity", 2).append("price", 10);
		Document item1_2 = new Document("item_id", 2).append("quantity", 1).append("price", 240);
		Document document1 = new Document("_id", 0).append("items", Arrays.asList(item1_1, item1_2));
		
		Document item2_1 = new Document("item_id", 23).append("quantity", 3).append("price", 110);
		Document item2_2 = new Document("item_id", 103).append("quantity", 4).append("price", 5);
		Document item2_3 = new Document("item_id", 38).append("quantity", 1).append("price", 300);
		Document document2 = new Document("_id", 1).append("items", Arrays.asList(item2_1, item2_2, item2_3));
		
		Document item3_1 = new Document("item_id", 4).append("quantity", 1).append("price", 23);
		Document document3 = new Document("_id", 2).append("items", Arrays.asList(item3_1));
		
		
		collection.insertMany(Arrays.asList(document1, document2, document3));

		Document under_filter = new Document();
		under_filter.append("input", "$items")
		            .append("as", "item")
		            .append("cond", new Document());
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		final Map<Integer, Document> result = new HashMap<>();
		
		iterable.forEach(new Block<Document>() {

			@Override
			public void apply(Document document) {
				result.put( (Integer) document.get("_id"), document);
				
			}
		});
				
		assertEquals(document1.toJson(), result.get(0).toJson());
		assertEquals(document2.toJson(), result.get(1).toJson());
		assertEquals(document3.toJson(), result.get(2).toJson());
	}
	
	
	
	
	
	@Test
	public void AValid$Project$filterPipelineWithMultiFunction_AValidDocuments_thenReturnAProjectedDocument(){
		
		Document item1_1 = new Document("item_id", 43).append("quantity", 2).append("price", 10);
		Document item1_2 = new Document("item_id", 2).append("quantity", 1).append("price", 240);
		Document document1 = new Document("_id", 0).append("items", Arrays.asList(item1_1, item1_2));
		
		Document item2_1 = new Document("item_id", 23).append("quantity", 3).append("price", 110);
		Document item2_2 = new Document("item_id", 103).append("quantity", 4).append("price", 5);
		Document item2_3 = new Document("item_id", 38).append("quantity", 1).append("price", 300);
		Document document2 = new Document("_id", 1).append("items", Arrays.asList(item2_1, item2_2, item2_3));
		
		Document item3_1 = new Document("item_id", 4).append("quantity", 1).append("price", 23);
		Document document3 = new Document("_id", 2).append("items", Arrays.asList(item3_1));
		
		
		collection.insertMany(Arrays.asList(document1, document2, document3));

		Document under_filter = new Document();
		under_filter.append("input", "$items")
		            .append("as", "item")
		            .append("cond", new Document("$and", Arrays.asList(new Document("$gte", Arrays.asList("$$item.price", 100)), new Document("$lte", Arrays.asList("$$item.price", 250)))));//new Document("$gte", Arrays.asList("$$item.price", 100)));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);
		
		Document itemResult1_1 = new Document("item_id", 2).append("quantity", 1).append("price", 240);
		Document documentResult1 = new Document("_id", 0).append("items", Arrays.asList(itemResult1_1));
		
		Document itemResult2_1 = new Document("item_id", 23).append("quantity", 3).append("price", 110);
		Document documentResult2 = new Document("_id", 1).append("items", Arrays.asList(itemResult2_1));
		
		Document documentResult3 = new Document("_id", 2).append("items", Arrays.asList());
		
		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		final Map<Integer, Document> result = new HashMap<>();
		
		iterable.forEach(new Block<Document>() {

			@Override
			public void apply(Document document) {
				result.put( (Integer) document.get("_id"), document);
				
			}
		});
		
		assertEquals(documentResult1.toJson(), result.get(0).toJson());
		assertEquals(documentResult2.toJson(), result.get(1).toJson());
		assertEquals(documentResult3.toJson(), result.get(2).toJson());
	}
	
	
	@Test
	public void AValid$Project$filterPipelineWith$anyElementTrue_AValidDocuments_thenReturnNotProjectedDocument(){ 
		
		Document item1_1 = new Document("item_id", 43).append("quantity", 2).append("price", 10);
		Document item1_2 = new Document("item_id", 2).append("quantity", 1).append("price", 240);
		Document document1 = new Document("_id", 0).append("items", Arrays.asList(item1_1, item1_2));
		
		Document item2_1 = new Document("item_id", 23).append("quantity", 3).append("price", 110);
		Document item2_2 = new Document("item_id", 103).append("quantity", 4).append("price", 5);
		Document item2_3 = new Document("item_id", 38).append("quantity", 1).append("price", 300);
		Document document2 = new Document("_id", 1).append("items", Arrays.asList(item2_1, item2_2, item2_3));
		
		Document item3_1 = new Document("item_id", 4).append("quantity", 1).append("price", 23);
		Document document3 = new Document("_id", 2).append("items", Arrays.asList(item3_1));
		
		
		collection.insertMany(Arrays.asList(document1, document2, document3));

		Document under_filter = new Document();
		under_filter.append("input", "$items")
		            .append("as", "item")
		            .append("cond", new Document("$anyElementTrue", Arrays.asList(Arrays.asList(true, false))));
		Document filter = new Document("$filter", under_filter);
		Document items = new Document("items", filter);
		Document pipeline = new Document("$project", items);

		
		AggregateIterable<Document> iterable = collection.aggregate(Arrays.asList(pipeline));
		
		final Map<Integer, Document> result = new HashMap<>();
		
		iterable.forEach(new Block<Document>() {

			@Override
			public void apply(Document document) {
				result.put( (Integer) document.get("_id"), document);
				
			}
		});
				
		assertEquals(document1.toJson(), result.get(0).toJson());
		assertEquals(document2.toJson(), result.get(1).toJson());
		assertEquals(document3.toJson(), result.get(2).toJson());
	}
	
	
	

}
