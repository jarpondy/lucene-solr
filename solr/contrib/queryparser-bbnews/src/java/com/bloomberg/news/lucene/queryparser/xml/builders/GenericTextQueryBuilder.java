package com.bloomberg.news.lucene.queryparser.xml.builders;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.BoostedQuery;
import org.apache.lucene.queries.function.valuesource.ConstValueSource;
import org.apache.lucene.queryparser.xml.DOMUtils;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.queryparser.xml.QueryBuilder;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.w3c.dom.Element;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Builds a MatchAllDocsQuery if the terms found are zero.
 * Builds a TermQuery If there is only one resulting term after analyzer being applied
 * Builds a PhraseQuery if there are multiple terms.
 */
public class GenericTextQueryBuilder implements QueryBuilder {

  protected Analyzer analyzer;

  private static final char WILDCARD_STRING = '*';
  private static final char WILDCARD_CHAR = '?';
  private static final char WILDCARD_ESCAPE = '\\';

  public GenericTextQueryBuilder(Analyzer analyzer) {
    this.analyzer = analyzer;
  }


  @Override
  public Query getQuery(Element e) throws ParserException {
      String field = DOMUtils.getAttributeWithInheritanceOrFail(e,
              "fieldName");
      String text = DOMUtils.getText(e);
      boolean ignoreWildcard = DOMUtils.getAttribute(e, "ignoreWC", false);

      if (!ignoreWildcard && containsWildcard(text))
      {
        WildcardNearQueryParser p = new WildcardNearQueryParser(field, analyzer);
        Query q = p.parse(text);

        float boost = DOMUtils.getAttribute(e, "boost", 1.0f);
        if (boost != 1.0f) {
          q = new BoostedQuery(q, new ConstValueSource(boost));
        }

        return q;
      }

      PhraseQuery.Builder pq = null; //this will be instantiated only if the query results in multiple terms
      Term firstTerm = null;//Keeps the first Term in the query and if there are more terms found then this will be consumed by above PhraseQuery
      int firstPosition = 0;

      TokenStream source = null;
      try {
          source = analyzer.tokenStream(field, text);
          source.reset();

          TermToBytesRefAttribute termAtt = null;
          if (source.hasAttribute(TermToBytesRefAttribute.class)) {
              termAtt = source.getAttribute(TermToBytesRefAttribute.class);
          }
          else throw new ParserException("Cannot build Text query, "
              + "token stream has no TermToBytesRefAttribute. field:" + field
              + ", phrase:" + text);

          PositionIncrementAttribute posIncrAtt = null;
          if (source.hasAttribute(PositionIncrementAttribute.class)) {
              posIncrAtt = source.getAttribute(PositionIncrementAttribute.class);
          }

          int position = -1;
          while (source.incrementToken()) {
              Term t = new Term(field, BytesRef.deepCopyOf(termAtt.getBytesRef()));

              int positionIncrement = (posIncrAtt != null) ? posIncrAtt.getPositionIncrement() : 1;
              position += positionIncrement;

              if (null == firstTerm) {
                firstTerm = t;
                firstPosition = position;
                continue;
              }

              if (pq == null) {
                pq = new PhraseQuery.Builder();
                pq.add(firstTerm, firstPosition);
              }

              pq.add(t, position);
            }

          source.end();
      } catch (IOException ioe) {
          ParserException p = new ParserException(
                  "Cannot build generic text query from xml input. field:" + field
                          + ", text:" + text);
          p.initCause(ioe);
          throw p;
      } finally {
          IOUtils.closeWhileHandlingException(source);
      }

      if (firstTerm == null) {
        return new MatchAllDocsQuery();
      } else if (pq == null) {
          Query q = new TermQuery(firstTerm);
          float boost = DOMUtils.getAttribute(e, "boost", 1.0f);
          if (boost != 1f) {
            q = new BoostQuery(q, boost);
          }
          return q;
      } else {
        //TODO pq.setSlop(phraseSlop);
        Query q = pq.build();
        float boost = DOMUtils.getAttribute(e, "boost", 1.0f);
        if (boost != 1f) {
          return new BoostQuery(q, boost);
        }
        return q;
      }
  }

  private static boolean containsWildcard(String text) {
    for (int i = 0; i < text.length();) {
       final int c = text.codePointAt(i);
       int length = Character.charCount(c);
       switch(c) {
         case WILDCARD_STRING:
         case WILDCARD_CHAR:
           return true;
         case WILDCARD_ESCAPE:
           // skip over the escaped codepoint if it exists
           if (i + length < text.length()) {
             final int nextChar = text.codePointAt(i + length);
             length += Character.charCount(nextChar);
           }
         break;
      }
      i += length;
    }
    return false;
  }
}