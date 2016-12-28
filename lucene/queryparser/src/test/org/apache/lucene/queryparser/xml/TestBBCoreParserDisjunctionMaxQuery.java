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

import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

public class TestBBCoreParserDisjunctionMaxQuery extends TestBBCoreParser {

  public void testDisjunctionMaxQueryTripleWildcardNearQuery() throws Exception {
    Query q = parse("BBDisjunctionMaxQueryTripleWildcardNearQuery.xml");
    int size = ((DisjunctionMaxQuery)q).getDisjuncts().size();
    assertTrue("Expecting 2 clauses, but resulted in " + size, size == 2);
    DisjunctionMaxQuery dm = (DisjunctionMaxQuery)q;
    for(Query q1 : dm.getDisjuncts())
    {
      assertFalse("Not expecting MatchAllDocsQuery ",q1 instanceof MatchAllDocsQuery);
    }
  }

  public void testDisjunctionMaxQueryMatchAllDocsQuery() throws Exception {
    final Query q = parse("BBDisjunctionMaxQueryMatchAllDocsQuery.xml");
    assertTrue("Expecting a MatchAllDocsQuery, but resulted in " + q.getClass(), q instanceof MatchAllDocsQuery);
  }

}
