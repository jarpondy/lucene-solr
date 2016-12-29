package com.bloomberg.news.solr.search.xml;

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
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.xml.CoreParser;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.queryparser.xml.TestCoreParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldedQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.intervals.OrderedNearQuery;
import org.apache.lucene.search.intervals.FieldedBooleanQuery;
import org.apache.lucene.search.intervals.UnorderedNearQuery;

import java.io.IOException;
import java.io.InputStream;

public class TestCoreParserNear extends TestCoreParser {

  private class CoreParserNearQuery extends CoreParser {
    CoreParserNearQuery(String defaultField, Analyzer analyzer) {
      super(defaultField, analyzer);

      // the query builder to be tested
      queryFactory.addBuilder("NearQuery", new NearQueryBuilder(
        defaultField, analyzer, null, queryFactory));

      // some additional builders to help
      // (here only since requiring access to queryFactory)
      queryFactory.addBuilder("WildcardNearQuery", new WildcardNearQueryBuilder(
        defaultField, analyzer, null, queryFactory));
    }
  }

  @Override
  protected CoreParser newCoreParser(String defaultField, Analyzer analyzer) {
    final CoreParser coreParser = new CoreParserNearQuery(defaultField, analyzer);

    return coreParser;
  }

  @Override
  protected Query parse(String xmlFileName) throws ParserException, IOException {
    try (InputStream xmlStream = TestCoreParserNear.class.getResourceAsStream(xmlFileName)) {
      if (xmlStream == null) {
        return super.parse(xmlFileName);
      }
      Query result = coreParser().parse(xmlStream);
      return result;
    }
  }

  public void testNearBooleanNear() throws IOException, ParserException {
    final Query q = parse("NearBooleanNear.xml");
    dumpResults("testNearBooleanNear", q, 5);
  }

  //working version of (A OR B) N/5 C
  public void testNearBoolean() throws IOException {
    BooleanQuery bq = new BooleanQuery();
    bq.add(new TermQuery(new Term("contents", "iranian")), BooleanClause.Occur.SHOULD);
    bq.add(new TermQuery(new Term("contents", "north")), BooleanClause.Occur.SHOULD);

    FieldedQuery[] subQueries = new FieldedQuery[2];
    subQueries[0] = FieldedBooleanQuery.toFieldedQuery(bq);
    subQueries[1] = FieldedBooleanQuery.toFieldedQuery(new TermQuery(new Term("contents", "akbar")));
    FieldedQuery fq = new UnorderedNearQuery(5, subQueries);
    dumpResults("testNearBoolean", fq, 5);
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

}
