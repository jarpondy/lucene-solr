package ltr.model.impl;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import ltr.feature.FeatureStore;
import ltr.ranking.Feature;
import ltr.ranking.Feature.FeatureWeight;
import ltr.ranking.Feature.FeatureWeight.FeatureScorer;
import ltr.util.FeatureException;
import ltr.util.LtrException;
import ltr.util.ModelException;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.intervals.IntervalIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a linear model, getting the weights directly from the request.
 **/
public class LinearModel extends Model {
  
  private static final Logger logger = LoggerFactory
      .getLogger(MethodHandles.lookup().lookupClass());
  final List<Feature> features;
  
  private final String LINEAR_MODEL_WEIGHTS_PARAM = "w";
  private final FeatureStore store;
  
  public LinearModel(final FeatureStore store) throws FeatureException {
    this.store = store;
    // features will be ordered as in the feature file
    features = new ArrayList<>(store.getFeatures());
  }
  
  @Override
  public Weight createWeight(final IndexSearcher searcher) throws IOException {
    // want to log different sets of features.
    return new LinearModelWeight(searcher,
        this.getWeights(features, searcher),
        this.getWeights(features, searcher));
  }
  
  @Override
  public Explanation explain(final AtomicReaderContext context, final int doc,
      final float finalScore, final List<Explanation> featureExplanations) {
    final Explanation e = new Explanation(finalScore, "linear-model, sum of:");
    IndexSearcher searcher = this.request.getSearcher();
    final LinearModelWeight weight;
    try {
      weight = new LinearModelWeight(searcher,
          this.getWeights(features, searcher),
          this.getWeights(features, searcher));
    } catch (IOException e1) {
      throw new RuntimeException(e1);
    }
    
    for (int i = 0; i < weight.weights.length; i++) {
      final Explanation featureExplain = featureExplanations.get(i);
      final Explanation feature = new Explanation(weight.weights[i] * featureExplain.getValue(), "weight("+weight.weights[i]+ ") * " +featureExplain.getDescription()+"("+ featureExplain.getValue() + ")");
      e.addDetail(feature);
    }
    return e;
  }

  
  public class LinearModelWeight extends ModelWeight {
    
    final float[] weights;
    
    public LinearModelWeight(final IndexSearcher searcher,
        final FeatureWeight[] modelFeatures,
        final FeatureWeight[] allFeatures) throws LtrException {
      super(searcher, modelFeatures, allFeatures);
      weights = new float[modelFeatures.length];
      if (! efi.containsKey(LINEAR_MODEL_WEIGHTS_PARAM)){
        throw new LtrException("Missing linear model weights");
      }
      final String weightsParam = efi.get(LINEAR_MODEL_WEIGHTS_PARAM);
      
      
      final String[] weightsStr = weightsParam.split(",");
      if (weights.length != weightsStr.length){
        throw new LtrException("List of weights doesn't match the number of features (expected:"+weights.length+", actual:"+weightsStr.length+")");
      }
      for (int i = 0; i < weightsStr.length; i++){
        weights[i] = Float.parseFloat(weightsStr[i]);
      }
     
    }
    
    @Override
    protected ModelScorer makeModelScorer(final ModelWeight weight,
        final FeatureScorer[] featureScorers) {
      return new LinearModelScorer(weight, featureScorers);
    }
    
    public class LinearModelScorer extends ModelScorer {
      
      protected LinearModelScorer(final Weight weight,
          final FeatureScorer[] featureScorers) {
        super(weight, featureScorers);
      }
      
      @Override
      public float score() throws IOException {
        
        for (int i = 0; i < this.allFeatureScorers.length; i++) {
          final FeatureScorer scorer = this.allFeatureScorers[i];
          if (scorer == null) {
            LinearModelWeight.this.allFeatureValues[i] = 0;
            continue;
          }
          if (scorer.docID() != this.doc) {
            LinearModelWeight.this.allFeatureValues[i] = scorer
                .getDefaultValue();
          } else {
            try {
              LinearModelWeight.this.allFeatureValues[i] = scorer.score();
              
            } catch (final Exception e) {
              LinearModelWeight.this.allFeatureValues[i] = scorer
                  .getDefaultValue();
              logger.debug("error computing feature {}, \n{}",
                  LinearModelWeight.this.allFeatureNames[i], e);
            }
          }
        }
        final float[] values = LinearModelWeight.this.getAllFeatureValues();
        float score = 0;
        for (int i = 0; i < values.length ; i++){
          score += weights[i]*values[i];
        }
        return score;
      }
      
      @Override
      public IntervalIterator intervals(final boolean collectIntervals)
          throws IOException {
        throw new UnsupportedOperationException();
      }
      
    }
    
  }
  
  @Override
  public Model replicate() throws ModelException {
    throw new UnsupportedOperationException();
  }
  
  @Override
  protected ModelWeight makeModelWeight(final IndexSearcher searcher,
      final FeatureWeight[] modelFeatures, final FeatureWeight[] allFeatures) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FeatureStore getFeatureStore() {
    return store;
  }
  
  
}
