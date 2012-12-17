/*
 * All content copyright Terracotta, Inc., unless otherwise indicated.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.statistics.archive;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.collection.IsEmptyCollection;
import org.hamcrest.core.CombinableMatcher;
import org.junit.Test;
import org.terracotta.statistics.ValueStatistic;

import static org.hamcrest.collection.IsCollectionWithSize.*;
import static org.hamcrest.number.OrderingComparison.*;
import static org.junit.Assert.assertThat;
import static org.terracotta.util.RetryAssert.assertBy;

/**
 *
 * @author cdennis
 */
public class StatisticSamplerTest {
  
  @Test
  public void testUnstartedSampler() throws InterruptedException {
    StatisticSampler<String> sampler = new StatisticSampler<String>(1L, TimeUnit.NANOSECONDS, new ValueStatistic<String>() {

      @Override
      public String value() {
        throw new AssertionError();
      }
    }, DevNull.DEV_NULL);
    
    sampler.shutdown();
  }
  
  @Test(expected = IllegalStateException.class)
  public void testShutdownOfSharedExecutor() throws InterruptedException {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    try {
      StatisticSampler<String> sampler = new StatisticSampler<String>(executor, 1L, TimeUnit.NANOSECONDS, constantStatistic("foo"), DevNull.DEV_NULL);
      sampler.shutdown();
    } finally {
      executor.shutdown();
    }
  }
  
  @Test
  public void testLongPeriodSampler() throws InterruptedException {
    StatisticArchive<String> archive = new StatisticArchive<String>(1);
    StatisticSampler<String> sampler = new StatisticSampler<String>(1L, TimeUnit.HOURS, new ValueStatistic<String>() {

      @Override
      public String value() {
        throw new AssertionError();
      }
    }, archive);
    try {
      sampler.start();
      TimeUnit.SECONDS.sleep(1);
      assertThat(archive.getArchive(), IsEmptyCollection.<Timestamped<String>>empty());
    } finally {
      sampler.shutdown();
    }
  }
  
  @Test
  public void testShortPeriodSampler() throws InterruptedException {
    StatisticArchive<String> archive = new StatisticArchive<String>(20);
    StatisticSampler<String> sampler = new StatisticSampler<String>(100L, TimeUnit.MILLISECONDS, constantStatistic("foo"), archive);
    try {
      sampler.start();
      TimeUnit.SECONDS.sleep(1);
      assertBy(1, TimeUnit.SECONDS, contentsOf(archive), hasSize(CombinableMatcher.<Integer>both(greaterThan(10)).and(lessThan(20))));
    } finally {
      sampler.shutdown();
    }
  }

  @Test
  public void testStoppingAndStartingSampler() throws InterruptedException {
    StatisticArchive<String> archive = new StatisticArchive<String>(20);
    StatisticSampler<String> sampler = new StatisticSampler<String>(200L, TimeUnit.MILLISECONDS, constantStatistic("foo"), archive);
    try {
      sampler.start();
      assertBy(1, TimeUnit.SECONDS, contentsOf(archive), hasSize(1));
      sampler.stop();
      int size = archive.getArchive().size();
      TimeUnit.SECONDS.sleep(1);
      assertThat(archive.getArchive(), hasSize(size));
      sampler.start();
      assertBy(1, TimeUnit.SECONDS, contentsOf(archive), hasSize(size + 1));
    } finally {
      sampler.shutdown();
    }
  }
  
  static <T> Callable<List<Timestamped<T>>> contentsOf(final StatisticArchive<T> archive) {
    return new Callable<List<Timestamped<T>>>() {

      @Override
      public List<Timestamped<T>> call() throws Exception {
        return archive.getArchive();
      }
    };
  }
  
  static <T> Matcher<Timestamped<T>> value(final Matcher<? super T> value) {
    return new TypeSafeMatcher<Timestamped<T>>() {

      @Override
      protected boolean matchesSafely(Timestamped<T> item) {
        return value.matches(item.getSample());
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("sample value ").appendDescriptionOf(value);
      }
    };
  }
  
  static <T> ValueStatistic<T> constantStatistic(final T value) {
    return new ValueStatistic<T>() {

      @Override
      public T value() {
        return value;
      }
    };
  }
}
