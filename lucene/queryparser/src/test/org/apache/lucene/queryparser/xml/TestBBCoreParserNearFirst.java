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
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.xml.builders.NearFirstQueryBuilder;
import org.apache.lucene.queryparser.xml.builders.WildcardNearQueryBuilder;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldedQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.intervals.FieldedBooleanQuery;
import org.apache.lucene.search.intervals.UnorderedNearQuery;

import java.io.IOException;

public class TestBBCoreParserNearFirst extends TestCoreParser {

  private class CoreParserNearFirstQuery extends CoreParser {
    CoreParserNearFirstQuery(String defaultField, Analyzer analyzer) {
      super(defaultField, analyzer);

      // the query builder to be tested
      queryFactory.addBuilder("NearFirstQuery", new NearFirstQueryBuilder(queryFactory));
    }
  }

  @Override
  protected CoreParser newCoreParser(String defaultField, Analyzer analyzer) {
    final CoreParser coreParser = new CoreParserNearFirstQuery(defaultField, analyzer);

    // some additional builders to help
    coreParser.addQueryBuilder("WildcardNearQuery", new WildcardNearQueryBuilder(analyzer));

    return coreParser;
  }

  public void testNearFirstBooleanMustXml() throws IOException, ParserException {
    final Query q = parse("BBNearFirstBooleanMust.xml");
    dumpResults("testNearFirstBooleanMustXml", q, 50);
  }

  public void testNearFirstBooleanMust() throws IOException {
    BooleanQuery bq = new BooleanQuery();
    bq.add(new TermQuery(new Term("contents", "upholds")), BooleanClause.Occur.MUST);
    bq.add(new TermQuery(new Term("contents", "building")), BooleanClause.Occur.MUST);

    FieldedQuery[] subQueries = new FieldedQuery[2];
    subQueries[0] = FieldedBooleanQuery.toFieldedQuery(bq);
    subQueries[1] = FieldedBooleanQuery.toFieldedQuery(new TermQuery(new Term("contents", "bank")));
    FieldedQuery fq = new UnorderedNearQuery(7, subQueries);
    dumpResults("testNearFirstBooleanMust", fq, 5);
  }

}
