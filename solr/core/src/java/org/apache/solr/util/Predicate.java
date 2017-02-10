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

package org.apache.solr.util;

/**
 * @deprecated Replacement for Java 8's Predicate class whilst we are still on Java 7
 */
@Deprecated
public abstract class Predicate<T> {
    abstract public boolean test(T obj);

    public Predicate<T> and(Predicate<T> otherPred) {
        return new AndPredicate<T>(this, otherPred);
    }

    private static class AndPredicate<T> extends Predicate<T> {
        final private Predicate<T> firstPred;
        final private Predicate<T> secondPred;

        AndPredicate(Predicate<T> firstPred, Predicate<T> secondPred) {
            this.firstPred = firstPred;
            this.secondPred = secondPred;
        }

        @Override
        public boolean test(T obj) {
            return firstPred.test(obj) && secondPred.test(obj);
        }
    }
}
