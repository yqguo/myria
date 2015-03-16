package edu.washington.escience.myria.operator;

import edu.washington.escience.myria.DbException;
import edu.washington.escience.myria.Schema;
import edu.washington.escience.myria.Type;
import edu.washington.escience.myria.storage.TupleBatch;
import edu.washington.escience.myria.storage.TupleBatchBuffer;
import edu.washington.escience.myria.util.TestEnvVars;

public class SplitTest {

  @Test
  public void testGeneratedSplits() throws DbException {
    final Object[][] expectedResults =
    {
        { true, "foo", 1L, 0.1 },
        { true, "bar", 1L, 0.1 },
        { true, "baz", 1L, 0.1, },
        { false, "", 2L, 0.2 },
        { false, "qux", 2L, 0.2 },
        { false, "", 2L, 0.2 },
        { false, "", 2L, 0.2 },
    };
    final Schema schema =
        new Schema(ImmutableList.of(Type.BOOLEAN_TYPE, Type.STRING_TYPE, Type.LONG_TYPE, Type.DOUBLE_TYPE),
            ImmutableList.of("bool", "string", "long", "double"));
    final TupleBatchBuffer input = new TupleBatchBuffer(schema);
    // First row to explode
    input.putBoolean(0, true);
    input.putString(1, "foo:bar:baz");
    input.putLong(2, 1L);
    input.putDouble(3, 0.1);
    // Second row to explode
    input.putBoolean(0, false);
    input.putString(1, ":qux::");
    input.putLong(2, 2L);
    input.putDouble(3, 0.2);
    Split splitOp = new Split(new TupleSource(input), 1, ":");

    splitOp.open(TestEnvVars.get());
    TupleBatch result;
    int rowIdx = 0;
    while (!splitOp.eos()) {
      result = splitOp.nextReady();
      if (result != null) {
        assertEquals(schema.numColumns(), result.getSchema().numColumns());
        assertEquals(Type.BOOLEAN_TYPE, result.getSchema().getColumnType(0));
        assertEquals(Type.STRING_TYPE, result.getSchema().getColumnType(1));
        assertEquals(Type.LONG_TYPE, result.getSchema().getColumnType(2));
        assertEquals(Type.DOUBLE_TYPE, result.getSchema().getColumnType(3));

        for (int batchIdx = 0; batchIdx < result.numTuples(); ++batchIdx, ++rowIdx) {
          assertEquals(((Boolean) expectedResults[rowIdx][0]).booleanValue(), result.getBoolean(0, batchIdx));
          assertEquals((expectedResults[rowIdx][1]).toString(), result.getString(1, batchIdx));
          assertEquals(((Long) expectedResults[rowIdx][2]).longValue(), result.getLong(2, batchIdx));
          assertEquals(Double.doubleToLongBits(((Double) expectedResults[rowIdx][3]).doubleValue()), Double
              .doubleToLongBits(result.getDouble(3, batchIdx)));

        }
      }
    }
    assertEquals(expectedResults.length, rowIdx);
    splitOp.close();
  }

  @Test
  public void testGeneratedSplitsSingleColumn() throws DbException {
    final String[] expectedResults = { "foo", "bar", "baz" };
    final Schema schema = new Schema(ImmutableList.of(Type.STRING_TYPE), ImmutableList.of("string"));
    final TupleBatchBuffer input = new TupleBatchBuffer(schema);
    input.putString(0, "foo:bar:baz");
    Split splitOp = new Split(new TupleSource(input), 0, ":");

    splitOp.open(TestEnvVars.get());
    TupleBatch result;
    int rowIdx = 0;
    while (!splitOp.eos()) {
      result = splitOp.nextReady();
      if (result != null) {
        assertEquals(schema.numColumns(), result.getSchema().numColumns());
        assertEquals(Type.STRING_TYPE, result.getSchema().getColumnType(0));

        for (int batchIdx = 0; batchIdx < result.numTuples(); ++batchIdx, ++rowIdx) {
          assertEquals(expectedResults[rowIdx], result.getString(0, batchIdx));
        }
      }
    }
    assertEquals(expectedResults.length, rowIdx);
    splitOp.close();
  }

  @Test(expected = Exception.class)
  public void testSplitColumnInvalidType() throws DbException {
    final Schema schema = new Schema(ImmutableList.of(Type.LONG_TYPE), ImmutableList.of("long"));
    final TupleBatchBuffer input = new TupleBatchBuffer(schema);
    input.putLong(0, 1L);
    Split splitOp = new Split(new TupleSource(input), 0, ":");
    splitOp.open(TestEnvVars.get());
    splitOp.close();
  }

  @Test(expected = Exception.class)
  public void testInvalidRegex() throws DbException {
    final Schema schema = new Schema(ImmutableList.of(Type.STRING_TYPE), ImmutableList.of("string"));
    final TupleBatchBuffer input = new TupleBatchBuffer(schema);
    input.putString(0, "foo");
    Split splitOp = new Split(new TupleSource(input), 0, "?:(");
    splitOp.open(TestEnvVars.get());
    splitOp.close();
  }
}
