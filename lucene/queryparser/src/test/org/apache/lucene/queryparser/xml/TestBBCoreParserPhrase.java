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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.xml.builders.PhraseQueryBuilder;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.PhraseQuery;

public class TestBBCoreParserPhrase extends TestCoreParser {

  private class CoreParserPhraseQuery extends CoreParser {
    CoreParserPhraseQuery(String defaultField, Analyzer analyzer) {
      super(defaultField, analyzer);

      // the query builder to be tested
      queryFactory.addBuilder("PhraseQuery", new PhraseQueryBuilder(analyzer));
    }
  }

  @Override
  protected CoreParser newCoreParser(String defaultField, Analyzer analyzer) {
    return new CoreParserPhraseQuery(defaultField, analyzer);
  }

  public void testPhraseQueryXML() throws Exception {
    Query q = parse("BBPhraseQuery.xml");
    assertTrue("Expecting a PhraseQuery, but resulted in " + q.getClass(), q instanceof PhraseQuery);
    dumpResults("PhraseQuery", q, 5);
  }

  public void testPhraseQueryXMLWithStopwordsXML() throws Exception {
    if (analyzer() instanceof StandardAnalyzer) {
      parseShouldFail("BBPhraseQueryStopwords.xml",
          "Empty phrase query generated for field:contents, phrase:and to a");
    }
  }

  public void testPhraseQueryXMLWithNoTextXML() throws Exception {
    parseShouldFail("BBPhraseQueryEmpty.xml",
        "PhraseQuery has no text");
  }

}
