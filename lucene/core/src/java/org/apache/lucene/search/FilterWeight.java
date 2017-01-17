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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.Set;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Weight.PostingFeatures;
import org.apache.lucene.util.Bits;

/**
 * A {@code FilterWeight} contains another {@code Weight} and implements
 * all abstract methods by calling the contained weight's method.
 *
 * Note that {@code FilterWeight} does not override the non-abstract
 * {@link Weight#bulkScorer(AtomicReaderContext, boolean, PostingFeatures, org.apache.lucene.util.Bits)} method and subclasses of
 * {@code FilterWeight} must provide their bulkScorer implementation
 * if required.
 *
 * @lucene.internal
 */
public abstract class FilterWeight extends Weight {

  final protected Weight in;

  /**
   * Default constructor.
   */
  protected FilterWeight(Weight weight) {
    this(weight.getQuery(), weight);
  }

  /**
   * Alternative constructor.
   * Use this variant only if the <code>weight</code> was not obtained
   * via the {@link Query#createWeight(IndexSearcher)}
   * method of the <code>query</code> object.
   */
  protected FilterWeight(Query query, Weight weight) {
    super();
    this.in = weight;
  }


  @Override
  public Explanation explain(AtomicReaderContext context, int doc) throws IOException {
    return in.explain(context, doc);
  }
  
  @Override
  public Query getQuery() {
    return in.getQuery();
  }

  @Override
  public float getValueForNormalization() throws IOException {
    return in.getValueForNormalization();
  }

  @Override
  public void normalize(float norm, float topLevelBoost) {
    in.normalize(norm, topLevelBoost);
  }
  
  @Override
  public Scorer scorer(AtomicReaderContext context, PostingFeatures flags, Bits acceptDocs) throws IOException {
    return in.scorer(context, flags, acceptDocs);
  }
  
  @Override
  Scorer scorer(AtomicReaderContext context, boolean scoreDocsInOrder, boolean topScorer, Bits acceptDocs) {
    throw new UnsupportedOperationException("Not supported yet.");
  }
  
  @Override
  public boolean scoresDocsOutOfOrder() { return false; }
}