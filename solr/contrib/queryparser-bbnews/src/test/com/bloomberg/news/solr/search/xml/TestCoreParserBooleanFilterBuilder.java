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

package com.bloomberg.news.solr.search.xml;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queries.BooleanFilter;
import org.apache.lucene.queries.FilterClause;
import org.apache.lucene.queryparser.xml.CoreParser;
import org.apache.lucene.queryparser.xml.FilterBuilder;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.queryparser.xml.TestCoreParser;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.MatchAllDocsFilter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeFilter;
import org.junit.BeforeClass;
import org.w3c.dom.Element;

import java.io.IOException;


public class TestCoreParserBooleanFilterBuilder extends TestCoreParser {

  private static boolean useTermsFilter = false;
  private static String matchAllDocsFilterName = "MatchAllDocsFilter";

  @BeforeClass
  public static void beforeClass() throws Exception {
    useTermsFilter = random().nextBoolean();
    if (random().nextBoolean()) {
      matchAllDocsFilterName += random().nextInt(10);
    }
  }

  private static class CoreParserBooleanFilter extends CoreParser {
    CoreParserBooleanFilter(String defaultField, Analyzer analyzer, boolean useTermsFilter) {
      super(defaultField, analyzer);

      // the filter to be tested
      filterFactory.addBuilder("BooleanFilter", new BooleanFilterBuilder(
        defaultField, analyzer, null, filterFactory));

      // sometimes but not always together with TermsFilter(Builder)
      if (useTermsFilter) {
        // used by lucene/queryparser/src/test/org/apache/lucene/queryparser/xml/BooleanFilter.xml
        // org.apache and com.bloomberg.news BooleanFilterBuilder should both pass BooleanFilter.xml tests
        addFilterBuilder("TermsFilter", TermFreqBuildersWrapper.newTermsFilterBuilder(
          defaultField, analyzer, queryFactory));
      }
    }
  }

  protected CoreParser newCoreParser(String defaultField, Analyzer analyzer) {
    final CoreParser coreParser = new CoreParserBooleanFilter(defaultField, analyzer, useTermsFilter);

    // always with MatchAllDocsFilter(Builder) but its element name can vary
    coreParser.addFilterBuilder(matchAllDocsFilterName, new FilterBuilder() {
      @Override
      public Filter getFilter(Element e) throws ParserException {
        return new MatchAllDocsFilter();
      }
    });

    return coreParser;
  }

  public void testBooleanFilterXML() throws ParserException, IOException {
    assumeTrue("useTermsFilter", useTermsFilter);
    Query q = parse("BooleanFilter.xml");
    dumpResults("Boolean filter", q, 5);
  }

  public void testBooleanFilterwithMatchAllDocsFilter() throws ParserException, IOException {
    
    String text = "<BooleanFilter fieldName='content' disableCoord='true'>"
        + "<Clause occurs='should'><RangeFilter fieldName='date' lowerTerm='19870409' upperTerm='19870412'/></Clause>"
        + "<Clause occurs='should'><"+matchAllDocsFilterName+"/></Clause></BooleanFilter>";
    
    Filter f = parseFilterXML(text);
    assertTrue("Expecting a TermRangeFilter, but resulted in " + f.getClass(), f instanceof TermRangeFilter);
  
    text = "<BooleanFilter fieldName='content' disableCoord='true'>"
        + "<Clause occurs='must'><RangeFilter fieldName='date' lowerTerm='19870409' upperTerm='19870412'/></Clause>"
        + "<Clause occurs='should'><"+matchAllDocsFilterName+"/></Clause></BooleanFilter>";
    f = parseFilterXML(text);
    assertTrue("Expecting a TermRangeFilter, but resulted in " + f.getClass(), f instanceof TermRangeFilter);
    
    text = "<BooleanFilter fieldName='content' disableCoord='true'>"
        + "<Clause occurs='must'><RangeFilter fieldName='date' lowerTerm='19870409' upperTerm='19870410'/></Clause>"
        + "<Clause occurs='must'><RangeFilter fieldName='date' lowerTerm='19870410' upperTerm='19870411'/></Clause>"
        + "<Clause occurs='must'><RangeFilter fieldName='date' lowerTerm='19870411' upperTerm='19870412'/></Clause>"
        + "<Clause occurs='should'><"+matchAllDocsFilterName+"/></Clause></BooleanFilter>";
    f = parseFilterXML(text);
    assertTrue("Expecting a BooleanFilter, but resulted in " + f.getClass(), f instanceof BooleanFilter);
    BooleanFilter bf = (BooleanFilter)f;
    int size = bf.clauses().size();
    assertTrue("Expecting 3 clauses, but resulted in " + size, size == 3);
    for(FilterClause fc : bf.clauses())
    {
      assertFalse("Not expecting MatchAllDocsQuery ", fc.getFilter() instanceof MatchAllDocsFilter);
    }
    
    text = "<BooleanFilter fieldName='content' disableCoord='true'>"
        + "<Clause occurs='must'><"+matchAllDocsFilterName+"/></Clause>"
        + "<Clause occurs='should'><"+matchAllDocsFilterName+"/></Clause></BooleanFilter>";
    f = parseFilterXML(text);
    assertTrue("Expecting a MatchAllDocsFilter, but resulted in " + f.getClass(), f instanceof MatchAllDocsFilter);
    
    text = "<BooleanFilter fieldName='content' disableCoord='true'>"
        + "<Clause occurs='must'><"+matchAllDocsFilterName+"/></Clause>"
        + "<Clause occurs='mustnot'><RangeFilter fieldName='date' lowerTerm='19871201' upperTerm='19871231'/></Clause></BooleanFilter>";
    f = parseFilterXML(text);
    assertTrue("Expecting a BooleanFilter, but resulted in " + f.getClass(), f instanceof BooleanFilter);
    bf = (BooleanFilter)f;
    size = bf.clauses().size();
    assertTrue("Expecting 2 clauses, but resulted in " + size, size == 2);
    boolean bMatchAllDocsFound = false;
    for(FilterClause fc : bf.clauses())
    {
      bMatchAllDocsFound |= fc.getFilter() instanceof MatchAllDocsFilter;
    }
    assertTrue("Expecting MatchAllDocsFilter ", bMatchAllDocsFound);
    
  }
}
