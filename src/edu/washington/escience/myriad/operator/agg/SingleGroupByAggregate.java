package edu.washington.escience.myriad.operator.agg;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.washington.escience.myriad.DbException;
import edu.washington.escience.myriad.Schema;
import edu.washington.escience.myriad.TupleBatch;
import edu.washington.escience.myriad.TupleBatchBuffer;
import edu.washington.escience.myriad.Type;
import edu.washington.escience.myriad.operator.Operator;

/**
 * The Aggregation operator that computes an aggregate (e.g., sum, avg, max, min) with a single group by column.
 */
public class SingleGroupByAggregate extends Operator {

  /**
   * default serialization ID.
   * */
  private static final long serialVersionUID = 1L;

  /**
   * result schema.
   * */
  private Schema schema;
  /**
   * the child.
   * */
  private Operator child;

  /**
   * compute multiple aggregates in the same time. The columns to compute the aggregates are
   * {@link SingleGroupByAggregate#afields}.
   * */
  private final Aggregator<?>[] agg;

  /**
   * Compute aggregate on each of the {@link SingleGroupByAggregate#afields}, with the corresponding {@link Aggregator}
   * in @link SingleGroupByAggregate#agg}.
   * */
  private final int[] afields;

  /** Aggregate operators. */
  private final int[] aggOps;

  /**
   * The group by column.
   * */
  private final int gColumn;

  /**
   * The buffer storing in-progress group by results. {groupby-column-value -> Aggregator Array}
   * */
  private transient HashMap<Object, Aggregator<?>[]> groupAggs;

  /**
   * {groupby-column-value -> }.
   * */
  private transient Map<Object, Pair<Object, TupleBatchBuffer>> groupedTupleBatches;

  /**
   * storing results after group by is done.
   * */
  private transient TupleBatchBuffer resultBuffer;

  /**
   * Constructor.
   * 
   * @param child The Operator that is feeding us tuples.
   * @param afields The columns over which we are computing an aggregate.
   * @param gfield The column over which we are grouping the result
   * @param aggOps The aggregation operator to use
   */
  public SingleGroupByAggregate(final Operator child, final int[] afields, final int gfield, final int[] aggOps) {
    Objects.requireNonNull(afields);
    if (afields.length == 0) {
      throw new IllegalArgumentException("aggregation fields must not be empty");
    }

    this.child = child;
    this.afields = afields;
    gColumn = gfield;
    this.aggOps = aggOps;
    agg = new Aggregator<?>[aggOps.length];
    groupAggs = new HashMap<Object, Aggregator<?>[]>();
    schema = generateSchema(child, groupAggs, gColumn, afields, agg, aggOps);
  }

  /**
   * @return the aggregate field
   * */
  public final int[] aggregateFields() {
    return afields;
  }

  @Override
  protected final void cleanup() throws DbException {
    groupAggs = new HashMap<Object, Aggregator<?>[]>();
    groupedTupleBatches = new HashMap<Object, Pair<Object, TupleBatchBuffer>>();
    resultBuffer = null;
  }

  /**
   * @param tb the TupleBatch to be processed.
   * */
  private void processTupleBatch(final TupleBatch tb) {
    final Set<Pair<Object, TupleBatchBuffer>> readyTBB = tb.groupby(gColumn, groupedTupleBatches);
    if (readyTBB != null) {
      for (final Pair<Object, TupleBatchBuffer> p : readyTBB) {
        final Object groupColumnValue = p.getKey();
        final TupleBatchBuffer tbb = p.getRight();
        Aggregator<?>[] groupAgg = groupAggs.get(groupColumnValue);
        if (groupAgg == null) {
          groupAgg = new Aggregator<?>[agg.length];
          for (int j = 0; j < agg.length; j++) {
            groupAgg[j] = agg[j].freshCopyYourself();
          }
          groupAggs.put(groupColumnValue, groupAgg);
        }

        TupleBatch filledTB = null;
        while ((filledTB = tbb.popFilled()) != null) {
          for (final Aggregator<?> ag : groupAgg) {
            ag.add(filledTB);
          }
        }
      }
    }
  }

  /**
   * Finish processing, drain the buffers.
   * */
  private void processEnd() {
    for (final Pair<Object, TupleBatchBuffer> p : groupedTupleBatches.values()) {
      final Object groupColumnValue = p.getKey();
      Aggregator<?>[] groupAgg = groupAggs.get(groupColumnValue);
      if (groupAgg == null) {
        groupAgg = new Aggregator<?>[agg.length];
        for (int j = 0; j < agg.length; j++) {
          groupAgg[j] = agg[j].freshCopyYourself();
        }
        groupAggs.put(groupColumnValue, groupAgg);
      }
      final TupleBatchBuffer tbb = p.getRight();
      TupleBatch anyTBB = null;
      while ((anyTBB = tbb.popAny()) != null) {
        for (final Aggregator<?> ag : groupAgg) {
          ag.add(anyTBB);
        }
      }
    }
  }

  /**
   * @param resultBuffer where the results are stored.
   * */
  private void generateResult(final TupleBatchBuffer resultBuffer) {

    for (final Map.Entry<Object, Aggregator<?>[]> e : groupAggs.entrySet()) {
      final Object groupByValue = e.getKey();
      final Aggregator<?>[] aggLocal = e.getValue();
      resultBuffer.put(0, groupByValue);
      int fromIndex = 1;
      for (final Aggregator<?> element : aggLocal) {
        element.getResult(resultBuffer, fromIndex);
        fromIndex += element.getResultSchema().numColumns();
      }
    }
  }

  /**
   * Returns the next tuple. If there is a group by field, then the first field is the field by which we are grouping,
   * and the second field is the result of computing the aggregate, If there is no group by field, then the result tuple
   * should contain one field representing the result of the aggregate. Should return null if there are no more tuples.
   * 
   * @throws DbException if processing error occurs.
   * @return next TB.
   */
  @Override
  protected final TupleBatch fetchNextReady() throws DbException {
    TupleBatch tb = null;

    if (resultBuffer.numTuples() > 0) {
      return resultBuffer.popAny();
    }

    if (child.eos() || child.eoi()) {
      return null;
    }

    while ((tb = child.nextReady()) != null) {
      processTupleBatch(tb);
    }

    if (child.eos() || child.eoi()) {
      processEnd();
      generateResult(resultBuffer);
    }
    return resultBuffer.popAny();
  }

  @Override
  public final Operator[] getChildren() {
    return new Operator[] { child };
  }

  @Override
  public final Schema getSchema() {
    return schema;
  }

  /**
   * @return the group by column.
   * */
  public final int getGroupByColumn() {
    return gColumn;
  }

  @Override
  protected final void init(final ImmutableMap<String, Object> execEnvVars) throws DbException {
    groupAggs = new HashMap<Object, Aggregator<?>[]>();
    groupedTupleBatches = new HashMap<Object, Pair<Object, TupleBatchBuffer>>();
    resultBuffer = new TupleBatchBuffer(schema);

  }

  @Override
  public final void setChildren(final Operator[] children) {
    child = children[0];
    schema = generateSchema(child, groupAggs, gColumn, afields, agg, aggOps);
  }

  /**
   * Generates the schema for MultiGroupByAggregate.
   * 
   * @param child the child operator
   * @param groupAggs the aggregator for each grouping
   * @param gfield the group field
   * @param afields the aggregate fields
   * @param agg the aggregators to be populated
   * @param aggOps ints representing the aggregate operations
   * 
   * @return the schema based on the aggregate, if the child is null, will return null.
   */
  private static Schema generateSchema(final Operator child, final Map<Object, Aggregator<?>[]> groupAggs,
      final int gfield, final int[] afields, final Aggregator<?>[] agg, final int[] aggOps) {
    if (child == null) {
      return null;
    }
    Schema outputSchema = null;

    final Schema childSchema = child.getSchema();
    final ImmutableList.Builder<Type> gTypes = ImmutableList.builder();
    gTypes.add(childSchema.getColumnType(gfield));
    final ImmutableList.Builder<String> gNames = ImmutableList.builder();
    gNames.add(childSchema.getColumnName(gfield));

    // Generates the output schema
    outputSchema = new Schema(gTypes, gNames);

    int idx = 0;
    for (final int afield : afields) {
      switch (childSchema.getColumnType(afield)) {
        case BOOLEAN_TYPE:
          agg[idx] = new BooleanAggregator(afield, childSchema.getColumnName(afield), aggOps[idx]);
          break;
        case INT_TYPE:
          agg[idx] = new IntegerAggregator(afield, childSchema.getColumnName(afield), aggOps[idx]);
          break;
        case LONG_TYPE:
          agg[idx] = new LongAggregator(afield, childSchema.getColumnName(afield), aggOps[idx]);
          break;
        case FLOAT_TYPE:
          agg[idx] = new FloatAggregator(afield, childSchema.getColumnName(afield), aggOps[idx]);
          break;
        case DOUBLE_TYPE:
          agg[idx] = new DoubleAggregator(afield, childSchema.getColumnName(afield), aggOps[idx]);
          break;
        case STRING_TYPE:
          agg[idx] = new StringAggregator(afield, childSchema.getColumnName(afield), aggOps[idx]);
          break;
        case DATETIME_TYPE:
          agg[idx] = new DateTimeAggregator(afield, childSchema.getColumnName(afield), aggOps[idx]);
          break;
        default:
          throw new IllegalArgumentException("Unknown column type: " + childSchema.getColumnType(afield));
      }
      outputSchema = Schema.merge(outputSchema, agg[idx].getResultSchema());
      idx++;
    }
    return outputSchema;
  }
}
