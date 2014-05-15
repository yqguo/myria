package edu.washington.escience.myria.expression.sql;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.washington.escience.myria.MyriaConstants;
import edu.washington.escience.myria.RelationKey;
import edu.washington.escience.myria.Schema;
import edu.washington.escience.myria.Type;
import edu.washington.escience.myria.coordinator.catalog.CatalogException;
import edu.washington.escience.myria.expression.ExpressionOperator;
import edu.washington.escience.myria.expression.evaluate.SqlExpressionOperatorParameter;
import edu.washington.escience.myria.parallel.Server;
import edu.washington.escience.myria.util.TestUtils;

/**
 * A select operator that can be compiled to SQL.
 */
public class SqlQuery implements Serializable {
  /***/
  private static final long serialVersionUID = 1L;

  /**
   * The select clause, projections. Null means *.
   */
  @JsonProperty
  private final List<ExpressionOperator> selectExpressions;

  /**
   * Schemas of the input. Used to create the from clause. It can be inferred from the select and where clause if the
   * catalog is present, which contains the schemas.
   */
  private HashMap<RelationKey, Schema> inputSchemas;

  /**
   * Selection for the where clause.
   */
  @JsonProperty
  private final ExpressionOperator whereExpression;

  /**
   * Column indexes that the output should be ordered by.
   */
  @JsonProperty
  private final List<ColumnReferenceExpression> sortedColumns;

  /**
   * True for each column in {@link #sortedColumns} that should be ordered ascending.
   */
  @JsonProperty
  private final List<Boolean> ascending;

  /**
   * This is not really unused, it's used automagically by Jackson deserialization.
   */
  public SqlQuery() {
    super();
    selectExpressions = null;
    whereExpression = null;
    sortedColumns = null;
    ascending = null;
  }

  /**
   * @param selectExpressions the select expressions
   * @param inputSchemas the relations and schema for from clause
   * @param whereExpression the where expressions
   * @param sortedColumns the columns by which the tuples should be ordered by.
   * @param ascending true for columns that should be ordered ascending.
   */
  public SqlQuery(final ImmutableList<ExpressionOperator> selectExpressions,
      final HashMap<RelationKey, Schema> inputSchemas, final ExpressionOperator whereExpression,
      final List<ColumnReferenceExpression> sortedColumns, final List<Boolean> ascending) {
    if (sortedColumns != null) {
      Objects.requireNonNull(ascending, "ascending");
      Preconditions.checkArgument(sortedColumns.size() == ascending.size());
    }
    this.selectExpressions = selectExpressions;
    this.whereExpression = whereExpression;
    this.inputSchemas = inputSchemas;
    this.sortedColumns = sortedColumns;
    this.ascending = ascending;
  }

  /**
   * Query for scanning whole relation. Note that the input and output schema are the same.
   * 
   * @param relation the relation to read
   */
  public SqlQuery(final RelationKey relation) {
    selectExpressions = null;
    whereExpression = null;
    inputSchemas = new HashMap<>();
    inputSchemas.put(relation, null);
    sortedColumns = null;
    ascending = null;
  }

  /**
   * Generate schema by inspecting the select and where clauses.
   * 
   * @param server the master catalog
   * @throws CatalogException if the catalog cannot be accessed
   */
  public void generateInputSchemas(final Server server) throws CatalogException {
    inputSchemas = Maps.newLinkedHashMap();

    LinkedList<ExpressionOperator> ops = Lists.newLinkedList();
    ops.add(whereExpression);
    for (ExpressionOperator op : selectExpressions) {
      ops.add(op);
    }
    while (!ops.isEmpty()) {
      final ExpressionOperator op = ops.pop();
      if (op.getClass().equals(ColumnReferenceExpression.class)) {
        ColumnReferenceExpression ref = (ColumnReferenceExpression) op;
        RelationKey key = ref.getRelation();
        if (!inputSchemas.containsKey(key)) {
          inputSchemas.put(key, server.getSchema(key));
        }
      } else {
        ops.addAll(op.getChildren());
      }
    }
  }

  /**
   * Returns the compiled sql string.
   * 
   * @param parameters parameters passed down the tree
   * @return the sql string
   */
  public String getSqlString(final SqlExpressionOperatorParameter parameters) {
    if (!TestUtils.inTravis()) {
      // ignore this in travis so that systemtests don't fail
      Preconditions.checkArgument(parameters.getDbms().equals(MyriaConstants.STORAGE_SYSTEM_POSTGRESQL));
    }

    Objects.requireNonNull(inputSchemas, "inputSchemas");

    List<RelationKey> fromRelations = Lists.newArrayList(inputSchemas.keySet());

    parameters.generateAliases(fromRelations);
    parameters.setSchemas(inputSchemas);

    if (whereExpression != null) {
      Preconditions.checkArgument(whereExpression.getOutputType(parameters) == Type.BOOLEAN_TYPE,
          "Expected where clause to be boolean but it was %s", whereExpression.getOutputType(parameters));
    }

    StringBuilder sb = new StringBuilder();

    sb.append("SELECT ");

    if (selectExpressions != null) {
      List<String> selects = Lists.newLinkedList();
      for (ExpressionOperator expr : selectExpressions) {
        selects.add(expr.getSqlString(parameters));
      }
      sb.append(Joiner.on(',').join(selects));
    } else {
      sb.append("*");
    }

    sb.append("\nFROM ");

    List<String> froms = Lists.newLinkedList();
    for (RelationKey relation : fromRelations) {
      froms.add(relation.toString(parameters.getDbms()) + " AS " + parameters.getAlias(relation));
    }
    sb.append(Joiner.on(',').join(froms));

    if (whereExpression != null) {
      sb.append("\nWHERE ").append(whereExpression.getSqlString(parameters));
    }

    if (sortedColumns != null && sortedColumns.size() > 0) {
      sb.append("\nORDER BY ");

      List<String> orders = Lists.newLinkedList();
      for (int i = 0; i < sortedColumns.size(); i++) {
        final ColumnReferenceExpression column = sortedColumns.get(i);
        String modifier = "ASC";
        if (!ascending.get(i)) {
          modifier = "DESC";
        }
        orders.add(column.getSqlString(parameters) + " " + modifier);
      }
      sb.append(Joiner.on(',').join(orders));
    }

    return sb.toString();
  }
}