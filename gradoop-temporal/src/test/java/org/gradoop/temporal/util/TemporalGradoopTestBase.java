/*
 * Copyright © 2014 - 2021 Leipzig University (Database Research Group)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradoop.temporal.util;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.tuple.Tuple2;
import org.gradoop.common.model.api.entities.Edge;
import org.gradoop.common.model.api.entities.Element;
import org.gradoop.common.model.api.entities.GraphHead;
import org.gradoop.common.model.api.entities.Identifiable;
import org.gradoop.common.model.api.entities.Vertex;
import org.gradoop.flink.model.GradoopFlinkTestBase;
import org.gradoop.flink.model.api.epgm.BaseGraph;
import org.gradoop.flink.model.api.epgm.BaseGraphCollection;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.util.FlinkAsciiGraphLoader;
import org.gradoop.flink.util.GradoopFlinkConfig;
import org.gradoop.temporal.model.api.functions.TimeIntervalExtractor;
import org.gradoop.temporal.model.impl.TemporalGraph;
import org.gradoop.temporal.model.impl.TemporalGraphCollection;
import org.gradoop.temporal.model.impl.pojo.TemporalEdge;
import org.gradoop.temporal.model.impl.pojo.TemporalEdgeFactory;
import org.gradoop.temporal.model.impl.pojo.TemporalElement;
import org.gradoop.temporal.model.impl.pojo.TemporalGraphHeadFactory;
import org.gradoop.temporal.model.impl.pojo.TemporalVertex;
import org.gradoop.temporal.model.impl.pojo.TemporalVertexFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import static java.lang.Long.MAX_VALUE;
import static java.lang.Long.MIN_VALUE;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * A base class for tests using the temporal property graph model.
 */
public abstract class TemporalGradoopTestBase extends GradoopFlinkTestBase {

  /**
   * The time format used for formatted date strings.
   *
   * @see SimpleDateFormat detailed description of date formats
   */
  public static final String DATE_FORMAT = "yyyy.MM.dd kk:mm:ss.SSS";

  /**
   * A formatter used to convert date strings to milliseconds.
   *
   * @see #DATE_FORMAT
   */
  private final SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.GERMANY);

  /**
   * The config used for tests.
   */
  private TemporalGradoopConfig config;

  /*
   * Constants used as labels inside a test graph.
   */
  protected static final String V1 = "v1";
  protected static final String V2 = "v2";
  protected static final String V3 = "v3";
  protected static final String V4 = "v4";
  protected static final String V5 = "v5";

  protected static final String E1 = "e1";
  protected static final String E2 = "e2";
  protected static final String E3 = "e3";
  protected static final String E4 = "e4";
  protected static final String E5 = "e5";

  /**
   * Current timestamp in milliseconds.
   */
  protected static final Long CURRENT_TIME = System.currentTimeMillis();

  /**
   * Path to the Citibike Temporal-GDL file.
   */
  public static final String TEMPORAL_GDL_CITIBIKE_PATH = TemporalGradoopTestBase.class
    .getResource("/data/patternmatchingtest/citibikesample.gdl").getPath();

  @Override
  protected TemporalGradoopConfig getConfig() {
    if (config == null) {
      config = TemporalGradoopConfig.createConfig(getExecutionEnvironment());
    }
    return config;
  }

  @Override
  protected void setConfig(GradoopFlinkConfig config) {
    if (config instanceof TemporalGradoopConfig) {
      this.config = (TemporalGradoopConfig) config;
    } else {
      throw new IllegalArgumentException("This test base requires a temporal Gradoop config.");
    }
  }

  /**
   * Get the temporal graph head factory from the config.
   *
   * @return The graph head factory.
   */
  protected TemporalGraphHeadFactory getGraphHeadFactory() {
    return (TemporalGraphHeadFactory) getConfig().getTemporalGraphFactory().getGraphHeadFactory();
  }

  /**
   * Get the temporal vertex factory from the config.
   *
   * @return The vertex factory.
   */
  protected TemporalVertexFactory getVertexFactory() {
    return (TemporalVertexFactory) getConfig().getTemporalGraphFactory().getVertexFactory();
  }

  /**
   * Get the temporal edge factory from the config.
   *
   * @return The edge factory.
   */
  protected TemporalEdgeFactory getEdgeFactory() {
    return (TemporalEdgeFactory) getConfig().getTemporalGraphFactory().getEdgeFactory();
  }

  /**
   * Convert some graph to a {@link TemporalGraph}.
   *
   * @param graph The graph.
   * @return The resulting temporal graph.
   */
  protected TemporalGraph toTemporalGraph(BaseGraph<?, ?, ?, ?, ?> graph) {
    return getConfig().getTemporalGraphFactory().fromNonTemporalGraph(graph);
  }

  /**
   * Convert a graph to a {@link TemporalGraph} and set temporal attributes using
   * {@link TimeIntervalExtractor} functions.
   *
   * @param graph                  The graph.
   * @param graphHeadTimeExtractor The function used to extract temporal attributes for graph heads.
   * @param vertexTimeExtractor    The function used to extract temporal attributes for vertices.
   * @param edgeTimeExtractor      The function used to extract temporal attributes for edges.
   * @param <G> The graph head type.
   * @param <V> The vertex type.
   * @param <E> The edge type.
   * @return A temporal graph with temporal attributes extracted from the original graph.
   */
  protected <G extends GraphHead, V extends Vertex, E extends Edge> TemporalGraph toTemporalGraph(
    BaseGraph<G, V, E, ?, ?> graph,
    TimeIntervalExtractor<G> graphHeadTimeExtractor,
    TimeIntervalExtractor<V> vertexTimeExtractor,
    TimeIntervalExtractor<E> edgeTimeExtractor) {
    return getConfig().getTemporalGraphFactory().fromNonTemporalDataSets(
      graph.getGraphHead(), graphHeadTimeExtractor, graph.getVertices(), vertexTimeExtractor,
      graph.getEdges(), edgeTimeExtractor);
  }

  /**
   * Convert a graph to a {@link TemporalGraph} with time extraction functions.
   * This will use {@link TemporalGradoopTestUtils#extractTime(Element)} to extract temporal attributes.
   *
   * @param graph The graph.
   * @return The temporal graph with extracted temporal information.
   */
  protected TemporalGraph toTemporalGraphWithDefaultExtractors(BaseGraph<?, ?, ?, ?, ?> graph) {
    // We have to use lambda expressions instead of method references here, otherwise a
    // ClassCastException will be thrown when those extractor functions are called.
    // TODO: Find out why. (#1399)
    return toTemporalGraph(graph,
      g -> TemporalGradoopTestUtils.extractTime(g),
      v -> TemporalGradoopTestUtils.extractTime(v),
      e -> TemporalGradoopTestUtils.extractTime(e));
  }

  /**
   * Convert some graph collection to a {@link TemporalGraphCollection}.
   *
   * @param collection The graph collection.
   * @return The resulting temporal graph collection.
   */
  protected TemporalGraphCollection toTemporalGraphCollection(BaseGraphCollection<?, ?, ?, ?, ?> collection) {
    return getConfig().getTemporalGraphCollectionFactory().fromNonTemporalGraphCollection(collection);
  }

  /**
   * Convert a graph collection to a {@link TemporalGraphCollection} and set temporal attributes using
   * {@link TimeIntervalExtractor} functions.
   *
   * @param collection             The graph collection.
   * @param graphHeadTimeExtractor The function used to extract temporal attributes for graph heads.
   * @param vertexTimeExtractor    The function used to extract temporal attributes for vertices.
   * @param edgeTimeExtractor      The function used to extract temporal attributes for edges.
   * @param <G> The graph head type.
   * @param <V> The vertex type.
   * @param <E> The edge type.
   * @return A temporal graph with temporal attributes extracted from the original graph.
   */
  protected <G extends GraphHead, V extends Vertex, E extends Edge> TemporalGraphCollection
  toTemporalGraphCollection(
    BaseGraphCollection<G, V, E, ?, ?> collection,
    TimeIntervalExtractor<G> graphHeadTimeExtractor,
    TimeIntervalExtractor<V> vertexTimeExtractor,
    TimeIntervalExtractor<E> edgeTimeExtractor) {
    return getConfig().getTemporalGraphCollectionFactory().fromNonTemporalDataSets(
      collection.getGraphHeads(), graphHeadTimeExtractor, collection.getVertices(), vertexTimeExtractor,
      collection.getEdges(), edgeTimeExtractor);
  }

  /**
   * Convert a graph collection to a {@link TemporalGraphCollection} with time extraction functions.
   * This will use {@link TemporalGradoopTestUtils#extractTime(Element)} to extract temporal attributes.
   *
   * @param collection The graph collection.
   * @return The temporal graph with extracted temporal information.
   */
  protected TemporalGraphCollection toTemporalGraphCollectionWithDefaultExtractors(
    BaseGraphCollection<?, ?, ?, ?, ?> collection) {
    // We have to use lambda expressions instead of method references here, otherwise a
    // ClassCastException will be thrown when those extractor functions are called.
    // TODO: Find out why. (#1399)
    return toTemporalGraphCollection(collection,
      g -> TemporalGradoopTestUtils.extractTime(g),
      v -> TemporalGradoopTestUtils.extractTime(v),
      e -> TemporalGradoopTestUtils.extractTime(e));
  }

  /**
   * Check if the temporal graph element has default time values for valid and transaction time.
   *
   * @param element the temporal graph element to check
   */
  protected void checkDefaultTemporalElement(TemporalElement element) {
    assertEquals(TemporalElement.DEFAULT_TIME_FROM, element.getValidFrom());
    assertEquals(TemporalElement.DEFAULT_TIME_TO, element.getValidTo());
    checkDefaultTxTimes(element);
  }

  /**
   * Check if the temporal graph element has default time values for transaction time.
   *
   * @param element the temporal graph element to check
   */
  protected void checkDefaultTxTimes(TemporalElement element) {
    assertTrue(element.getTxFrom() < System.currentTimeMillis());
    assertEquals(TemporalElement.DEFAULT_TIME_TO, element.getTxTo());
  }

  /**
   * Creates a social network graph with temporal attributes used for tests.
   *
   * @return The graph loader containing the network graph.
   * @throws IOException When loading the graph resource fails.
   */
  protected FlinkAsciiGraphLoader getTemporalSocialNetworkLoader() throws IOException {
    InputStream inputStream = getClass()
      .getResourceAsStream(TemporalGradoopTestUtils.SOCIAL_NETWORK_TEMPORAL_GDL_FILE);
    return getLoaderFromStream(inputStream);
  }

  /**
   * Get a test graph with temporal attributes set.
   *
   * @return The test graph.
   */
  protected TemporalGraph getTestGraphWithValues() {
    TemporalVertex v1 = createVertex(V1, MIN_VALUE, MAX_VALUE, MIN_VALUE, MAX_VALUE);
    TemporalVertex v2 = createVertex(V2, 0, MAX_VALUE, 0, MAX_VALUE);
    TemporalVertex v3 = createVertex(V3, 1, 8, 3, 9);
    TemporalVertex v4 = createVertex(V4, 2, 7, 4, 5);
    TemporalVertex v5 = createVertex(V5, 3, 8, 1, 9);
    TemporalEdge e1 = createEdge(E1, v1, v2, 0, MAX_VALUE, 0, MAX_VALUE);
    TemporalEdge e2 = createEdge(E2, v2, v3, 1, 2, 6, 7);
    TemporalEdge e3 = createEdge(E3, v4, v5, 3, 4, 4, 5);
    TemporalEdge e4 = createEdge(E4, v3, v5, 6, 7, 4, 6);
    TemporalEdge e5 = createEdge(E5, v4, v4, 2, 6, 4, 5);
    DataSet<TemporalVertex> vertices = getExecutionEnvironment().fromElements(v1, v2, v3, v4, v5);
    DataSet<TemporalEdge> edges = getExecutionEnvironment().fromElements(e1, e2, e3, e4, e5);
    return getConfig().getTemporalGraphFactory().fromDataSets(vertices, edges);
  }

  /**
   * Get a test graph with all temporal attributes set to their default value.
   *
   * @return The test graph.
   */
  protected TemporalGraph getTestGraphWithAllDefaults() {
    TemporalVertex v1 = createVertex(V1, CURRENT_TIME, MAX_VALUE, MIN_VALUE, MAX_VALUE);
    TemporalVertex v2 = createVertex(V2, CURRENT_TIME, MAX_VALUE, MIN_VALUE, MAX_VALUE);
    TemporalVertex v3 = createVertex(V3, CURRENT_TIME, MAX_VALUE, MIN_VALUE, MAX_VALUE);
    TemporalEdge e1 = createEdge(E1, v1, v2, CURRENT_TIME, MAX_VALUE, MIN_VALUE, MAX_VALUE);
    TemporalEdge e2 = createEdge(E2, v2, v3, CURRENT_TIME, MAX_VALUE, MIN_VALUE, MAX_VALUE);
    TemporalEdge e3 = createEdge(E3, v3, v1, CURRENT_TIME, MAX_VALUE, MIN_VALUE, MAX_VALUE);
    DataSet<TemporalVertex> vertices = getExecutionEnvironment().fromElements(v1, v2, v3);
    DataSet<TemporalEdge> edges = getExecutionEnvironment().fromElements(e1, e2, e3);
    return getConfig().getTemporalGraphFactory().fromDataSets(vertices, edges);
  }

  /**
   * Create a temporal edge with temporal attributes set.
   *
   * @param label     The label of the edge.
   * @param source    The element used as a source for the edge.
   * @param target    The element used as a target for the edge.
   * @param txFrom    The start of the transaction time.
   * @param txTo      The end of the transaction time.
   * @param validFrom The start of the valid time.
   * @param validTo   The end of the valid time.
   * @return A temporal edge with those times set.
   */
  private TemporalEdge createEdge(String label, Identifiable source, Identifiable target, long txFrom,
                                  long txTo, long validFrom, long validTo) {
    TemporalEdge edge = getConfig().getTemporalGraphFactory().getEdgeFactory()
      .createEdge(source.getId(), target.getId());
    edge.setLabel(label);
    edge.setTransactionTime(Tuple2.of(txFrom, txTo));
    edge.setValidTime(Tuple2.of(validFrom, validTo));
    return edge;
  }

  /**
   * Create a temporal vertex with temporal attributes set.
   *
   * @param label     The label of the vertex.
   * @param txFrom    The start of the transaction time.
   * @param txTo      The end of the transaction time.
   * @param validFrom The start of the valid time.
   * @param validTo   The end of the valid time.
   * @return A temporal vertex with those times set.
   */
  private TemporalVertex createVertex(String label, long txFrom, long txTo, long validFrom, long validTo) {
    TemporalVertex vertex = getConfig().getTemporalGraphFactory().getVertexFactory().createVertex();
    vertex.setLabel(label);
    vertex.setTransactionTime(Tuple2.of(txFrom, txTo));
    vertex.setValidTime(Tuple2.of(validFrom, validTo));
    return vertex;
  }

  /**
   * Convert a formatted date/time string to the time format (milliseconds since unix epoch) used by Gradoop.
   * The date string is expected to be formatted according to {@link #DATE_FORMAT}.
   *
   * @param dateTimeString The date string.
   * @return The time in milliseconds since unix epoch.
   */
  protected long asMillis(String dateTimeString) {
    try {
      return dateFormatter.parse(dateTimeString).getTime();
    } catch (ParseException pe) {
      throw new IllegalArgumentException("Failed to parse date.", pe);
    }
  }

  /**
   * Loads citibike sample dataset as temporal graph.
   * Uses {@code start} and {@code end} edge properties to set {@code valid_from}/{@code tx_from}
   * and {@code valid_to}/{@code tx_to} values.
   *
   * @return temporal citibike sample graph
   * @throws Exception on failure
   */
  public TemporalGraph loadCitibikeSample() throws Exception {
    FlinkAsciiGraphLoader loader = new FlinkAsciiGraphLoader(getConfig());
    loader.initDatabaseFromFile(TEMPORAL_GDL_CITIBIKE_PATH);
    return transformToTemporalGraph(loader.getLogicalGraph());
  }

  /**
   * Given a logical graph, this method transforms it to a temporal graph.
   * {@code start} and {@code end} values are extracted from the edges (= "trips")
   * and used to set {@code valid_from}/{@code tx_from} and {@code valid_to}/{@code tx_to}
   * values.
   *
   * @param logicalGraph the logical graph to transform
   * @return logical graph transformed to temporal graph
   */
  protected TemporalGraph transformToTemporalGraph(LogicalGraph logicalGraph) {
    TemporalGraph tg = toTemporalGraph(logicalGraph);
    return tg.getFactory().fromDataSets(
      tg.getVertices().map(vertexTransform),
      tg.getEdges().map(edgeTransform));
  }

  /**
   * Set the edge's {@code valid_from} and {@code tx_from} according to the {@code start}
   * property and the edge's {@code valid_to} and {@code tx_to} according to the
   * {@code end} property. Both properties are retained.
   */
  protected static final MapFunction<TemporalEdge, TemporalEdge> edgeTransform = value -> {
    long start = value.getPropertyValue("start").getLong();
    long end = value.getPropertyValue("end").getLong();
    value.setValidTime(new Tuple2<>(start, end));
    value.setTransactionTime(new Tuple2<>(start, end));
    return value;
  };

  /**
   * Set the vertex {@code valid_from} and {@code tx_from} according to the {@code start}
   * property and the vertice's {@code valid_to} and {@code tx_to} according to the
   * {@code end} property. Both properties are retained.
   */
  protected static final MapFunction<TemporalVertex, TemporalVertex> vertexTransform = value -> {
    long start = value.getPropertyValue("start").getLong();
    long end = value.getPropertyValue("end").getLong();
    value.setValidTime(new Tuple2<>(start, end));
    value.setTransactionTime(new Tuple2<>(start, end));
    return value;
  };
}
