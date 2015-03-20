package org.apache.lucene.queryparser.xml;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.xml.builders.KeywordNearQueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class TestParser extends LuceneTestCase {

  private static CoreParser builder;
  private static Directory dir;
  private static IndexReader reader;
  private static IndexSearcher searcher;
  private static String ANALYSER_PARAM     = "tests.TestParser.analyser";
  private static String DEFAULT_ANALYSER   = "mock";
  private static String STANDARD_ANALYSER  = "standard";
 
  @BeforeClass
  public static void beforeClass() throws Exception {
    String analyserToUse = System.getProperty(ANALYSER_PARAM, DEFAULT_ANALYSER);
    Analyzer analyzer =  null;
    if (analyserToUse.equals(STANDARD_ANALYSER))
    {
      analyzer = new StandardAnalyzer(); 
    }
    else
    {
      assertEquals(DEFAULT_ANALYSER, analyserToUse);
      // TODO: rewrite test (this needs to set QueryParser.enablePositionIncrements, too, for work with CURRENT):
      analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true, MockTokenFilter.ENGLISH_STOPSET);
    }
    builder = new CorePlusExtensionsParser("contents", analyzer);

    BufferedReader d = new BufferedReader(new InputStreamReader(
        TestParser.class.getResourceAsStream("reuters21578.txt"), StandardCharsets.US_ASCII));
    dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    String line = d.readLine();
    while (line != null) {
      int endOfDate = line.indexOf('\t');
      String date = line.substring(0, endOfDate).trim();
      String content = line.substring(endOfDate).trim();
      Document doc = new Document();
      doc.add(newTextField("date", date, Field.Store.YES));
      doc.add(newTextField("contents", content, Field.Store.YES));
      doc.add(new IntField("date2", Integer.valueOf(date), Field.Store.NO));
      writer.addDocument(doc);
      line = d.readLine();
    }
    d.close();
    writer.close();
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);

  }

  @AfterClass
  public static void afterClass() throws Exception {
    reader.close();
    dir.close();
    reader = null;
    searcher = null;
    dir = null;
    builder = null;
  }

  public void testTermQueryXML() throws ParserException, IOException {
    Query q = parse("TermQuery.xml");
    dumpResults("TermQuery", q, 5);
  }
  
  public void testTermQueryEmptyXML() throws ParserException, IOException {
    parse("TermQueryEmpty.xml", true/*shouldFail*/);
  }
  
  public void testTermQueryStopwordXML() throws ParserException, IOException {
    parse("TermQueryStopwords.xml", true/*shouldFail*/);
  }
  
  public void testTermQueryMultipleTermsXML() throws ParserException, IOException {
    parse("TermQueryMultipleTerms.xml", true/*shouldFail*/);
  }

  public void testSimpleTermsQueryXML() throws ParserException, IOException {
    Query q = parse("TermsQuery.xml");
    assertTrue("Expecting a BooleanQuery, but resulted in " + q.getClass(), q instanceof BooleanQuery);
    dumpResults("TermsQuery", q, 5);
  }

  public void testSimpleTermsQueryWithTermElementXML() throws ParserException, IOException {
    Query q = parse("TermsQueryWithTermElement.xml");
    dumpResults("TermsQuery", q, 5);
  }
  
  public void testTermsQueryWithSingleTerm() throws ParserException, IOException {
    Query q = parse("TermsQuerySingleTerm.xml");
    assertTrue("Expecting a TermQuery, but resulted in " + q.getClass(), q instanceof TermQuery);
    dumpResults("TermsQueryWithSingleTerm", q, 5);
  }
  
  
  //term appears like single term but results in two terms when it runs through standard analyzer
  public void testTermsQueryWithStopwords() throws ParserException, IOException {
    Query q = parse("TermsQueryStopwords.xml");
    if (builder.analyzer instanceof StandardAnalyzer)
      assertTrue("Expecting a BooleanQuery, but resulted in " + q.getClass(), q instanceof BooleanQuery);
    dumpResults("TermsQueryWithStopwords", q, 5);
    }
  
  public void testTermsQueryEmpty() throws ParserException, IOException {
    Query q = parse("TermsQueryEmpty.xml");
    assertTrue("Expecting a MatchAllDocsQuery, but resulted in " + q.getClass(), q instanceof MatchAllDocsQuery);
    dumpResults("Empty TermsQuery", q, 5);
  }
  
  public void testTermsQueryWithOnlyStopwords() throws ParserException, IOException {
    Query q = parse("TermsQueryOnlyStopwords.xml");
    if (builder.analyzer instanceof StandardAnalyzer)
      assertTrue("Expecting a MatchAllDocsQuery, but resulted in " + q.getClass(), q instanceof MatchAllDocsQuery);
    dumpResults("TermsQuery with only stopwords", q, 5);
  }
  

  public void testBooleanQueryXML() throws ParserException, IOException {
    Query q = parse("BooleanQuery.xml");
    dumpResults("BooleanQuery", q, 5);
  }

  public void testDisjunctionMaxQueryXML() throws ParserException, IOException {
    Query q = parse("DisjunctionMaxQuery.xml");
    assertTrue(q instanceof DisjunctionMaxQuery);
    DisjunctionMaxQuery d = (DisjunctionMaxQuery)q;
    assertEquals(0.0f, d.getTieBreakerMultiplier(), 0.0001f);
    assertEquals(2, d.getDisjuncts().size());
    DisjunctionMaxQuery ndq = (DisjunctionMaxQuery) d.getDisjuncts().get(1);
    assertEquals(1.2f, ndq.getTieBreakerMultiplier(), 0.0001f);
    assertEquals(1, ndq.getDisjuncts().size());
  }

  public void testRangeFilterQueryXML() throws ParserException, IOException {
    Query q = parse("RangeFilterQuery.xml");
    dumpResults("RangeFilter", q, 5);
  }

  public void testUserQueryXML() throws ParserException, IOException {
    Query q = parse("UserInputQuery.xml");
    dumpResults("UserInput with Filter", q, 5);
  }

  public void testCustomFieldUserQueryXML() throws ParserException, IOException {
    Query q = parse("UserInputQueryCustomField.xml");
    int h = searcher.search(q, null, 1000).totalHits;
    assertEquals("UserInputQueryCustomField should produce 0 result ", 0, h);
  }

  public void testLikeThisQueryXML() throws Exception {
    Query q = parse("LikeThisQuery.xml");
    dumpResults("like this", q, 5);
  }

  public void testBoostingQueryXML() throws Exception {
    Query q = parse("BoostingQuery.xml");
    dumpResults("boosting ", q, 5);
  }

  public void testFuzzyLikeThisQueryXML() throws Exception {
    Query q = parse("FuzzyLikeThisQuery.xml");
    //show rewritten fuzzyLikeThisQuery - see what is being matched on
    if (VERBOSE) {
      System.out.println(q.rewrite(reader));
    }
    dumpResults("FuzzyLikeThis", q, 5);
  }

  public void testTermsFilterXML() throws Exception {
    Query q = parse("TermsFilterQuery.xml");
    dumpResults("Terms Filter", q, 5);
  }
  
  public void testTermsFilterWithSingleTerm() throws Exception {
    Query q = parse("TermsFilterQueryWithSingleTerm.xml");
    dumpResults("TermsFilter With SingleTerm", q, 5);
  }
  
  public void testTermsFilterQueryWithStopword() throws Exception {
    Query q = parse("TermsFilterQueryStopwords.xml");
    dumpResults("TermsFilter with Stopword", q, 5);
  }
  
  public void testTermsFilterQueryWithOnlyStopword() throws Exception {
    Query q = parse("TermsFilterOnlyStopwords.xml");
    dumpResults("TermsFilter with all stop words", q, 5);
  }
  
  public void testBoostingTermQueryXML() throws Exception {
    Query q = parse("BoostingTermQuery.xml");
    dumpResults("BoostingTermQuery", q, 5);
  }

  public void testSpanTermXML() throws Exception {
    Query q = parse("SpanQuery.xml");
    dumpResults("Span Query", q, 5);
  }

  public void testConstantScoreQueryXML() throws Exception {
    Query q = parse("ConstantScoreQuery.xml");
    dumpResults("ConstantScoreQuery", q, 5);
  }
  
  public void testPhraseQueryXML() throws Exception {
    Query q = parse("PhraseQuery.xml");
    assertTrue("Expecting a PhraseQuery, but resulted in " + q.getClass(), q instanceof PhraseQuery);
    dumpResults("PhraseQuery", q, 5);
  }
  
  public void testPhraseQueryXMLWithStopwordsXML() throws Exception {
    if (builder.analyzer instanceof StandardAnalyzer) {
      parse("PhraseQueryStopwords.xml", true/*shouldfail*/);
    }
  }
  
  public void testPhraseQueryXMLWithNoTextXML() throws Exception {
    parse("PhraseQueryEmpty.xml", true/*shouldFail*/);
  }

  public void testGenericTextQueryXML() throws Exception {
    Query q = parse("GenericTextQuery.xml");
    assertTrue("Expecting a PhraseQuery, but resulted in " + q.getClass(), q instanceof PhraseQuery);
    dumpResults("GenericTextQuery", q, 5);
  }
  
  public void testGenericTextQuerySingleTermXML() throws Exception {
    Query q = parse("GenericTextQuerySingleTerm.xml");
    assertTrue("Expecting a TermQuery, but resulted in " + q.getClass(), q instanceof TermQuery);
    dumpResults("GenericTextQuery", q, 5);
  }
  
  public void testGenericTextQueryWithStopwordsXML() throws Exception {
    Query q = parse("GenericTextQueryStopwords.xml");
    assertTrue("Expecting a PhraseQuery, but resulted in " + q.getClass(), q instanceof PhraseQuery);
    dumpResults("GenericTextQuery with stopwords", q, 5);
  }
  
  public void testGenericTextQueryWithAllStopwordsXML() throws Exception {
    Query q = parse("GenericTextQueryAllStopwords.xml");
    if (builder.analyzer instanceof StandardAnalyzer)
      assertTrue("Expecting a MatchAllDocsQuery, but resulted in " + q.getClass(), q instanceof MatchAllDocsQuery);
    dumpResults("GenericTextQuery with just stopwords", q, 5);
  }
  
  public void testGenericTextQueryWithNoTextXML() throws Exception {
    Query q = parse("GenericTextQueryEmpty.xml");
    assertTrue("Expecting a MatchAllDocsQuery, but resulted in " + q.getClass(), q instanceof MatchAllDocsQuery);
    dumpResults("GenericTextQuery with no text", q, 5);
  }
  
  public void testGenericTextQueryPhraseWildcardXML() throws Exception {
    Query q = parse("GenericTextQueryPhraseWildcard.xml");
    dumpResults("GenericTextQuery with a phrase wildcard", q, 5);
  }
  
  public void testGenericTextQueryTrailingWildcardXML() throws Exception {
    Query q = parse("GenericTextQueryTrailingWildcard.xml");
    dumpResults("GenericTextQuery with a trailing wildcard", q, 5);
  }

  public void testGenericTextQueryMultiWildcardXML() throws Exception {
    Query q = parse("GenericTextQueryMultiWildcard.xml");
    dumpResults("GenericTextQuery with multiple terms containing wildcards", q, 5);
  }
  
  public void testGenericTextQueryPhraseWildcard2XML() throws Exception {
    Query q = parse("GenericTextQueryPhraseWildcard2.xml");
    dumpResults("GenericTextQuery with a phrase wildcard", q, 5);
  }
  
  public void testGenericTextQueryTrailingWildcard2XML() throws Exception {
    Query q = parse("GenericTextQueryTrailingWildcard2.xml");
    dumpResults("GenericTextQuery with a trailing wildcard", q, 5);
  }

  public void testGenericTextQueryMultiWildcard2XML() throws Exception {
    Query q = parse("GenericTextQueryMultiWildcard2.xml");
    dumpResults("GenericTextQuery with multiple terms containing wildcards", q, 5);
  }

  public void testGenericTextQueryMultiClauseXML() throws Exception {
    Query q = parse("GenericTextQueryMultiClause.xml");
    dumpResults("GenericTextQuery. BooleanQuery containing multiple GenericTextQuery clauses with different boost factors", q, 5);
  }

  public void testComplexPhraseQueryXML() throws Exception {
    Query q = parse("ComplexPhraseQuery.xml");
    dumpResults("ComplexPhraseQuery", q, 5);
  }
  
  public void testComplexPhraseQueryPrefixQueryXML() throws Exception {
    Query q = parse("ComplexPhraseQueryPrefixQuery.xml");
    dumpResults("ComplexPhraseQuery with a single prefix query term", q, 5);
  }
  
  public void testComplexPhraseNearQueryXML() throws Exception {
    Query q = parse("ComplexPhraseNearQuery.xml");
    dumpResults("ComplexPhraseNearQuery", q, 5);
  }
  
  /* test cases for keyword near query */
  public void testKWNearQuery() throws Exception {
    Query q = parse("KeywordNear.xml");
    dumpResults("KeywordNear query", q, 5);
  }
  
  public void testKWNearQueryWildcard() throws Exception {
    Query q = parse("KeywordNearWildcard.xml");
    dumpResults("KeywordNear with wildcard terms", q, 5);
  }
  
  public void testKWNearQuerySpecialChar() throws Exception {
    Query q = parse("KeywordNearSpecialChars.xml");
    dumpResults("KeywordNear with special characters", q, 5);
  }
  
  public void testKWNearQuerywithStopwords() throws Exception {
    Query q = parse("KeywordNearStopwords.xml");
    dumpResults("KeywordNear with stopwords", q, 5);
  }
  
  public void testKWNearViaGenericTextQuery() throws Exception {
    Query q = parse("KeywordNearThroughGenericTextQuery.xml");
    dumpResults("GenericTextQuery with multiple terms containing wildcards", q, 5);
  }
  
  public void testKWNearQuerywithEmptytokens() throws Exception {
    Query q = parse("KeywordNearEmptyQuery.xml");
    dumpResults("Keyword Near with empty tokens", q, 5);
  }
  
  //TODO: move this test along with the KeywordNearQueryParser to an appropriate parser names space
  public void testKeywordNearQueryParser() throws Exception {
    
    KeywordNearQueryParser p = new KeywordNearQueryParser("contents", builder.analyzer);
    Query q = p.parse("to");
    dumpResults("KeywordNearQueryParser stop word", q, 5);
    q = p.parse("");
    assertTrue("Expecting a MatchAllDocsQuery, but resulted in " + q.getClass(), q instanceof MatchAllDocsQuery);
    dumpResults("KeywordNearQueryParser empty query", q, 5);
    q = p.parse("<TRUMP PLAZA>");
    dumpResults("KeywordNearQueryParser special char1", q, 5);
    q = p.parse("7/8");
    dumpResults("KeywordNearQueryParser special char2", q, 5);
    q = p.parse("ACQUIR* BY ZENEX <Zenex Oil Pty Ltd> said it acquired the interests of E?so S*h Africa, the local subsidiary");
    dumpResults("KeywordNearQueryParser wildcard", q, 5);
  }
  
  /* end of keyword near query test cases*/

  public void testMatchAllDocsPlusFilterXML() throws ParserException, IOException {
    Query q = parse("MatchAllDocsQuery.xml");
    dumpResults("MatchAllDocsQuery with range filter", q, 5);
  }

  public void testBooleanFilterXML() throws ParserException, IOException {
    Query q = parse("BooleanFilter.xml");
    dumpResults("Boolean filter", q, 5);
  }

  public void testNestedBooleanQuery() throws ParserException, IOException {
    Query q = parse("NestedBooleanQuery.xml");
    dumpResults("Nested Boolean query", q, 5);
  }

  public void testCachedFilterXML() throws ParserException, IOException {
    Query q = parse("CachedFilter.xml");
    dumpResults("Cached filter", q, 5);
  }

  public void testDuplicateFilterQueryXML() throws ParserException, IOException {
    List<LeafReaderContext> leaves = searcher.getTopReaderContext().leaves();
    Assume.assumeTrue(leaves.size() == 1);
    Query q = parse("DuplicateFilterQuery.xml");
    int h = searcher.search(q, null, 1000).totalHits;
    assertEquals("DuplicateFilterQuery should produce 1 result ", 1, h);
  }

  public void testNumericRangeFilterQueryXML() throws ParserException, IOException {
    Query q = parse("NumericRangeFilterQuery.xml");
    dumpResults("NumericRangeFilter", q, 5);
  }

  public void testNumericRangeQueryQueryXML() throws ParserException, IOException {
    Query q = parse("NumericRangeQueryQuery.xml");
    dumpResults("NumericRangeQuery", q, 5);
  }
  
  public void testNearFirstXML() throws ParserException, IOException {
    Query q = parse("NearFirst.xml");
    dumpResults("Near First (Interval)", q, 5);
  }
  
  public void testNearNotFirstXML() throws ParserException, IOException {
    Query q = parse("NearNotFirst.xml");
    dumpResults("Near Not First (Interval)", q, 5);
  }
  
  public void testNearNearXML() throws ParserException, IOException {
    Query q = parse("NearNear.xml");
    dumpResults("Near Near (Interval)", q, 5);
  }
  
  public void testNearOrXML() throws ParserException, IOException {
    Query q = parse("NearOr.xml");
    dumpResults("Near Or (Interval)", q, 5);
  }
  
  public void testNearPhraseXML() throws ParserException, IOException {
    Query q = parse("NearPhrase.xml");
    dumpResults("Near Phrase (Interval)", q, 5);
  }
  
  public void testNearWildcardXML() throws ParserException, IOException {
    Query q = parse("NearWildcard.xml");
    dumpResults("NearWildcard (Interval)", q, 5);
  }
  
  public void testNearTermQuery() throws ParserException, IOException {
    int slop = 1;
    FieldedQuery[] subqueries = new FieldedQuery[2];
    subqueries[0] = new TermQuery(new Term("contents", "keihanshin"));
    subqueries[1] = new TermQuery(new Term("contents", "real"));
    Query q = new OrderedNearQuery(slop, true, subqueries);
    dumpResults("NearPrefixQuery", q, 5);
  }
  
  public void testPrefixedNearQuery() throws ParserException, IOException {
    int slop = 1;
    FieldedQuery[] subqueries = new FieldedQuery[2];
    subqueries[0] = new PrefixQuery(new Term("contents", "keihanshi"));
    ((MultiTermQuery)subqueries[0]).setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE);
    subqueries[1] = new PrefixQuery(new Term("contents", "rea"));
    ((MultiTermQuery)subqueries[1]).setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE);
    Query q = new OrderedNearQuery(slop, true, subqueries);
    dumpResults("NearPrefixQuery", q, 5);
  }

  //================= Helper methods ===================================

  private Query parse(String xmlFileName) throws ParserException, IOException {
    return parse(xmlFileName, false);
  }
  private Query parse(String xmlFileName, Boolean shouldFail) throws ParserException, IOException {
    InputStream xmlStream = TestParser.class.getResourceAsStream(xmlFileName);
    assertTrue("Test XML file " + xmlFileName + " cannot be found", xmlStream != null);
    Query result = null;
    try {
      result = builder.parse(xmlStream);
    } catch (ParserException ex) {
      assertTrue("Parser exception " + ex, shouldFail);
    }
    if (shouldFail && result != null)
      assertTrue("Expected to fail. But resulted in query: " + result.getClass() + " with value: " + result, false);
    xmlStream.close();
    return result;
  }

  private void dumpResults(String qType, Query q, int numDocs) throws IOException {
    if (VERBOSE) {
      System.out.println("=======TEST: " + q.getClass() + " query=" + q);
    }
    TopDocs hits = searcher.search(q, null, numDocs);
    assertTrue(qType + " " + q + " should produce results ", hits.totalHits > 0);
    if (VERBOSE) {
      System.out.println("=========" + qType + " class=" + q.getClass() + " query=" + q + "============");
      ScoreDoc[] scoreDocs = hits.scoreDocs;
      for (int i = 0; i < Math.min(numDocs, hits.totalHits); i++) {
        Document ldoc = searcher.doc(scoreDocs[i].doc);
        System.out.println("[" + ldoc.get("date") + "]" + ldoc.get("contents"));
      }
      System.out.println();
    }
  }
}
