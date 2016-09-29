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
package org.apache.solr.ltr.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.solr.ltr.feature.FeatureException;
import org.apache.solr.ltr.norm.IdentityNormalizer;
import org.apache.solr.ltr.norm.Normalizer;
import org.apache.solr.ltr.ranking.Feature;

/**
 * Contains all the data needed for loading a model.
 */

public abstract class LTRScoringModel {

  protected final String name;
  private final String featureStoreName;
  protected final List<Feature> features;
  private final List<Feature> allFeatures;
  private final Map<String,Object> params;
  private final List<Normalizer> norms;

  public LTRScoringModel(String name, List<Feature> features,
      List<Normalizer> norms,
      String featureStoreName, List<Feature> allFeatures,
      Map<String,Object> params) {
    this.name = name;
    this.features = features;
    this.featureStoreName = featureStoreName;
    this.allFeatures = allFeatures;
    this.params = params;
    this.norms = norms;
  }

  /**
   * Validate that settings make sense and throws
   * {@link ModelException} if they do not make sense.
   */
  public void validate() throws ModelException {
  }

  /**
   * @return the norms
   */
  public List<Normalizer> getNorms() {
    return Collections.unmodifiableList(norms);
  }
  
  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @return the features
   */
  public List<Feature> getFeatures() {
    return Collections.unmodifiableList(features);
  }

  public int numFeatures() {
    return features.size();
  }

  public Map<String,Object> getParams() {
    return params;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = (prime * result) + ((features == null) ? 0 : features.hashCode());
    result = (prime * result) + ((name == null) ? 0 : name.hashCode());
    result = (prime * result) + ((params == null) ? 0 : params.hashCode());
    result = (prime * result) + ((norms == null) ? 0 : norms.hashCode());
    result = (prime * result) + ((featureStoreName == null) ? 0 : featureStoreName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final LTRScoringModel other = (LTRScoringModel) obj;
    if (features == null) {
      if (other.features != null) {
        return false;
      }
    } else if (!features.equals(other.features)) {
      return false;
    }
    if (norms == null) {
      if (other.norms != null) {
        return false;
      }
    } else if (!norms.equals(other.norms)) {
      return false;
    }
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    if (params == null) {
      if (other.params != null) {
        return false;
      }
    } else if (!params.equals(other.params)) {
      return false;
    }
    if (featureStoreName == null) {
      if (other.featureStoreName != null) {
        return false;
      }
    } else if (!featureStoreName.equals(other.featureStoreName)) {
      return false;
    }


    return true;
  }

  public boolean hasParams() {
    return !((params == null) || params.isEmpty());
  }

  public Collection<Feature> getAllFeatures() {
    return allFeatures;
  }

  public String getFeatureStoreName() {
    return featureStoreName;
  }
  
  /**
   * Given a list of normalized values for all features a scoring algorithm
   * cares about, calculate and return a score.
   *
   * @param modelFeatureValuesNormalized
   *          List of normalized feature values. Each feature is identified by
   *          its id, which is the index in the array
   * @return The final score for a document
   */
  public abstract float score(float[] modelFeatureValuesNormalized);

  /**
   * Similar to the score() function, except it returns an explanation of how
   * the features were used to calculate the score.
   *
   * @param context
   *          Context the document is in
   * @param doc
   *          Document to explain
   * @param finalScore
   *          Original score
   * @param featureExplanations
   *          Explanations for each feature calculation
   * @return Explanation for the scoring of a document
   */
  public abstract Explanation explain(LeafReaderContext context, int doc,
      float finalScore, List<Explanation> featureExplanations);

  @Override
  public String toString() {
    return  getClass().getSimpleName() + "(name="+getName()+")";
  }
  
  /**
   * Goes through all the stored feature values, and calculates the normalized
   * values for all the features that will be used for scoring.
   */
  public void normalizeFeaturesInPlace(float[] modelFeatureValues) {
    float[] modelFeatureValuesNormalized = modelFeatureValues;
    if (modelFeatureValues.length != norms.size()) {
      throw new FeatureException("Must have normalizer for every feature");
    }
    for(int idx = 0; idx < modelFeatureValuesNormalized.length; ++idx) {
      modelFeatureValuesNormalized[idx] = 
          norms.get(idx).normalize(modelFeatureValuesNormalized[idx]);
    }
  }
  
  public Explanation getNormalizerExplanation(Explanation e, int idx) {
    Normalizer n = norms.get(idx);
    if (n != IdentityNormalizer.INSTANCE) {
      return n.explain(e);
    }
    return e;
  } 

}